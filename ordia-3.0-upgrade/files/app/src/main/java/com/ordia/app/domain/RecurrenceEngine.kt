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
        val base = task.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone) } ?: Instant.ofEpochMilli(completedAt).atZone(zone)
        var next = advance(base, interval, task.recurrence, task.recurrenceDays)
        var guard = 0
        while (next.toInstant().toEpochMilli() <= completedAt && guard++ < 10_000) {
            next = advance(next, interval, task.recurrence, task.recurrenceDays)
        }
        if (next.toInstant().toEpochMilli() <= completedAt) return null
        val nextDue = next.toInstant().toEpochMilli()
        val reminderOffset = if (task.dueAt != null && task.reminderAt != null) task.dueAt - task.reminderAt else null
        val startOffset = if (task.dueAt != null && task.startAt != null) task.dueAt - task.startAt else null
        return task.copy(
            id = 0,
            startAt = startOffset?.let { nextDue - it },
            dueAt = nextDue,
            reminderAt = reminderOffset?.let { nextDue - it },
            status = TaskStatus.PLANNED,
            completed = false,
            completedAt = null,
            createdAt = completedAt,
            updatedAt = completedAt
        )
    }

    private fun advance(base: ZonedDateTime, interval: Long, frequency: RecurrenceFrequency, days: String): ZonedDateTime = when (frequency) {
        RecurrenceFrequency.NONE -> base
        RecurrenceFrequency.DAILY -> base.plusDays(interval)
        RecurrenceFrequency.WEEKLY -> nextWeekly(base, interval, days)
        RecurrenceFrequency.MONTHLY -> base.plusMonths(interval)
        RecurrenceFrequency.YEARLY -> base.plusYears(interval)
    }

    private fun nextWeekly(base: ZonedDateTime, interval: Long, recurrenceDays: String): ZonedDateTime {
        val days = recurrenceDays.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.distinct().sorted()
        if (days.isEmpty()) return base.plusWeeks(interval)
        val current = base.dayOfWeek.value
        val later = days.firstOrNull { it > current }
        return if (later != null) base.plusDays((later - current).toLong())
        else base.plusWeeks(interval).minusDays((current - days.first()).toLong())
    }
}
