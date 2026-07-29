package com.ordia.app.domain

import com.ordia.app.data.local.RecurrenceFrequency
import com.ordia.app.data.local.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class RecurrenceEngineTest {
    private val zone = ZoneId.of("America/Santo_Domingo")

    @Test fun none_hasNoNextOccurrence() {
        assertNull(RecurrenceEngine.nextOccurrence(TaskEntity(title = "Una vez"), zone = zone))
    }

    @Test fun daily_preservesTimeAndReminderOffset() {
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 7, 29), LocalTime.of(9, 30), zone)
        val task = TaskEntity(title = "Diaria", dueAt = due, reminderAt = due - 30 * 60_000L, recurrence = RecurrenceFrequency.DAILY)
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = due, zone = zone))
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(next.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 30), DateRules.toLocalTime(next.dueAt, zone))
        assertEquals(30 * 60_000L, next.dueAt - next.reminderAt!!)
        assertFalse(next.completed)
    }

    @Test fun weekly_usesSelectedWeekdays() {
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 7, 29), LocalTime.NOON, zone) // miércoles
        val task = TaskEntity(title = "Lunes y viernes", dueAt = due, recurrence = RecurrenceFrequency.WEEKLY, recurrenceDays = "1,5")
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = due, zone = zone))
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(next.dueAt!!, zone))
    }
}
