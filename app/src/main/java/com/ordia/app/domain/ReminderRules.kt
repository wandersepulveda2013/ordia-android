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
}
