package com.ordia.app.context.external

import com.ordia.app.context.ContextIntentKind

/**
 * Acciones que el usuario puede realizar sobre una sugerencia externa.
 *
 * No se muestran más de 4 acciones principales simultáneamente.
 * Las acciones secundarias se muestran en un menú secundario.
 */
sealed class ExternalSuggestionAction {

    // ========================================================================
    // Acciones principales
    // ========================================================================

    /** Crear entidad (tarea, evento, nota) según el tipo detectado. */
    data object Add : ExternalSuggestionAction()

    /** Editar título, fecha, hora, prioridad, tipo, recordatorio. */
    data class Edit(
        val newTitle: String? = null,
        val newDueAt: Long? = null,
        val newPriority: Int? = null,
        val newKind: ContextIntentKind? = null
    ) : ExternalSuggestionAction()

    /** Posponer por una duración determinada. */
    data class Postpone(val duration: PostponeDuration) : ExternalSuggestionAction()

    /** Ignorar y no volver a mostrar. */
    data object Ignore : ExternalSuggestionAction()

    // ========================================================================
    // Acciones secundarias (menú)
    // ========================================================================

    /** Cambiar solo la fecha. */
    data class ChangeDate(val newDate: Long) : ExternalSuggestionAction()

    /** Cambiar solo la hora. */
    data class ChangeTime(val newTime: Long) : ExternalSuggestionAction()

    /** Forzar tipo tarea. */
    data object ConvertToTask : ExternalSuggestionAction()

    /** Forzar tipo evento. */
    data object ConvertToEvent : ExternalSuggestionAction()

    /** Guardar como nota suelta. */
    data object SaveAsNote : ExternalSuggestionAction()

    /** No detectar frases similares en el futuro. */
    data object DontDetectSimilar : ExternalSuggestionAction()

    /** Pausar atención contextual 1 hora. */
    data object PauseOneHour : ExternalSuggestionAction()

    /** Detener atención contextual permanentemente (hasta reactivación manual). */
    data object StopAttention : ExternalSuggestionAction()
}

/**
 * Duraciones predefinidas para posponer una sugerencia.
 */
enum class PostponeDuration(val label: String, val millis: Long) {
    FIFTEEN_MINUTES("15 minutos", 900_000L),
    ONE_HOUR("1 hora", 3_600_000L),
    TONIGHT("Esta noche", 0L) {  // Se calcula dinámicamente
        override fun resolveMillis(now: Long): Long {
            val calendar = java.util.Calendar.getInstance()
            calendar.timeInMillis = now
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 21)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            val tonight = calendar.timeInMillis
            return if (tonight <= now) tonight + 86_400_000L else tonight
        }
    },
    TOMORROW("Mañana", 86_400_000L),
    CUSTOM("Elegir momento", -1L);

    open fun resolveMillis(now: Long = System.currentTimeMillis()): Long {
        return now + millis
    }
}
