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

    // ── Contrato de navegación mensual estable (anclaje + desplazamiento absoluto) ──
    // La UI de planificación NO debe aplicar shiftMonthPreservingDay sobre la fecha
    // YA clampeada del mes intermedio: perdería el día original al cruzar un mes
    // corto. Debe llevar el anclaje (el día que el usuario eligió) + un desplazamiento
    // absoluto, y calcular el objetivo desde el anclaje. Estos tests fijan ese
    // contrato verificable en JVM para que el cableado Compose (NO VERIFICADO) se
    // apoye en una fuente de verdad probada.

    @Test
    fun absoluteMonthOffsetPreservesHighDayAcrossShortMonth() {
        // 31-ene +2 meses (absoluto) → 31-mar: el mes intermedio (feb, 28 días) NO
        // degrada el día si el cálculo parte del anclaje original.
        val anchor = LocalDate.of(2026, 1, 31)

        assertEquals(
            LocalDate.of(2026, 3, 31),
            PlannerCalendar.shiftMonthPreservingDay(anchor, 2)
        )
    }

    @Test
    fun relativeClampLosesDayButAbsoluteAnchorRecoversIt() {
        // Contraste que justifica el fix de UI: el camino RELATIVO (clampear mes a
        // mes desde la fecha ya clampeada) pierde el 31; el camino ABSOLUTO lo recupera.
        val anchor = LocalDate.of(2026, 1, 31)
        val feb = PlannerCalendar.shiftMonthPreservingDay(anchor, 1)   // 28-feb (clampeado)

        // Relativo (lo que hace la UI hoy, bug): 28-feb +1 → 28-mar (día perdido)
        assertEquals(LocalDate.of(2026, 3, 28), PlannerCalendar.shiftMonthPreservingDay(feb, 1))
        // Absoluto (lo que la UI debe hacer): ancla +2 → 31-mar (recupera el 31)
        assertEquals(LocalDate.of(2026, 3, 31), PlannerCalendar.shiftMonthPreservingDay(anchor, 2))
    }

    @Test
    fun absoluteMonthOffsetPreservesEndOfMonthAcrossTwoShortMonths() {
        // 30-ene → +2 (feb 28 / mar 30) → +3 (abr 30). Y 31-mar → +1 (abr 30, clampeado).
        val anchor = LocalDate.of(2026, 1, 31)

        assertEquals(LocalDate.of(2026, 2, 28), PlannerCalendar.shiftMonthPreservingDay(anchor, 1))
        assertEquals(LocalDate.of(2026, 3, 31), PlannerCalendar.shiftMonthPreservingDay(anchor, 2))
        assertEquals(LocalDate.of(2026, 4, 30), PlannerCalendar.shiftMonthPreservingDay(anchor, 3))
        assertEquals(LocalDate.of(2026, 5, 31), PlannerCalendar.shiftMonthPreservingDay(anchor, 4))
    }

    @Test
    fun absoluteMonthOffsetBackwardPreservesHighDayAcrossShortMonth() {
        // 31-mar -2 (absoluto) → 31-ene, aunque feb intermedio tenga 28 días.
        val anchor = LocalDate.of(2026, 3, 31)

        assertEquals(LocalDate.of(2026, 1, 31), PlannerCalendar.shiftMonthPreservingDay(anchor, -2))
    }
}
