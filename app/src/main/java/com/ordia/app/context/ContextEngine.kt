package com.ordia.app.context

import android.content.Context
import android.util.Log

/**
 * Motor contextual central de Ordía 3.
 *
 * Orquesta el pipeline completo:
 * ContextEvent → PrivacyFilter → IntentEngine → Deduplicator → ConfirmationCoordinator → AuditLog → Output
 *
 * Uso desde cualquier fuente de captura:
 *   ContextEngine.getInstance(context).processEvent(event)
 *
 * Thread-safe. Las fuentes pueden llamar desde cualquier hilo.
 */
class ContextEngine private constructor(appContext: Context) {

    private val intentEngine = ContextIntentEngine
    private val deduplicator = ContextDeduplicator()
    val confirmationCoordinator = ContextConfirmationCoordinator()
    private val auditLog = ContextAuditLog(appContext)

    /** Oyentes de eventos del pipeline */
    private val listeners = mutableListOf<ContextEngineListener>()

    /**
     * Procesa un evento contextual completo.
     * Puede llamarse desde cualquier hilo.
     */
    fun processEvent(event: ContextEvent): ContextResult {
        Log.d(TAG, "Processing event from ${event.source}")

        // 1. Analizar con el motor de intenciones (incluye filtro de privacidad)
        val intent = intentEngine.analyze(event) ?: return ContextResult.Discarded(
            reason = DiscardReason.PRIVACY_FILTER,
            source = event.source
        )

        // 2. Verificar duplicados
        if (deduplicator.isDuplicate(intent)) {
            Log.d(TAG, "Duplicate intent: ${intent.kind} — ${intent.title.take(40)}")
            auditLog.logIntentDiscarded(intent, DiscardReason.DUPLICATE)
            notifyOnDiscarded(intent, DiscardReason.DUPLICATE)
            return ContextResult.Discarded(
                reason = DiscardReason.DUPLICATE,
                source = event.source,
                intent = intent
            )
        }

        // 3. Marcar como visto
        deduplicator.markAsSeen(intent)

        // 4. Verificar si necesita confirmación
        return if (confirmationCoordinator.needsConfirmation(intent)) {
            val confirmationId = confirmationCoordinator.registerPending(intent)
            auditLog.logIntentDiscarded(intent, DiscardReason.LOW_CONFIDENCE)
            Log.d(TAG, "Pending confirmation $confirmationId for: ${intent.title.take(40)}")
            notifyOnPending(intent, confirmationId)
            ContextResult.PendingConfirmation(
                confirmationId = confirmationId,
                intent = intent
            )
        } else {
            // 5. Confirmación automática
            auditLog.logIntentCreated(intent)
            notifyOnCreated(intent)
            Log.d(TAG, "Auto-confirmed: ${intent.kind} — ${intent.title.take(40)}")
            ContextResult.Created(intent)
        }
    }

    /**
     * Procesa texto plano desde cualquier fuente.
     * Útil para fuentes donde no se necesita metadata adicional.
     */
    fun processText(text: String, source: ContextCaptureSource): ContextResult {
        val event = ContextEvent(
            source = source,
            rawText = text,
            timestampMs = System.currentTimeMillis()
        )
        return processEvent(event)
    }

    /**
     * Resuelve una confirmación pendiente.
     */
    fun resolveConfirmation(confirmationId: String, accepted: Boolean, modifiedIntent: ContextIntent? = null): Boolean {
        val resolved = confirmationCoordinator.resolve(confirmationId, accepted, modifiedIntent)
        if (resolved && accepted) {
            val pending = confirmationCoordinator.allPending()
            // El intent original ya no está disponible, pero el callback del coordinator lo maneja
        }
        return resolved
    }

    /**
     * Obtiene todas las confirmaciones pendientes.
     */
    fun getPendingConfirmations(): List<PendingConfirmation> {
        return confirmationCoordinator.allPending()
    }

    /**
     * Registra un oyente para eventos del pipeline.
     */
    fun addListener(listener: ContextEngineListener) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    /**
     * Elimina un oyente.
     */
    fun removeListener(listener: ContextEngineListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    /**
     * Exporta el registro de auditoría como JSON.
     */
    fun exportAuditLog(): String = auditLog.exportAsJson()

    /**
     * Obtiene estadísticas del registro de auditoría.
     */
    fun getAuditStats(): AuditStats = auditLog.getStats()

    /**
     * Limpia registros de auditoría antiguos.
     */
    fun cleanAuditLog(ageMs: Long = DEFAULT_AUDIT_RETENTION_MS): Int = auditLog.cleanOlderThan(ageMs)

    /**
     * Prepara el motor para ser destruido.
     */
    fun shutdown() {
        auditLog.close()
        synchronized(listeners) {
            listeners.clear()
        }
    }

    // Private: notificaciones a oyentes

    private fun notifyOnCreated(intent: ContextIntent) {
        synchronized(listeners) {
            listeners.forEach { it.onIntentCreated(intent) }
        }
    }

    private fun notifyOnDiscarded(intent: ContextIntent, reason: DiscardReason) {
        synchronized(listeners) {
            listeners.forEach { it.onIntentDiscarded(intent, reason) }
        }
    }

    private fun notifyOnPending(intent: ContextIntent, confirmationId: String) {
        synchronized(listeners) {
            listeners.forEach { it.onConfirmationPending(intent, confirmationId) }
        }
    }

    override fun toString(): String {
        val pending = confirmationCoordinator.pendingCount()
        val auditStats = try { auditLog.getStats().totalEntries } catch (e: Exception) { -1 }
        return "ContextEngine(pending=$pending, audit=$auditStats)"
    }

    companion object {
        private const val TAG = "ContextEngine"
        private const val DEFAULT_AUDIT_RETENTION_MS = 30L * 86_400_000L // 30 días

        @Volatile
        private var instance: ContextEngine? = null

        /**
         * Obtiene la instancia singleton del motor contextual.
         */
        @JvmStatic
        fun getInstance(context: Context): ContextEngine {
            return instance ?: synchronized(this) {
                instance ?: ContextEngine(context.applicationContext).also { instance = it }
            }
        }

        /**
         * Reinicia el singleton (solo para pruebas).
         */
        @JvmStatic
        fun resetInstance() {
            synchronized(this) {
                instance?.shutdown()
                instance = null
            }
        }
    }
}

/** Oyente de eventos del pipeline contextual */
interface ContextEngineListener {
    /** Se llamó cuando una intención se crea automáticamente */
    fun onIntentCreated(intent: ContextIntent) {}
    /** Se llamó cuando una intención se descarta */
    fun onIntentDiscarded(intent: ContextIntent, reason: DiscardReason) {}
    /** Se llamó cuando una intención queda pendiente de confirmación */
    fun onConfirmationPending(intent: ContextIntent, confirmationId: String) {}
}

/** Resultado del procesamiento de un evento contextual */
sealed class ContextResult {
    /** Intención creada automáticamente */
    data class Created(val intent: ContextIntent) : ContextResult()
    /** Pendiente de confirmación del usuario */
    data class PendingConfirmation(
        val confirmationId: String,
        val intent: ContextIntent
    ) : ContextResult()
    /** Descartado (filtro de privacidad, duplicado, baja confianza) */
    data class Discarded(
        val reason: DiscardReason,
        val source: ContextCaptureSource,
        val intent: ContextIntent? = null
    ) : ContextResult()
}
