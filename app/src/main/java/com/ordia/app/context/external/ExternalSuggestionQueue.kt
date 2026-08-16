package com.ordia.app.context.external

import java.util.PriorityQueue

/**
 * Cola FIFO con prioridades para sugerencias externas.
 *
 * - Una sugerencia visible como máximo.
 * - Prioridad: evento urgente > pago > tarea > compra > nota > otras.
 * - Deduplicación por ID.
 */
class ExternalSuggestionQueue {

    // Cola ordenada por prioridad descendente, luego por creación ascendente
    private val queue = PriorityQueue<ExternalSuggestion> { a, b ->
        val prio = b.priority.compareTo(a.priority)
        if (prio != 0) prio
        else a.createdAt.compareTo(b.createdAt)
    }

    @Volatile
    private var currentSuggestion: ExternalSuggestion? = null

    // ========================================================================
    // Operaciones de cola
    // ========================================================================

    /** Añade una sugerencia a la cola si no existe ya (dedup por ID). */
    fun enqueue(suggestion: ExternalSuggestion): Boolean {
        synchronized(this) {
            // Dedup: no añadir si ya existe el mismo ID
            if (containsLocked(suggestion.id)) return false
            return queue.add(suggestion)
        }
    }

    /** Obtiene la siguiente sugerencia de mayor prioridad sin removerla. */
    fun peek(): ExternalSuggestion? = synchronized(this) { queue.peek() }

    /** Remueve y retorna la sugerencia de mayor prioridad. */
    fun poll(): ExternalSuggestion? = synchronized(this) { queue.poll() }

    /** Remueve una sugerencia por ID. */
    fun remove(id: String): Boolean = synchronized(this) {
        val removed = queue.removeIf { it.id == id }
        if (currentSuggestion?.id == id) {
            currentSuggestion = null
        }
        removed
    }

    /** Verifica si existe una sugerencia con el ID dado. */
    fun contains(id: String): Boolean = synchronized(this) { containsLocked(id) }

    /** Vacía la cola. */
    fun clear() = synchronized(this) {
        queue.clear()
        currentSuggestion = null
    }

    /** Cantidad de elementos en cola. */
    fun size(): Int = synchronized(this) { queue.size }

    /** ¿Está vacía? */
    fun isEmpty(): Boolean = synchronized(this) { queue.isEmpty() }

    /** Obtiene copia de la lista actual (para persistencia). */
    fun toList(): List<ExternalSuggestion> = synchronized(this) {
        queue.toList().toList()
    }

    /** Restaura cola desde una lista. */
    fun restore(items: List<ExternalSuggestion>) = synchronized(this) {
        queue.clear()
        queue.addAll(items.filter { it.isActionable })
    }

    // ========================================================================
    // Gestión de sugerencia actual (visible)
    // ========================================================================

    /** Sugerencia actualmente visible (una a la vez). */
    fun getCurrent(): ExternalSuggestion? = synchronized(this) { currentSuggestion }

    /** Establece la sugerencia actual. */
    fun setCurrent(suggestion: ExternalSuggestion?) = synchronized(this) {
        currentSuggestion?.let { old ->
            if (old.state == ExternalSuggestionState.DISPLAYED && old.id != suggestion?.id) {
                // Re-encolar si no fue resuelta
                val reEnqueued = old.copy(state = ExternalSuggestionState.PENDING)
                queue.add(reEnqueued)
            }
        }
        currentSuggestion = suggestion
    }

    /** Marca la sugerencia actual como resuelta y la remueve de la cola. */
    fun resolveCurrent(): ExternalSuggestion? = synchronized(this) {
        val current = currentSuggestion
        currentSuggestion = null
        current?.let { c -> queue.removeIf { it.id == c.id } }
        current
    }

    /** Avanza a la siguiente sugerencia de la cola. */
    fun advanceToNext(): ExternalSuggestion? = synchronized(this) {
        currentSuggestion = null
        val next = queue.poll()
        if (next != null) {
            currentSuggestion = next.copy(state = ExternalSuggestionState.DISPLAYED)
        }
        currentSuggestion
    }

    /** Actualiza el estado de una sugerencia. */
    fun updateState(id: String, newState: ExternalSuggestionState) = synchronized(this) {
        if (currentSuggestion?.id == id) {
            currentSuggestion = currentSuggestion?.copy(state = newState)
        }
        // También actualizar en cola si está
        val items = queue.toMutableList()
        val idx = items.indexOfFirst { it.id == id }
        if (idx >= 0) {
            items[idx] = items[idx].copy(state = newState)
            queue.clear()
            queue.addAll(items)
        }
    }

    /** Elimina sugerencias expiradas. */
    fun removeExpired() = synchronized(this) {
        val now = System.currentTimeMillis()
        queue.removeIf { it.expiresAt <= now }
        if (currentSuggestion?.isExpired == true) {
            currentSuggestion = null
        }
    }

    private fun containsLocked(id: String): Boolean {
        if (currentSuggestion?.id == id) return true
        return queue.any { it.id == id }
    }
}
