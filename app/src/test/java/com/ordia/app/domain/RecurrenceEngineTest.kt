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

    @Test fun monthly_anchorsToDayOfMonthAndSkipsMonthsLackingIt() {
        // "el 31 de cada mes": ene 31 + 1 mes NO debe dar feb 28 (clamp),
        // sino saltar a mar 31 (feb no tiene 31). Coincide con el anclaje del parser.
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 1, 31), LocalTime.of(8, 0), zone)
        val task = TaskEntity(title = "Mensual 31", dueAt = due, recurrence = RecurrenceFrequency.MONTHLY)
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = due, zone = zone))
        assertEquals(LocalDate.of(2026, 3, 31), DateRules.toLocalDate(next.dueAt!!, zone))
        assertEquals(LocalTime.of(8, 0), DateRules.toLocalTime(next.dueAt, zone))
    }

    @Test fun monthly_preservesDayForCommonDays() {
        // Dias 1-28: comportamiento estable, sin deriva (caso mas comun: "el 15 de cada mes").
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 1, 15), LocalTime.of(9, 0), zone)
        val task = TaskEntity(title = "Renta", dueAt = due, recurrence = RecurrenceFrequency.MONTHLY)
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = due, zone = zone))
        assertEquals(LocalDate.of(2026, 2, 15), DateRules.toLocalDate(next.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(next.dueAt, zone))
    }

    @Test fun monthly_advancesPastCompletedAt() {
        // Si se completa despues del vencimiento, no regresa al pasado.
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 1, 31), LocalTime.NOON, zone)
        val task = TaskEntity(title = "Mensual 31", dueAt = due, recurrence = RecurrenceFrequency.MONTHLY)
        val late = DateRules.toEpochMillis(LocalDate.of(2026, 3, 1), LocalTime.NOON, zone)
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = late, zone = zone))
        assertEquals(LocalDate.of(2026, 3, 31), DateRules.toLocalDate(next.dueAt!!, zone))
    }
}
