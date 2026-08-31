package com.ordia.app.reminders
import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
class TaskReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result = Result.success()
}
