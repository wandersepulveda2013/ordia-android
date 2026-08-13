package com.ordia.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ordia.app.OrdiaApplication
import com.ordia.app.data.local.TaskStatus
import com.ordia.app.domain.RecurrenceEngine
import com.ordia.app.domain.ReminderRules
import com.ordia.app.domain.TaskMutationGate
import com.ordia.app.widget.OrdiaWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val taskId = intent.getLongExtra(TaskReminderWorker.KEY_TASK_ID, -1L)
        if (taskId <= 0L) { pending.finish(); return }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as OrdiaApplication
                TaskMutationGate.mutex.withLock {
                    val repo = app.container.taskRepository
                    val task = repo.get(taskId) ?: return@withLock
                    when (intent.action) {
                        ACTION_COMPLETE -> {
                            if (task.completed || task.archived || task.status == TaskStatus.CANCELLED) return@withLock
                            val now = System.currentTimeMillis()
                            repo.update(task.copy(completed = true, status = TaskStatus.COMPLETED, completedAt = now, updatedAt = now))
                            app.container.reminderScheduler.cancel(taskId)
                            RecurrenceEngine.nextOccurrence(task, now)?.let { next ->
                                val newId = repo.add(next)
                                app.container.reminderScheduler.schedule(next.copy(id = newId))
                            }
                            context.getSystemService(android.app.NotificationManager::class.java).cancel(taskId.hashCode())
                        }
                        ACTION_SNOOZE -> if (!task.completed && !task.archived && task.status != TaskStatus.CANCELLED) {
                            // Snooze NO sobrescribe reminderAt: codifica la preferencia
                            // del usuario y, en recurrentes, el offset que reutiliza
                            // RecurrenceEngine en cada ocurrencia futura. Sobrescribirlo
                            // con now+10 corrompería ese offset para siempre. El aplazamiento
                            // vive solo en el worker (persistido por WorkManager).
                            val now = System.currentTimeMillis()
                            val snoozed = ReminderRules.snooze(task, now)
                            repo.update(snoozed.task)
                            app.container.reminderScheduler.scheduleAt(taskId, snoozed.triggerAt)
                        }
                    }
                }
                OrdiaWidgetUpdater.updateAll(context)
            } finally { pending.finish() }
        }
    }
    companion object {
        const val ACTION_COMPLETE = "com.ordia.app.action.COMPLETE_TASK"
        const val ACTION_SNOOZE = "com.ordia.app.action.SNOOZE_TASK"
    }
}
