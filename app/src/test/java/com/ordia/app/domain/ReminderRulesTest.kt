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

    // ---- resolveReminderAt (preservación de offset en el editor) ----

    @Test
    fun resolveReminderAt_disabledReturnsNull() {
        val due = 1_700_000_000_000L
        assertEquals(null, ReminderRules.resolveReminderAt(null, reminderEnabled = false, dueAt = due))
    }

    @Test
    fun resolveReminderAt_nullDueReturnsNull() {
        assertEquals(null, ReminderRules.resolveReminderAt(null, reminderEnabled = true, dueAt = null))
    }

    @Test
    fun resolveReminderAt_newTaskUsesDefaultOffset() {
        val due = 1_700_000_000_000L
        assertEquals(
            due - ReminderRules.DEFAULT_REMINDER_OFFSET_MS,
            ReminderRules.resolveReminderAt(null, reminderEnabled = true, dueAt = due),
        )
    }

    @Test
    fun resolveReminderAt_existingWithoutDueTimeUsesDefaultOffset() {
        val due = 1_700_000_000_000L
        // Recordatorio recién activado: existing sin reminderAt previo.
        val existing = TaskEntity(id = 1, title = "X", dueAt = due, reminderAt = null)
        assertEquals(
            due - ReminderRules.DEFAULT_REMINDER_OFFSET_MS,
            ReminderRules.resolveReminderAt(existing, reminderEnabled = true, dueAt = due),
        )
    }

    /**
     * Invariante central: editar un campo NO relacionado (p. ej. prioridad)
     * NO debe alterar el offset de recordatorio explícito del usuario.
     * Antes, el editor forzaba siempre due-30min y destruía un offset de 2h.
     */
    @Test
    fun resolveReminderAt_editingUnrelatedField_preservesCustomOffset() {
        val due = 1_700_000_000_000L
        val customOffset = 2 * 60 * 60_000L // "2 horas antes"
        val existing = TaskEntity(id = 1, title = "X", dueAt = due, reminderAt = due - customOffset)

        // El usuario abre el editor, cambia la prioridad, y guarda con el mismo dueAt.
        val result = ReminderRules.resolveReminderAt(existing, reminderEnabled = true, dueAt = due)
        assertEquals(due - customOffset, result)
        assertEquals(customOffset, due - result!!) // offset intacto, no 30 min
    }

    /**
     * Al mover el vencimiento, el offset se traslada (no se resetea a 30 min):
     * "15 min antes" sigue siendo 15 min antes en la nueva hora.
     */
    @Test
    fun resolveReminderAt_changingDue_translatesOffset() {
        val offset = 15 * 60_000L
        val oldDue = 1_700_000_000_000L
        val newDue = oldDue + 3 * 60 * 60_000L // 3h después
        val existing = TaskEntity(id = 1, title = "X", dueAt = oldDue, reminderAt = oldDue - offset)

        val result = ReminderRules.resolveReminderAt(existing, reminderEnabled = true, dueAt = newDue)
        assertEquals(newDue - offset, result)
    }

    /**
     * Para tareas recurrentes, preservar el offset en el editor evita que una
     * edición inocua corrompa el recordatorio de TODAS las ocurrencias futuras.
     */
    @Test
    fun resolveReminderAt_recurrenceEditKeepsOffsetForNextOccurrence() {
        val zone = ZoneId.of("America/Santo_Domingo")
        val dueDate = LocalDate.of(2026, 8, 13)
        val due = DateRules.toEpochMillis(dueDate, LocalTime.of(10, 0), zone)
        val offset = 15 * 60_000L
        val existing = TaskEntity(
            id = 1, title = "Diaria", dueAt = due, reminderAt = due - offset,
            recurrence = RecurrenceFrequency.DAILY,
        )

        // El usuario edita la prioridad (mismo dueAt) y guarda.
        val preserved = existing.copy(
            reminderAt = ReminderRules.resolveReminderAt(existing, reminderEnabled = true, dueAt = due),
        )

        val next = requireNotNull(RecurrenceEngine.nextOccurrence(preserved, completedAt = due, zone = zone))
        assertEquals(offset, next.dueAt!! - next.reminderAt!!)
    }

    @Test
    fun resolveReminderAt_togglingOffReturnsNull() {
        val due = 1_700_000_000_000L
        val existing = TaskEntity(id = 1, title = "X", dueAt = due, reminderAt = due - 30 * 60_000L)
        assertEquals(null, ReminderRules.resolveReminderAt(existing, reminderEnabled = false, dueAt = due))
    }

    @Test
    fun resolveReminderAt_clearingDueReturnsNull() {
        val existing = TaskEntity(id = 1, title = "X", dueAt = 1_700_000_000_000L, reminderAt = 1_699_998_200_000L)
        assertEquals(null, ReminderRules.resolveReminderAt(existing, reminderEnabled = true, dueAt = null))
    }
}
