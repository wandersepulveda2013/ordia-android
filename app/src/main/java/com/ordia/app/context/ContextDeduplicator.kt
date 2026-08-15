package com.ordia.app.context

/**
 * Evita que el mismo contenido contextual se sugiera múltiples veces.
 *
 * Funcionamiento:
 * - Cada intent genera un hash del título + tipo
 * - Si el hash ya existe dentro de la ventana de deduplicación, se descarta
 * - Los hashes expiran automáticamente después de la ventana configurable
 *
 * Thread-safe para uso desde múltiples fuentes (teclado, notificaciones, etc.)
 */
class ContextDeduplicator(
    /** Ventana en milisegundos durante la cual un intent no puede repetirse */
    private val dedupWindowMs: Long = DEFAULT_DEDUP_WINDOW_MS,
    /** Capacidad máxima de entradas en el historial */
    private val maxEntries: Int = MAX_DEDUP_ENTRIES,
    /** Reloj inyectable para tests deterministas; en producción usa el reloj del sistema. */
    private val clock: () -> Long = System::currentTimeMillis
) {

    /** Mapa de hash → timestamp en milisegundos */
    private val seenHashes = LinkedHashMap<String, Long>(maxEntries, 0.75f, false)

    @Synchronized
    fun isDuplicate(intent: ContextIntent): Boolean {
        expireEntries()
        val hash = computeHash(intent)
        val seen = seenHashes[hash] ?: return false
        // Solo es duplicado dentro de la ventana; pasado el umbral ya debe poder
        // sugerirse de nuevo (coherente con isDuplicateWithDetails). Limpiar la
        // entrada vencida evita reportarla como duplicada antes de su purge (2×).
        if (currentTimeMs() - seen >= dedupWindowMs) {
            seenHashes.remove(hash)
            return false
        }
        return true
    }

    @Synchronized
    fun markAsSeen(intent: ContextIntent) {
        expireEntries()
        val hash = computeHash(intent)
        seenHashes[hash] = currentTimeMs()
        trimToMax()
    }

    @Synchronized
    fun markAsCompleted(intent: ContextIntent) {
        // Al completarse, permitir que vuelva a sugerirse después de un tiempo más corto
        val hash = computeHash(intent)
        seenHashes[hash] = currentTimeMs() - dedupWindowMs / 2
    }

    @Synchronized
    fun clear() {
        seenHashes.clear()
    }

    @Synchronized
    fun size(): Int = seenHashes.size

    @Synchronized
    fun isDuplicateWithDetails(intent: ContextIntent): DedupResult {
        expireEntries()
        val hash = computeHash(intent)
        val existing = seenHashes[hash]
        return when {
            existing == null -> DedupResult.NotDuplicate
            currentTimeMs() - existing < dedupWindowMs -> {
                val remaining = dedupWindowMs - (currentTimeMs() - existing)
                DedupResult.Duplicate(remainingMs = remaining)
            }
            else -> {
                // Expirado, remover
                seenHashes.remove(hash)
                DedupResult.Expired
            }
        }
    }

    // Internal

    private fun computeHash(intent: ContextIntent): String {
        // Normalizar: minúsculas, sin espacios extra
        val normalized = "${intent.kind.name}|${intent.title.lowercase().trim().replace(Regex("\\s+"), " ")}"
        return normalized.hashCode().toUInt().toString(16)
    }

    private fun expireEntries() {
        val now = currentTimeMs()
        val iterator = seenHashes.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > dedupWindowMs * 2) {
                iterator.remove()
            }
        }
    }

    private fun trimToMax() {
        while (seenHashes.size > maxEntries) {
            val oldest = seenHashes.entries.firstOrNull() ?: break
            seenHashes.remove(oldest.key)
        }
    }

    private fun currentTimeMs(): Long = clock()

    companion object {
        /** Ventana por defecto: 1 hora */
        private const val DEFAULT_DEDUP_WINDOW_MS = 3_600_000L
        /** Límite de entradas históricas */
        private const val MAX_DEDUP_ENTRIES = 500
    }
}

/** Resultado de la verificación de duplicado */
sealed class DedupResult {
    /** No es duplicado, puede sugerirse */
    data object NotDuplicate : DedupResult()
    /** Es duplicado activo, tiempo restante en ms */
    data class Duplicate(val remainingMs: Long) : DedupResult()
    /** Era duplicado pero ya expiró, puede sugerirse de nuevo */
    data object Expired : DedupResult()
}
