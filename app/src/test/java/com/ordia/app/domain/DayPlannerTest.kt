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

    @Test
    fun taskScheduledOnDateOnlyIncludedWhenReplanFlagIsSet() {
        // Programada hoy 15:00 pero vence mañana: no es "due" hoy.
        val scheduled = TaskEntity(
            id = 8, title = "Programada", durationMinutes = 30,
            startAt = DateRules.toEpochMillis(date, LocalTime.of(15, 0), zone),
            dueAt = DateRules.toEpochMillis(date.plusDays(1), LocalTime.of(12, 0), zone)
        )

        val normal = DayPlanner.build(listOf(scheduled), date, 9 * 60, 18 * 60, breakMinutes = 0, now = now, zone = zone)
        val replan = DayPlanner.build(listOf(scheduled), date, 9 * 60, 18 * 60, breakMinutes = 0, includeScheduledOnDate = true, now = now, zone = zone)

        assertTrue(normal.blocks.isEmpty())
        assertEquals(1, replan.blocks.size)
        assertEquals(8L, replan.blocks.first().taskId)
        // La replanificación la reubica y avisa del conflicto con la hora prevista.
        assertEquals(9 * 60, replan.blocks.first().startMinute)
        assertEquals(1, replan.conflicts.size)
        assertEquals(PlanConflictKind.MOVED_FROM_SCHEDULED_TIME, replan.conflicts.first().kind)
    }

    @Test
    fun replanKeepsInboxAndDueTasksOnDate() {
        val due = TaskEntity(id = 5, title = "Vence hoy", durationMinutes = 30, dueAt = DateRules.toEpochMillis(date, LocalTime.of(18, 0), zone))
        val inbox = TaskEntity(id = 6, title = "Bandeja", durationMinutes = 30)

        val plan = DayPlanner.build(listOf(due, inbox), date, 9 * 60, 18 * 60, breakMinutes = 0, includeScheduledOnDate = true, now = now, zone = zone)

        assertEquals(2, plan.blocks.size)
        assertEquals(setOf(5L, 6L), plan.blocks.map { it.taskId }.toSet())
    }

    @Test
    fun dueOnFuturePlanDateIsNotLabeledAsToday() {
        // Un plan construido para mañana con una tarea que vence mañana no debe
        // decir "Vence hoy" (urgencia falsa), sino "Vence este día".
        val tomorrow = date.plusDays(1)
        val futureNow = DateRules.toEpochMillis(date, LocalTime.of(8, 0), zone)
        val due = TaskEntity(
            id = 7, title = "Vence mañana", durationMinutes = 30,
            dueAt = DateRules.toEpochMillis(tomorrow, LocalTime.of(18, 0), zone)
        )

        val plan = DayPlanner.build(listOf(due), tomorrow, 9 * 60, 18 * 60, breakMinutes = 0, now = futureNow, zone = zone)

        assertEquals(1, plan.blocks.size)
        assertEquals(PlanReason.DUE_ON_DATE, plan.blocks.first().reason)
    }

    @Test
    fun dueTodayIsLabeledAsTodayEvenOnFuturePlanDate() {
        // Una tarea que vence hoy (y aún no está atrasada) sigue siendo
        // "Vence hoy" aunque se muestre en un plan de otra fecha: la urgencia
        // real no cambia con la vista, y no debe degradarse a "Vence este día".
        val tomorrow = date.plusDays(1)
        val dueToday = TaskEntity(
            id = 9, title = "Vence hoy", durationMinutes = 30,
            dueAt = DateRules.toEpochMillis(date, LocalTime.of(18, 0), zone)
        )

        val plan = DayPlanner.build(listOf(dueToday), tomorrow, 9 * 60, 18 * 60, breakMinutes = 0, now = now, zone = zone)

        assertEquals(PlanReason.DUE_TODAY, plan.blocks.first().reason)
    }
}
