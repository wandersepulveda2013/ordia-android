package com.ordia.app.domain

import com.ordia.app.data.local.RecurrenceFrequency
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.data.local.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ReminderRulesTest {
    private val zone = ZoneId.of("America/Santo_Domingo")

    @Test
    fun snooze_triggerIsNowPlusDefaultMinutes() {
        val now = 1_700_000_000_000L
        val due = now + 2 * 60 * 60_000L
        val reminderAt = due - 15 * 60_000L
        val task = TaskEntity(id = 1, title = "Reunión", dueAt = due, reminderAt = reminderAt)

        val result = ReminderRules.snooze(task, now)

        assertEquals(now + ReminderRules.DEFAULT_SNOOZE_MINUTES * 60_000L, result.triggerAt)
    }

    @Test
    fun snooze_customMinutesComputesTrigger() {
        val now = 1_700_000_000_000L
        val task = TaskEntity(id = 1, title = "X", dueAt = now + 3 * 60 * 60_000L)

        val result = ReminderRules.snooze(task, now, minutes = 25)

        assertEquals(now + 25 * 60_000L, result.triggerAt)
    }

    @Test
    fun snooze_preservesReminderAtAndDueAt() {
        val now = 1_700_000_000_000L
        val due = now + 2 * 60 * 60_000L
        val reminderAt = due - 15 * 60_000L
        val task = TaskEntity(id = 1, title = "Reunión", dueAt = due, reminderAt = reminderAt)

        val result = ReminderRules.snooze(task, now)

        // Clave: la preferencia original (reminderAt) NO se toca. Solo se aplaza
        // el disparo del worker.
        assertEquals(reminderAt, result.task.reminderAt)
        assertEquals(due, result.task.dueAt)
    }

    @Test
    fun snooze_marksUpdatedAt() {
        val now = 1_700_000_000_000L
        val task = TaskEntity(id = 1, title = "X", dueAt = now + 3 * 60 * 60_000L, updatedAt = now - 5_000L)

        val result = ReminderRules.snooze(task, now)

        assertEquals(now, result.task.updatedAt)
    }

    @Test
    fun snooze_keepsOtherFieldsIntact() {
        val now = 1_700_000_000_000L
        val due = now + 2 * 60 * 60_000L
        val task = TaskEntity(
            id = 7, title = "Clase", dueAt = due, reminderAt = due - 30 * 60_000L,
            durationMinutes = 45, priority = TaskPriority.HIGH,
            status = TaskStatus.PLANNED, recurrence = RecurrenceFrequency.WEEKLY,
            recurrenceDays = "1,3,5"
        )

        val result = ReminderRules.snooze(task, now)

        assertEquals(7L, result.task.id)
        assertEquals("Clase", result.task.title)
        assertEquals(45, result.task.durationMinutes)
        assertEquals(TaskPriority.HIGH, result.task.priority)
        assertEquals(TaskStatus.PLANNED, result.task.status)
        assertEquals(RecurrenceFrequency.WEEKLY, result.task.recurrence)
        assertEquals("1,3,5", result.task.recurrenceDays)
    }

    /**
     * Invariante de integridad de datos: snoozear una tarea recurrente NO debe
     * corromper el offset de recordatorio que [RecurrenceEngine] reutiliza en
     * cada ocurrencia futura. Antes, snooze reescribía reminderAt=now+10min, lo
     * que hacía que "15 min antes" se volviera "5 min antes" para siempre.
     */
    @Test
    fun snoozeThenComplete_preservesReminderOffsetAcrossRecurrence() {
        val dueDate = LocalDate.of(2026, 8, 13)
        val now = DateRules.toEpochMillis(dueDate, LocalTime.of(9, 45), zone) // 15 min antes de las 10:00
        val due = DateRules.toEpochMillis(dueDate, LocalTime.of(10, 0), zone)
        val reminderAt = due - 15 * 60_000L // 9:45, "15 min antes"

        val task = TaskEntity(
            id = 1, title = "Diaria", dueAt = due, reminderAt = reminderAt,
            recurrence = RecurrenceFrequency.DAILY
        )

        // El usuario pospone el recordatorio 10 min desde las 9:45.
        val snoozed = ReminderRules.snooze(task, now)

        // La preferencia original se mantiene: el offset 15 min sigue intacto.
        assertEquals(15 * 60_000L, snoozed.task.dueAt!! - snoozed.task.reminderAt!!)

        // Al completar, la próxima ocurrencia reutiliza ese offset 15 min.
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(snoozed.task, completedAt = due, zone = zone))
        assertEquals(15 * 60_000L, next.dueAt!! - next.reminderAt!!)

        // Sanity: tras el bug, el offset hubiera quedado en 5 min (15-10), no 15.
        assertNotEquals(5 * 60_000L, next.dueAt!! - next.reminderAt!!)
    }
}
