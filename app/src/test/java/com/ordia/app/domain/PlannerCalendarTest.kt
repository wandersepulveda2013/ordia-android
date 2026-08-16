package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

class PlannerCalendarTest {
    private val zone = ZoneId.of("America/Santo_Domingo")
    private val date = LocalDate.of(2026, 8, 10)

    @Test
    fun taskIsIncludedWhenStartAtFallsOnDateEvenIfDueAtDoesNot() {
        val task = TaskEntity(
            id = 1,
            title = "Preparar informe",
            startAt = DateRules.toEpochMillis(date, LocalTime.of(9, 30), zone),
            dueAt = DateRules.toEpochMillis(date.plusDays(1), LocalTime.of(17, 0), zone)
        )

        val result = PlannerCalendar.tasksOnDate(listOf(task), date, zone)

        assertEquals(listOf(1L), result.map(TaskEntity::id))
    }

    @Test
    fun taskAppearsOnceWhenStartAndDueShareDate() {
        val task = TaskEntity(
            id = 2,
            title = "Enviar propuesta",
            startAt = DateRules.toEpochMillis(date, LocalTime.of(10, 0), zone),
            dueAt = DateRules.toEpochMillis(date, LocalTime.of(12, 0), zone)
        )

        assertEquals(1, PlannerCalendar.tasksOnDate(listOf(task), date, zone).size)
        assertEquals(1, PlannerCalendar.agenda(listOf(task), date, 2, zone).single().tasks.size)
    }

    @Test
    fun monthGridBuildsSixCompleteWeeksFromConfiguredWeekStart() {
        val grid = PlannerCalendar.monthGrid(YearMonth.of(2026, 8), DayOfWeek.MONDAY)

        assertEquals(42, grid.size)
        assertEquals(DayOfWeek.MONDAY, grid.first().date.dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, grid.last().date.dayOfWeek)
        assertEquals(31, grid.count(PlannerMonthDay::inDisplayedMonth))
        assertTrue(grid.any { it.date == LocalDate.of(2026, 8, 1) && it.inDisplayedMonth })
        assertFalse(grid.first().inDisplayedMonth)
    }

    @Test
    fun agendaGroupsStartAndDueDatesInChronologicalOrder() {
        val scheduled = TaskEntity(
            id = 3,
            title = "Bloque de trabajo",
            startAt = DateRules.toEpochMillis(date.plusDays(1), LocalTime.of(8, 0), zone),
            dueAt = DateRules.toEpochMillis(date.plusDays(3), LocalTime.of(17, 0), zone)
        )
        val deadline = TaskEntity(
            id = 4,
            title = "Entrega",
            dueAt = DateRules.toEpochMillis(date.plusDays(2), LocalTime.of(11, 0), zone)
        )

        val groups = PlannerCalendar.agenda(listOf(scheduled, deadline), date, 7, zone)

        assertEquals(
            listOf(date.plusDays(1), date.plusDays(2), date.plusDays(3)),
            groups.map(PlannerAgendaGroup::date)
        )
        assertEquals(listOf(3L), groups[0].tasks.map(TaskEntity::id))
        assertEquals(listOf(4L), groups[1].tasks.map(TaskEntity::id))
        assertEquals(listOf(3L), groups[2].tasks.map(TaskEntity::id))
    }

    @Test
    fun monthNavigationPreservesDayAndClampsShortMonths() {
        val january31 = LocalDate.of(2026, 1, 31)

        assertEquals(
            LocalDate.of(2026, 2, 28),
            PlannerCalendar.shiftMonthPreservingDay(january31, 1)
        )
        assertEquals(
            LocalDate.of(2025, 12, 31),
            PlannerCalendar.shiftMonthPreservingDay(january31, -1)
        )
    }

    @Test
    fun weekDatesHonorConfiguredFirstDay() {
        val week = PlannerCalendar.weekDates(date, DayOfWeek.SUNDAY)

        assertEquals(7, week.size)
        assertEquals(DayOfWeek.SUNDAY, week.first().dayOfWeek)
        assertEquals(DayOfWeek.SATURDAY, week.last().dayOfWeek)
        assertTrue(date in week)
    }
}
