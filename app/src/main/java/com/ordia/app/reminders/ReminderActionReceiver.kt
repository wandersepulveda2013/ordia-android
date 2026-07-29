package com.ordia.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ordia.app.OrdiaApplication
import com.ordia.app.domain.RecurrenceEngine
import com.ordia.app.widget.OrdiaWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val taskId = intent.getLongExtra(TaskReminderWorker.KEY_TASK_ID, -1L)
        if (taskId <= 0L) {
            pending.finish()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as OrdiaApplication
                val repo = app.container.taskRepository
                val task = repo.get(taskId) ?: return@launch
                when (intent.action) {
                    ACTION_COMPLETE -> {
                        val now = System.currentTimeMillis()
                        repo.update(task.copy(completed = true, status = com.ordia.app.data.local.TaskStatus.COMPLETED, completedAt = now, updatedAt = now))
                        RecurrenceEngine.nextOccurrence(task, now)?.let { next ->
                            val newId = repo.add(next)
                            app.container.reminderScheduler.schedule(next.copy(id = newId))
                        }
                        context.getSystemService(android.app.NotificationManager::class.java).cancel(taskId.hashCode())
                    }
                    ACTION_SNOOZE -> app.container.reminderScheduler.scheduleAt(taskId, System.currentTimeMillis() + 10 * 60_000L)
                }
                OrdiaWidgetUpdater.updateAll(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.ordia.app.action.COMPLETE_TASK"
        const val ACTION_SNOOZE = "com.ordia.app.action.SNOOZE_TASK"
    }
}
