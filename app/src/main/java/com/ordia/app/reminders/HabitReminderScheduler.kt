package com.ordia.app.reminders

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ordia.app.data.local.HabitEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class HabitReminderScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun schedule(habit: HabitEntity) {
        val reminderMinutes = habit.reminderMinutes ?: return
        if (habit.id <= 0L || habit.archived) return
        val request = PeriodicWorkRequestBuilder<HabitReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayUntilNext(reminderMinutes), TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putLong(HabitReminderWorker.KEY_HABIT_ID, habit.id).build())
            .addTag(TAG_HABIT_REMINDERS)
            .build()
        workManager.enqueueUniquePeriodicWork(workName(habit.id), ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel(habitId: Long) {
        if (habitId > 0L) workManager.cancelUniqueWork(workName(habitId))
    }

    fun cancelAll() {
        workManager.cancelAllWorkByTag(TAG_HABIT_REMINDERS)
    }

    private fun delayUntilNext(reminderMinutes: Int): Long {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val time = LocalTime.of((reminderMinutes / 60).coerceIn(0, 23), (reminderMinutes % 60).coerceIn(0, 59))
        var triggerInstant = today.atTime(time).atZone(zone).toInstant()
        val now = System.currentTimeMillis()
        if (triggerInstant.toEpochMilli() <= now) {
            triggerInstant = today.plusDays(1).atTime(time).atZone(zone).toInstant()
        }
        return (triggerInstant.toEpochMilli() - now).coerceAtLeast(0)
    }

    companion object {
        const val TAG_HABIT_REMINDERS = "ordia_habit_reminders"
        private fun workName(habitId: Long) = "ordia_habit_reminder_$habitId"
    }
}
