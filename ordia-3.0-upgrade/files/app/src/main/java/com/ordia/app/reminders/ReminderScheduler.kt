package com.ordia.app.reminders

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ordia.app.data.local.TaskEntity
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReminderScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun schedule(task: TaskEntity) {
        val triggerAt = task.reminderAt ?: task.dueAt ?: return
        scheduleAt(task.id, triggerAt)
    }

    fun scheduleAt(taskId: Long, triggerAt: Long) {
        if (taskId <= 0L) return
        val delay = (triggerAt - System.currentTimeMillis()).coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<TaskReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putLong(TaskReminderWorker.KEY_TASK_ID, taskId).build())
            .addTag(TAG_REMINDERS)
            .build()
        workManager.enqueueUniqueWork(workName(taskId), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(taskId: Long) {
        if (taskId > 0L) workManager.cancelUniqueWork(workName(taskId))
    }

    fun cancelAll() {
        workManager.cancelAllWorkByTag(TAG_REMINDERS)
    }

    /** Waits for cancellation before imported reminders are re-enqueued, avoiding a cancellation race. */
    suspend fun cancelAllAndAwait() = withContext(Dispatchers.IO) {
        workManager.cancelAllWorkByTag(TAG_REMINDERS).result.get(30, TimeUnit.SECONDS)
    }

    companion object {
        const val TAG_REMINDERS = "ordia_task_reminders"
        private fun workName(taskId: Long) = "ordia_task_reminder_$taskId"
    }
}
