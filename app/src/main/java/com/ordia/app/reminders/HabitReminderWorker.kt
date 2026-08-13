package com.ordia.app.reminders

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ordia.app.MainActivity
import com.ordia.app.OrdiaApplication
import com.ordia.app.R
import com.ordia.app.domain.HabitRules
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class HabitReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val habitId = inputData.getLong(KEY_HABIT_ID, -1L)
        if (habitId <= 0L) return Result.failure()
        val app = applicationContext as? OrdiaApplication ?: return Result.failure()
        val habit = app.container.habitRepository.get(habitId) ?: return Result.success()
        if (habit.archived || habit.reminderMinutes == null) return Result.success()

        // Si el hábito ya cumplió la meta hoy, no molestamos.
        val today = LocalDate.now()
        if (HabitRules.isScheduled(habit, today) &&
            app.container.habitRepository.let { repo ->
                val logs = repo.history(habitId)
                HabitRules.countFor(logs, habitId, today) >= habit.targetPerPeriod
            }
        ) return Result.success()

        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        TaskReminderWorker.createChannel(applicationContext)
        val openIntent = android.app.PendingIntent.getActivity(
            applicationContext,
            habitId.hashCode(),
            Intent(applicationContext, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_DESTINATION, "habits"),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val detail = habit.details.takeIf { it.isNotBlank() } ?: "Un pequeño paso mantiene la racha."
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ordia)
            .setContentTitle(habit.title)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        applicationContext.getSystemService(android.app.NotificationManager::class.java)
            .notify(habitId.hashCode(), notification)
        return Result.success()
    }

    companion object {
        const val KEY_HABIT_ID = "habit_id"
        private const val CHANNEL_ID = "ordia_reminders"
    }
}
