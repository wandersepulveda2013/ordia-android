package com.ordia.app.backup

import com.ordia.app.data.local.TaskEntity

/**
 * Puerta de entrada a la reprogramación de recordatorios que el flujo de
 * respaldo necesita tras una restauración. La implementa
 * [com.ordia.app.reminders.ReminderScheduler].
 */
interface ReminderSchedulerPort {
    suspend fun cancelAllAndAwait()

    fun schedule(task: TaskEntity)

    /**
     * Re-encola el disparo de una tarea a un instante concreto (delay 0 si
     * `triggerAt` ya pasó). Es el espejo simétrico de [scheduleCommitmentAt]:
     * tras una restauración, las tareas con disparo YA vencido se avisan de
     * inmediato (delay 0) en lugar de quedar olvidadas — igual que los
     * compromisos vencidos. Lo implementa [com.ordia.app.reminders.ReminderScheduler.scheduleAt].
     */
    fun scheduleAt(taskId: Long, triggerAt: Long)

    suspend fun cancelAllCommitmentsAndAwait()

    fun scheduleCommitmentAt(commitmentId: Long, triggerAt: Long)
}
