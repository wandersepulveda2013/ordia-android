package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderSyncTest {

    private fun task(id: Long, status: TaskStatus = TaskStatus.INBOX, reminderAt: Long? = null, dueAt: Long? = null) =
        TaskEntity(
            id = id,
            title = "Tarea $id",
            reminderAt = reminderAt,
            dueAt = dueAt,
            status = status
        )

    @Test
    fun usesReminderAtWhenPresent() {
        val now = 1_000L
        val reminderAt = 5_000L
        val tasks = listOf(task(1, reminderAt = reminderAt, dueAt = 9_000L))

        val triggers = ReminderSync.triggers(tasks, now)

        assertEquals(listOf(1L to 5_000L), triggers)
    }

    @Test
    fun fallsBackToDueAtWhenReminderIsNull() {
        val now = 1_000L
        val tasks = listOf(task(2, dueAt = 8_000L))

        val triggers = ReminderSync.triggers(tasks, now)

        assertEquals(listOf(2L to 8_000L), triggers)
    }

    @Test
    fun ignoresPastTriggers() {
        val now = 10_000L
        val tasks = listOf(
            task(1, reminderAt = 5_000L),
            task(2, dueAt = 9_999L),
            task(3, dueAt = 11_000L)
        )

        val triggers = ReminderSync.triggers(tasks, now)

        assertEquals(listOf(3L to 11_000L), triggers)
    }

    @Test
    fun ignoresCompletedArchivedAndCancelled() {
        val now = 1_000L
        val tasks = listOf(
            task(1, TaskStatus.COMPLETED, dueAt = 5_000L).copy(completed = true),
            task(2, TaskStatus.CANCELLED, dueAt = 5_000L),
            task(3, TaskStatus.INBOX, dueAt = 5_000L).copy(archived = true),
            task(4, TaskStatus.INBOX, dueAt = 5_000L)
        )

        val triggers = ReminderSync.triggers(tasks, now)

        assertEquals(listOf(4L to 5_000L), triggers)
    }

    @Test
    fun ignoresTasksWithoutAnyTrigger() {
        val tasks = listOf(task(1))

        assertTrue(ReminderSync.triggers(tasks, now = 1_000L).isEmpty())
        assertTrue(ReminderSync.triggers(emptyList(), now = 1_000L).isEmpty())
    }

    // --- overdueNow: espejo simétrico de CommitmentReminderSync.overdueNow (c.372) ---
    // Tras un restore, BackupManager cancela todos los WorkManager jobs y sólo
    // re-encola los FUTUROS. Una tarea con disparo pasado quedaba sin aviso (olvido).
    // overdueNow recupera esas tareas para avisarlas de inmediato (delay 0).

    @Test
    fun overdueNowReturnsActiveWhoseTriggerHasPassed() {
        val now = 10_000L
        val tasks = listOf(
            task(1, reminderAt = 5_000L, dueAt = 9_000L),  // reminder pasado
            task(2, dueAt = 10_000L),                       // due justo ahora
            task(3, dueAt = 11_000L),                       // futuro
            task(4)                                          // sin disparo
        )

        val overdue = ReminderSync.overdueNow(tasks, now)

        assertEquals(listOf(1L, 2L), overdue)
    }

    @Test
    fun overdueNowFallsBackToDueAtWhenReminderIsNull() {
        val now = 10_000L
        val tasks = listOf(task(2, dueAt = 8_000L))

        assertEquals(listOf(2L), ReminderSync.overdueNow(tasks, now))
    }

    @Test
    fun overdueNowIgnoresCompletedArchivedAndCancelled() {
        val now = 10_000L
        val tasks = listOf(
            task(1, TaskStatus.COMPLETED, dueAt = 5_000L).copy(completed = true),
            task(2, TaskStatus.CANCELLED, dueAt = 5_000L),
            task(3, TaskStatus.INBOX, dueAt = 5_000L).copy(archived = true),
            task(4, TaskStatus.INBOX, dueAt = 5_000L)
        )

        val overdue = ReminderSync.overdueNow(tasks, now)

        assertEquals(listOf(4L), overdue)
    }

    @Test
    fun overdueNowIgnoresTasksWithoutAnyTrigger() {
        val now = 10_000L

        assertTrue(ReminderSync.overdueNow(listOf(task(1)), now).isEmpty())
        assertTrue(ReminderSync.overdueNow(emptyList(), now).isEmpty())
    }

    @Test
    fun overdueNowSymmetricWithCommitmentReminderSyncShape() {
        // Paridad estructural con CommitmentReminderSync.overdueNow: devuelve
        // List<Long> de ids, sin duplicados, en el orden de entrada.
        val now = 10_000L
        val tasks = listOf(
            task(1, dueAt = 5_000L),
            task(2, dueAt = 5_000L),
            task(3, dueAt = 15_000L)
        )

        val overdue = ReminderSync.overdueNow(tasks, now)

        assertEquals(listOf(1L, 2L), overdue)
    }
}
