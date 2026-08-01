package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class DayPlannerTest {
    private val zone = ZoneId.of("America/Santo_Domingo")
    private val date = LocalDate.of(2026, 7, 29)
    private val now = DateRules.toEpochMillis(date, LocalTime.of(8, 0), zone)

    @Test
    fun urgentTasksAreScheduledBeforeNormalTasks() {
        val normal = TaskEntity(id = 1, title = "Normal", durationMinutes = 30, priority = TaskPriority.NORMAL)
        val urgent = TaskEntity(id = 2, title = "Urgente", durationMinutes = 30, priority = TaskPriority.URGENT)

        val plan = DayPlanner.build(listOf(normal, urgent), date, 9 * 60, 11 * 60, now = now, zone = zone)

        assertEquals(2L, plan.blocks.first().taskId)
        assertEquals(2, plan.blocks.size)
    }

    @Test
    fun tasksThatDoNotFitAreReported() {
        val tasks = (1L..4L).map { id -> TaskEntity(id = id, title = "Tarea $id", durationMinutes = 60) }

        val plan = DayPlanner.build(tasks, date, 9 * 60, 11 * 60, breakMinutes = 10, now = now, zone = zone)

        assertEquals(1, plan.blocks.size)
        assertEquals(3, plan.unscheduledTaskIds.size)
        assertTrue(plan.remainingMinutes >= 0)
    }

    @Test
    fun blockReasonsExplainPlacement() {
        val overdue = TaskEntity(
            id = 1, title = "Atrasada", durationMinutes = 30,
            dueAt = DateRules.toEpochMillis(date.minusDays(1), LocalTime.of(18, 0), zone)
        )
        val urgent = TaskEntity(id = 2, title = "Urgente", durationMinutes = 30, priority = TaskPriority.URGENT)
        val high = TaskEntity(id = 3, title = "Alta", durationMinutes = 30, priority = TaskPriority.HIGH)
        val scheduled = TaskEntity(
            id = 4, title = "Con hora", durationMinutes = 30,
            startAt = DateRules.toEpochMillis(date, LocalTime.of(15, 0), zone),
            dueAt = DateRules.toEpochMillis(date, LocalTime.of(16, 0), zone)
        )
        val due = TaskEntity(id = 5, title = "Vence hoy", durationMinutes = 30, dueAt = DateRules.toEpochMillis(date, LocalTime.of(18, 0), zone))
        val inbox = TaskEntity(id = 6, title = "Bandeja", durationMinutes = 30)

        val plan = DayPlanner.build(
            listOf(overdue, urgent, high, scheduled, due, inbox), date,
            9 * 60, 18 * 60, breakMinutes = 0, now = now, zone = zone
        )

        val reasons = plan.blocks.associate { it.taskId to it.reason }
        assertEquals(PlanReason.OVERDUE, reasons[1])
        assertEquals(PlanReason.URGENT, reasons[2])
        assertEquals(PlanReason.HIGH_PRIORITY, reasons[3])
        assertEquals(PlanReason.SCHEDULED_TIME, reasons[4])
        assertEquals(PlanReason.DUE_TODAY, reasons[5])
        assertEquals(PlanReason.INBOX, reasons[6])
    }

    @Test
    fun conflictReportedWhenPlanMovesScheduledTime() {
        val scheduled = TaskEntity(
            id = 4, title = "Con hora", durationMinutes = 30,
            startAt = DateRules.toEpochMillis(date, LocalTime.of(15, 0), zone),
            dueAt = DateRules.toEpochMillis(date, LocalTime.of(16, 0), zone)
        )

        val plan = DayPlanner.build(listOf(scheduled), date, 9 * 60, 18 * 60, breakMinutes = 0, now = now, zone = zone)

        // El plan la ubica a las 9:00, no a las 15:00.
        assertEquals(9 * 60, plan.blocks.first().startMinute)
        assertEquals(1, plan.conflicts.size)
        assertEquals(4L, plan.conflicts.first().taskId)
        assertEquals(PlanConflictKind.MOVED_FROM_SCHEDULED_TIME, plan.conflicts.first().kind)
    }

    @Test
    fun noConflictWhenTaskHasNoPreviousTime() {
        val due = TaskEntity(id = 5, title = "Vence hoy", durationMinutes = 30, dueAt = DateRules.toEpochMillis(date, LocalTime.of(18, 0), zone))

        val plan = DayPlanner.build(listOf(due), date, 9 * 60, 18 * 60, breakMinutes = 0, now = now, zone = zone)

        assertEquals(0, plan.conflicts.size)
    }
}
