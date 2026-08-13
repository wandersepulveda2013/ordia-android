package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity

/**
 * Reglas puras de recordatorios.
 *
 * Posponer (snooze) un recordatorio es un aplazamiento **transitorio** de la
 * notificación, NO un cambio en la preferencia de cuándo debe avisarse la
 * tarea. Por eso [snooze] NO sobrescribe [TaskEntity.reminderAt]:
 *
 * - [reminderAt] codifica la preferencia del usuario (p. ej. "15 min antes del
 *   vencimiento") y, para tareas recurrentes, el offset que [RecurrenceEngine]
 *   reutiliza en TODAS las ocurrencias futuras (`reminderOffset = dueAt - reminderAt`).
 *   Si snooze lo reescribiera con `now + N min`, el offset quedaría corrupto y
 *   cada próxima ocurrencia recordaría a una hora equivocada para siempre.
 * - El aplazamiento se materializa solo en el disparador del worker
 *   ([SnoozeResult.triggerAt]), que el llamador agenda con
 *   `ReminderScheduler.scheduleAt`. WorkManager persiste ese trabajo, así que
 *   el snooze sobrevive a reinicios sin tocar la preferencia original.
 */
object ReminderRules {

    /** Minutos por defecto de un aplazamiento desde una acción de recordatorio. */
    const val DEFAULT_SNOOZE_MINUTES = 10

    /**
     * Offset por defecto del recordatorio respecto al vencimiento (en ms):
     * "30 min antes" cuando el usuario no especificó uno explícito.
     */
    const val DEFAULT_REMINDER_OFFSET_MS = 30L * 60_000L

    /** Resultado de posponer: el disparador a agendar y la tarea a persistir. */
    data class SnoozeResult(val triggerAt: Long, val task: TaskEntity)

    /**
     * Calcula el aplazamiento de un recordatorio sin destruir el offset original.
     *
     * @return [SnoozeResult] con [triggerAt] = `now + minutes` (cuándo volver a
     *   avisar) y [task] = la tarea solo con `updatedAt = now` (reminderAt,
     *   dueAt y startAt se preservan intactos).
     */
    fun snooze(task: TaskEntity, now: Long, minutes: Int = DEFAULT_SNOOZE_MINUTES): SnoozeResult {
        require(minutes >= 0) { "minutes must be non-negative" }
        val triggerAt = now + minutes.toLong() * 60_000L
        return SnoozeResult(triggerAt, task.copy(updatedAt = now))
    }

    /**
     * Resuelve el [TaskEntity.reminderAt] al guardar una tarea desde el editor,
     * preservando el offset personalizado del usuario en lugar de siempre
     * forzar "30 min antes".
     *
     * Antes, el editor recomputaba `reminderAt = dueAt - 30min` en cada guardado.
     * Esto destruía offsets explícitos ("recuérdame 2 horas antes") cuando el
     * usuario editaba un campo NO relacionado (prioridad, proyecto, etiquetas,
     * flagged). Para tareas recurrentes, [RecurrenceEngine] reutiliza el offset
     * (`dueAt - reminderAt`) en TODAS las ocurrencias futuras, así que una
     * edición inocua corrompía el recordatorio de la tarea para siempre.
     *
     * Reglas (con [dueAt] != null y reminder habilitado):
     * - Si [existing] ya tiene recordatorio y el vencimiento NO cambió, se
     *   conserva exactamente el [TaskEntity.reminderAt] previo (offset intacto).
     * - Si [existing] tenía recordatorio y vencimiento y el vencimiento SÍ
     *   cambió, se traslada el offset: `dueAt - (oldDueAt - oldReminderAt)`.
     *   Así "15 min antes" sigue siendo 15 min antes en la nueva hora.
     * - En cualquier otro caso (nueva tarea, o recordatorio recién activado sin
     *   offset previo) se usa [DEFAULT_REMINDER_OFFSET_MS] ("30 min antes").
     *
     * @param existing tarea previa (null si es nueva).
     * @param reminderEnabled preferencia actual del toggle de recordatorio.
     * @param dueAt vencimiento del guardado (null desactiva el recordatorio).
     * @return timestamp del recordatorio, o null si no procede.
     */
    fun resolveReminderAt(
        existing: TaskEntity?,
        reminderEnabled: Boolean,
        dueAt: Long?,
    ): Long? {
        if (!reminderEnabled || dueAt == null) return null
        val prevReminder = existing?.reminderAt
        val prevDue = existing?.dueAt
        if (prevReminder != null && prevDue != null) {
            return if (prevDue == dueAt) prevReminder
            else dueAt - (prevDue - prevReminder)
        }
        return dueAt - DEFAULT_REMINDER_OFFSET_MS
    }
}
