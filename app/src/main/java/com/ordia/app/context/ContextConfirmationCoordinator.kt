package com.ordia.app.context

import java.util.UUID

/**
 * Coordina el flujo de confirmación contextual.
 *
 * Cuando el motor detecta una intención, el guardián (confirmation coordinator)
 * puede optar por:
 * - ALWAYS_CONFIRM: Siempre preguntar al usuario antes de crear
 * - AUTO_CONFIRM_HIGH_CONFIDENCE: Crear automáticamente si confianza > umbral
 * - AUTO_CONFIRM_TASK: Crear automáticamente para tareas simples
 * - SUPPRESS: No crear ni preguntar (para contenido descartado)
 *
 * El estado de la decisión se mantiene hasta que se resuelve o expira.
 */
class ContextConfirmationCoordinator(
    private val confirmationTimeoutMs: Long = DEFAULT_CONFIRM_TIMEOUT_MS
) {

    /** Intents pendientes de confirmación */
    private val pendingConfirmations = LinkedHashMap<String, PendingConfirmation>()

    /** Preferencias de confirmación por tipo de intención */
    private val confirmationPreferences = mutableMapOf<ContextIntentKind, ConfirmationPolicy>()

    @Synchronized
    fun needsConfirmation(intent: ContextIntent): Boolean {
        // Si hay una preferencia específica para este tipo
        val policy = confirmationPreferences[intent.kind] ?: defaultPolicy(intent)
        return when (policy) {
            ConfirmationPolicy.ALWAYS_CONFIRM -> true
            ConfirmationPolicy.AUTO_CONFIRM_HIGH_CONFIDENCE -> intent.confidence < HIGH_CONFIDENCE_THRESHOLD
            ConfirmationPolicy.AUTO_CONFIRM_TASK -> false
            ConfirmationPolicy.SUPPRESS -> false
        }
    }

    @Synchronized
    fun registerPending(intent: ContextIntent): String {
        val id = UUID.randomUUID().toString()
        pendingConfirmations[id] = PendingConfirmation(
            id = id,
            intent = intent,
            createdAtMs = currentTimeMs()
        )
        expirePending()
        return id
    }

    @Synchronized
    fun getPending(id: String): PendingConfirmation? {
        expirePending()
        val pending = pendingConfirmations[id]
        if (pending != null && currentTimeMs() - pending.createdAtMs > confirmationTimeoutMs) {
            pendingConfirmations.remove(id)
            return null
        }
        return pending
    }

    @Synchronized
    fun resolve(id: String, accepted: Boolean, modifiedIntent: ContextIntent? = null): Boolean {
        val pending = pendingConfirmations[id] ?: return false
        pendingConfirmations.remove(id)

        if (accepted) {
            onAccepted(pending, modifiedIntent)
        } else {
            onRejected(pending)
        }
        return true
    }

    @Synchronized
    fun setPolicy(kind: ContextIntentKind, policy: ConfirmationPolicy) {
        confirmationPreferences[kind] = policy
    }

    @Synchronized
    fun getPolicy(kind: ContextIntentKind): ConfirmationPolicy {
        return confirmationPreferences[kind] ?: defaultPolicy()
    }

    @Synchronized
    fun resetPolicy(kind: ContextIntentKind) {
        confirmationPreferences.remove(kind)
    }

    @Synchronized
    fun resetAllPolicies() {
        confirmationPreferences.clear()
    }

    @Synchronized
    fun pendingCount(): Int = pendingConfirmations.size

    @Synchronized
    fun allPending(): List<PendingConfirmation> {
        expirePending()
        return pendingConfirmations.values.toList()
    }

    // Callbacks — pueden ser sobreescritos para integración con UI
    var onConfirmed: ((ContextIntent) -> Unit)? = null
    var onRejected: ((ContextIntent) -> Unit)? = null
    var onModified: ((ContextIntent) -> Unit)? = null

    // Internal

    private fun defaultPolicy(intent: ContextIntent? = null): ConfirmationPolicy {
        // No crear nada automáticamente por defecto — todas las intenciones requieren confirmación
        return ConfirmationPolicy.ALWAYS_CONFIRM
    }

    private fun onAccepted(pending: PendingConfirmation, modified: ContextIntent?) {
        val intent = modified ?: pending.intent
        onConfirmed?.invoke(intent)
    }

    private fun onRejected(pending: PendingConfirmation) {
        onRejected?.invoke(pending.intent)
    }

    private fun expirePending() {
        val now = currentTimeMs()
        val iterator = pendingConfirmations.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.createdAtMs > confirmationTimeoutMs) {
                iterator.remove()
            }
        }
    }

    private fun currentTimeMs(): Long = System.currentTimeMillis()

    companion object {
        /** Timeout por defecto: 5 minutos */
        private const val DEFAULT_CONFIRM_TIMEOUT_MS = 300_000L
        /** Confianza alta: 80% */
        private const val HIGH_CONFIDENCE_THRESHOLD = 0.8f
    }
}

/** Intención pendiente de confirmación */
data class PendingConfirmation(
    val id: String,
    val intent: ContextIntent,
    val createdAtMs: Long
)

/** Política de confirmación para cada tipo de intención */
enum class ConfirmationPolicy {
    /** Siempre preguntar al usuario */
    ALWAYS_CONFIRM,
    /** Crear automáticamente si la confianza es alta */
    AUTO_CONFIRM_HIGH_CONFIDENCE,
    /** Crear automáticamente (tareas simples) */
    AUTO_CONFIRM_TASK,
    /** No crear ni preguntar nunca */
    SUPPRESS
}
