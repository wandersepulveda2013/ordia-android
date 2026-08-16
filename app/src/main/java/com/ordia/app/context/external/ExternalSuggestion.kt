package com.ordia.app.context.external

import com.ordia.app.context.ContextCaptureSource
import com.ordia.app.context.ContextIntentKind

/**
 * Sugerencia estructurada y segura para confirmación externa.
 *
 * NO contiene texto original del usuario.
 * Solo contiene datos interpretados y normalizados.
 * Segura para almacenar en SharedPreferences, Intents, Notificaciones y Logcat.
 */
data class ExternalSuggestion(
    /** Identificador único compartido con ContextIntent.id. */
    val id: String,

    /** ID de confirmación del ContextEngine (vacío si ya está confirmado). */
    val confirmationId: String,

    /** Tipo de intención detectada. */
    val kind: ContextIntentKind,

    /** Título interpretado y normalizado (máx 120 caracteres). */
    val title: String,

    /** Fecha/hora detectada en epoch millis (nullable). */
    val dueAt: Long? = null,

    /** Origen de la captura. */
    val source: ContextCaptureSource,

    /** Paquete de la aplicación de origen (si está disponible). */
    val sourcePackage: String? = null,

    /** Prioridad calculada para la cola. */
    val priority: Int = 0,

    /** Nivel de confianza (0.0–1.0). Solo visible en modo diagnóstico. */
    val confidence: Float = 0f,

    /** Timestamp de creación. */
    val createdAt: Long = System.currentTimeMillis(),

    /** Timestamp de expiración (por defecto 5 minutos después de creación). */
    val expiresAt: Long = System.currentTimeMillis() + 300_000L,

    /** Estado actual de la sugerencia. */
    val state: ExternalSuggestionState = ExternalSuggestionState.PENDING
) {

    /** ¿La sugerencia ha expirado? */
    val isExpired: Boolean get() = System.currentTimeMillis() > expiresAt

    /** ¿La sugerencia está pendiente de acción? (no expirada) */
    val isActionable: Boolean get() = !isExpired && (
            state == ExternalSuggestionState.PENDING ||
            state == ExternalSuggestionState.DISPLAYED ||
            state == ExternalSuggestionState.POSTPONED
    )

    companion object {
        /** Prioridades calculadas según el tipo de intención. */
        fun calculatePriority(kind: ContextIntentKind, dueAt: Long?): Int {
            val now = System.currentTimeMillis()
            // Urgente: dentro de la próxima hora
            if (dueAt != null && dueAt <= now + 3_600_000L) return 100
            // Hoy
            if (dueAt != null && dueAt <= now + 86_400_000L) return 95
            return when (kind) {
                ContextIntentKind.APPOINTMENT,
                ContextIntentKind.EVENT,
                ContextIntentKind.MEETING -> 90

                ContextIntentKind.PAYMENT,
                ContextIntentKind.DEADLINE -> 80

                ContextIntentKind.TASK,
                ContextIntentKind.ERRAND -> 70

                ContextIntentKind.CALL,
                ContextIntentKind.VISIT -> 65

                ContextIntentKind.DELIVERY,
                ContextIntentKind.SHOPPING -> 60

                ContextIntentKind.REMINDER,
                ContextIntentKind.GOAL -> 55

                ContextIntentKind.NOTE -> 50

                ContextIntentKind.HABIT,
                ContextIntentKind.EXERCISE -> 45

                ContextIntentKind.STUDY,
                ContextIntentKind.PROJECT -> 40

                ContextIntentKind.HOUSEHOLD,
                ContextIntentKind.TRAVEL -> 35

                ContextIntentKind.COMMITMENT_PERSONAL,
                ContextIntentKind.COMMITMENT_WORK -> 30

                else -> 20
            }
        }
    }
}
