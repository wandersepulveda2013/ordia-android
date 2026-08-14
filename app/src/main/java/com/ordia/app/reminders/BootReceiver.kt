package com.ordia.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ordia.app.OrdiaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED) {

            val pendingResult = goAsync()
            val app = context.applicationContext as OrdiaApplication
            val container = app.container

            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    // Reschedule Tasks
                    val tasks = container.taskRepository.tasks.firstOrNull() ?: emptyList()
                    val pendingTasksWithReminders = tasks.filter { !it.completed && !it.archived && (it.reminderAt != null || it.dueAt != null) }
                    pendingTasksWithReminders.forEach { task ->
                        container.reminderScheduler.schedule(task)
                    }

                    // Reschedule Habits
                    val habits = container.habitRepository.habits.firstOrNull() ?: emptyList()
                    val activeHabitsWithReminders = habits.filter { !it.archived && it.reminderMinutes != null }
                    activeHabitsWithReminders.forEach { habit ->
                        container.habitReminderScheduler.schedule(habit)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
