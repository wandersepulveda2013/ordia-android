package com.ordia.app.domain

import com.ordia.app.data.local.RecurrenceFrequency
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskStatus
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

object RecurrenceEngine {
    fun nextOccurrence(task: TaskEntity, completedAt: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): TaskEntity? {
        if (task.recurrence == RecurrenceFrequency.NONE) return null
        val interval = task.recurrenceInterval.coerceAtLeast(1).toLong()
        val base = task.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone) }
            ?: Instant.ofEpochMilli(completedAt).atZone(zone)
        val next = when (task.recurrence) {
            RecurrenceFrequency.NONE -> return null
            RecurrenceFrequency.DAILY -> base.plusDays(interval)
            RecurrenceFrequency.WEEKLY -> nextWeekly(base, interval, task.recurrenceDays)
            RecurrenceFrequency.MONTHLY -> base.plusMonths(interval)
            RecurrenceFrequency.YEARLY -> base.plusYears(interval)
        }
        val reminderOffset = if (task.dueAt != null && task.reminderAt != null) task.dueAt - task.reminderAt else null
        return task.copy(
            id = 0,
            dueAt = next.toInstant().toEpochMilli(),
            reminderAt = reminderOffset?.let { next.toInstant().toEpochMilli() - it },
            status = TaskStatus.PLANNED,
            completed = false,
            completedAt = null,
            createdAt = completedAt,
            updatedAt = completedAt
        )
    }

    private fun nextWeekly(base: ZonedDateTime, interval: Long, recurrenceDays: String): ZonedDateTime {
        val days = recurrenceDays.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.sorted()
        if (days.isEmpty()) return base.plusWeeks(interval)
        val current = base.dayOfWeek.value
        val laterThisCycle = days.firstOrNull { it > current }
        return if (laterThisCycle != null) {
            base.plusDays((laterThisCycle - current).toLong())
        } else {
            val first = days.first()
            base.plusWeeks(interval).minusDays((base.dayOfWeek.value - first).toLong())
        }
    }
}
