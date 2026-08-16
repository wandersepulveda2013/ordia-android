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

class ReminderScheduler(context: Context) : com.ordia.app.backup.ReminderSchedulerPort {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun schedule(task: TaskEntity) {
        val triggerAt = task.reminderAt ?: task.dueAt ?: return
        scheduleAt(task.id, triggerAt)
    }

    override fun scheduleAt(taskId: Long, triggerAt: Long) {
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

    // --- Compromisos (c.304): notificación proactiva al vencer una promesa PENDING ---

    override fun scheduleCommitmentAt(commitmentId: Long, triggerAt: Long) {
        if (commitmentId <= 0L) return
        val delay = (triggerAt - System.currentTimeMillis()).coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<CommitmentDueWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putLong(CommitmentDueWorker.KEY_COMMITMENT_ID, commitmentId).build())
            .addTag(TAG_COMMITMENTS)
            .build()
        workManager.enqueueUniqueWork(commitmentWorkName(commitmentId), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelCommitment(commitmentId: Long) {
        if (commitmentId > 0L) workManager.cancelUniqueWork(commitmentWorkName(commitmentId))
    }

    fun cancelAllCommitments() {
        workManager.cancelAllWorkByTag(TAG_COMMITMENTS)
    }

    suspend fun cancelAllCommitmentsAndAwait() = withContext(Dispatchers.IO) {
        workManager.cancelAllWorkByTag(TAG_COMMITMENTS).result.get(30, TimeUnit.SECONDS)
        Unit
    }

    fun cancelAll() {
        workManager.cancelAllWorkByTag(TAG_REMINDERS)
    }

    /** Waits for cancellation before imported reminders are re-enqueued, avoiding a cancellation race. */
    override suspend fun cancelAllAndAwait() = withContext(Dispatchers.IO) {
        workManager.cancelAllWorkByTag(TAG_REMINDERS).result.get(30, TimeUnit.SECONDS)
        Unit
    }

    companion object {
        const val TAG_REMINDERS = "ordia_task_reminders"
        const val TAG_COMMITMENTS = "ordia_commitment_reminders"
        private fun workName(taskId: Long) = "ordia_task_reminder_$taskId"
        private fun commitmentWorkName(commitmentId: Long) = "ordia_commitment_$commitmentId"
    }
}
