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

    suspend fun cancelAllCommitmentsAndAwait()

    fun scheduleCommitmentAt(commitmentId: Long, triggerAt: Long)
}
