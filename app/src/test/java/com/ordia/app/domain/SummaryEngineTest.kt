package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.data.local.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SummaryEngineTest {
    private val zone = ZoneId.of("America/Santo_Domingo")
    private val today = LocalDate.of(2026, 7, 29)
    private val now = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun at(date: LocalDate, hour: Int = 9): Long =
        date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun task(
        id: Long,
        dueAt: Long? = null,
        startAt: Long? = null,
        completed: Boolean = false,
        completedAt: Long? = null,
        status: TaskStatus = TaskStatus.PLANNED,
        durationMinutes: Int = 25
    ) = TaskEntity(
        id = id,
        title = "T$id",
        dueAt = dueAt,
        startAt = startAt,
        durationMinutes = durationMinutes,
        status = status,
        completed = completed,
        completedAt = completedAt
    )

    @Test
    fun emptyListYieldsZeroSummary() {
        val s = SummaryEngine.summarize(emptyList(), now, zone)

        assertEquals(0, s.completedToday)
        assertEquals(0, s.remainingToday)
        assertEquals(0, s.remainingMinutesToday)
        assertEquals(0, s.overdue)
        assertEquals(0, s.inboxPending)
        assertEquals(0, s.completedThisWeek)
        assertEquals(0f, s.weekDailyAverage)
    }

    @Test
    fun countsCompletedTodayAndRemainingWithMinutes() {
        val tasks = listOf(
            task(1, dueAt = at(today, 9), completed = true, completedAt = at(today, 11)),
            task(2, dueAt = at(today, 14), durationMinutes = 30),
            task(3, startAt = at(today, 16), durationMinutes = 45),
            task(4, dueAt = at(today.minusDays(2), 9))
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(1, s.completedToday)
        assertEquals(2, s.remainingToday)
        assertEquals(75, s.remainingMinutesToday)
        // task 4 venció hace dos días y sigue pendiente
        assertEquals(1, s.overdue)
    }

    @Test
    fun overdueUsesInjectedNowAndIgnoresCompletedArchivedCancelled() {
        val tasks = listOf(
            task(1, dueAt = at(today.minusDays(1), 8)),
            task(2, dueAt = at(today.minusDays(1), 8), completed = true, completedAt = at(today.minusDays(1), 20)),
            task(3, dueAt = at(today.minusDays(1), 8), status = TaskStatus.CANCELLED),
            task(4, dueAt = at(today.minusDays(1), 8)).copy(archived = true)
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(1, s.overdue)
    }

    @Test
    fun countsInboxPendingReview() {
        val tasks = listOf(
            task(1, status = TaskStatus.INBOX),
            task(2, status = TaskStatus.INBOX),
            task(3, status = TaskStatus.INBOX).copy(archived = true),
            task(4, status = TaskStatus.PLANNED)
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(2, s.inboxPending)
    }

    @Test
    fun completedThisWeekIncludesLastSevenDays() {
        val tasks = listOf(
            task(1, completed = true, completedAt = at(today)),
            task(2, completed = true, completedAt = at(today.minusDays(3))),
            task(3, completed = true, completedAt = at(today.minusDays(6), 23)),
            task(4, completed = true, completedAt = at(today.minusDays(7))),
            task(5, completed = true, completedAt = at(today.minusDays(30)))
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(3, s.completedThisWeek)
        assertEquals(3f / 7f, s.weekDailyAverage)
    }

    @Test
    fun remainingTodayCountsStartAtWhenNoDueAt() {
        val tasks = listOf(
            task(1, startAt = at(today, 14)),
            task(2, startAt = at(today, 18)),
            task(3, startAt = at(today.minusDays(1), 14)).copy(completed = true, completedAt = at(today.minusDays(1), 15))
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(2, s.remainingToday)
        assertEquals(0, s.overdue) // ninguna tiene dueAt vencido
    }

    @Test
    fun priorityIsNotPartOfSummary() {
        // Sanidad: el resumen no depende de prioridad; una tarea prioritaria
        // de hoy simplemente cuenta como pendiente de hoy.
        val tasks = listOf(
            task(1, dueAt = at(today, 9), status = TaskStatus.INBOX).copy(priority = TaskPriority.URGENT)
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(1, s.remainingToday)
        assertEquals(1, s.inboxPending)
    }

    @Test
    fun subtasksDoNotInflateOverdueCount() {
        // Un padre atrasado con dos subtareas también atrasadas debe contar
        // como 1 sola tarea raíz atrasada, no 3 (las subtareas son anidadas).
        val tasks = listOf(
            task(1, dueAt = at(today.minusDays(1), 8)),
            task(2, dueAt = at(today.minusDays(1), 8)).copy(parentTaskId = 1),
            task(3, dueAt = at(today.minusDays(1), 8)).copy(parentTaskId = 1)
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(1, s.overdue)
    }

    @Test
    fun subtasksDoNotInflateRemainingToday() {
        // Un padre con dueAt hoy y dos subtareas también hoy cuenta como 1
        // pendiente de hoy; la duración es la del padre, no la suma de las tres.
        val tasks = listOf(
            task(1, dueAt = at(today, 9), durationMinutes = 30),
            task(2, dueAt = at(today, 9), durationMinutes = 20).copy(parentTaskId = 1),
            task(3, dueAt = at(today, 9), durationMinutes = 15).copy(parentTaskId = 1)
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(1, s.remainingToday)
        assertEquals(30, s.remainingMinutesToday)
    }

    @Test
    fun subtasksDoNotInflateCompletedToday() {
        // Completar las dos subtareas auto-completa al padre: el usuario hizo
        // 2 acciones reales; el resumen debe mostrar 1 completada hoy (raíz),
        // no 3 (padre + 2 subtareas).
        val tasks = listOf(
            task(1, dueAt = at(today, 9), completed = true, completedAt = at(today, 12)),
            task(2, completed = true, completedAt = at(today, 11)).copy(parentTaskId = 1),
            task(3, completed = true, completedAt = at(today, 12)).copy(parentTaskId = 1)
        )

        val s = SummaryEngine.summarize(tasks, now, zone)

        assertEquals(1, s.completedToday)
        assertEquals(1, s.completedThisWeek)
    }
}
