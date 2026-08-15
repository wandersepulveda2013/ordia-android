package com.ordia.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ordia.app.OrdiaApplication
import com.ordia.app.domain.CommitmentReminderSync
import com.ordia.app.domain.ReminderSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Re-sincroniza los recordatorios cuando cambia la hora, la fecha o la zona
 * horaria. Estas acciones del sistema no requieren permisos adicionales.
 *
 * El arranque tras un reinicio lo cubre WorkManager con su restauración
 * persistente de trabajos; por eso este receiver no escucha BOOT_COMPLETED y
 * no añade el permiso RECEIVE_BOOT_COMPLETED.
 */
class ReminderResyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIMEZONE_CHANGED &&
            intent.action != Intent.ACTION_TIME_CHANGED &&
            intent.action != Intent.ACTION_DATE_CHANGED
        ) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as OrdiaApplication
                val tasks = app.container.taskRepository.tasks.first()
                val now = System.currentTimeMillis()
                ReminderSync.triggers(tasks, now).forEach { (taskId, triggerAt) ->
                    app.container.reminderScheduler.scheduleAt(taskId, triggerAt)
                }
                // Compromisos: mismo principio. Un cambio de zona/hora puede
                // adelantar el vencimiento de una promesa y dejarla olvidada
                // si no se reprograma aquí.
                val commitments = app.container.conversationRepository.getCommitmentsNow()
                CommitmentReminderSync.triggers(commitments, now).forEach { (id, triggerAt) ->
                    app.container.reminderScheduler.scheduleCommitmentAt(id, triggerAt)
                }
                CommitmentReminderSync.overdueNow(commitments, now).forEach { id ->
                    app.container.reminderScheduler.scheduleCommitmentAt(id, now)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
