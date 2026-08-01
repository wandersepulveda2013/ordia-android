package com.ordia.app.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ordia.app.MainActivity
import com.ordia.app.OrdiaApplication
import com.ordia.app.R
import com.ordia.app.domain.DateRules
import com.ordia.app.domain.QuietHours
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId

class TaskReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(KEY_TASK_ID, -1L)
        if (taskId <= 0L) return Result.failure()
        val app = applicationContext as? OrdiaApplication ?: return Result.failure()
        val task = app.container.taskRepository.get(taskId) ?: return Result.success()
        if (task.completed || task.archived || task.status == com.ordia.app.data.local.TaskStatus.CANCELLED) return Result.success()

        val preferences = app.container.preferencesRepository.preferences.first()
        val now = Instant.now().atZone(ZoneId.systemDefault())
        val currentMinutes = now.hour * 60 + now.minute
        if (QuietHours.contains(currentMinutes, preferences.quietStartMinutes, preferences.quietEndMinutes)) {
            app.container.reminderScheduler.scheduleAt(
                taskId,
                QuietHours.nextEndMillis(System.currentTimeMillis(), preferences.quietStartMinutes, preferences.quietEndMinutes)
            )
            return Result.success()
        }
        // Sin permiso de notificaciones (API 33+) no se puede mostrar el
        // recordatorio. En lugar de fingir éxito, se reintenta un número
        // acotado de veces (por si el usuario otorga el permiso) y luego se
        // descarta el trabajo de forma explícita.
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return if (runAttemptCount < MAX_PERMISSION_RETRIES) Result.retry() else Result.failure()
        }

        createChannel(applicationContext)
        val openIntent = PendingIntent.getActivity(
            applicationContext,
            taskId.hashCode(),
            Intent(applicationContext, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TASK_ID, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val completeIntent = PendingIntent.getBroadcast(
            applicationContext,
            taskId.hashCode() + 1,
            Intent(applicationContext, ReminderActionReceiver::class.java)
                .setAction(ReminderActionReceiver.ACTION_COMPLETE)
                .putExtra(KEY_TASK_ID, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozeIntent = PendingIntent.getBroadcast(
            applicationContext,
            taskId.hashCode() + 2,
            Intent(applicationContext, ReminderActionReceiver::class.java)
                .setAction(ReminderActionReceiver.ACTION_SNOOZE)
                .putExtra(KEY_TASK_ID, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val detail = buildString {
            append(task.details.takeIf { it.isNotBlank() } ?: applicationContext.getString(R.string.reminder_default_detail))
            task.dueAt?.let { append(" · ${DateRules.formatTime(it)}") }
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ordia)
            .setContentTitle(task.title)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, applicationContext.getString(R.string.reminder_action_complete), completeIntent)
            .addAction(0, applicationContext.getString(R.string.reminder_action_snooze_10min), snoozeIntent)
            .build()

        applicationContext.getSystemService(NotificationManager::class.java)
            .notify(taskId.hashCode(), notification)
        return Result.success()
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        private const val CHANNEL_ID = "ordia_reminders"
        private const val MAX_PERMISSION_RETRIES = 5

        fun createChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.reminders_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = context.getString(R.string.reminders_channel_description) }
            )
        }
    }
}
