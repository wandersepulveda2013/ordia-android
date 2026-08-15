package com.ordia.app.domain

import com.ordia.app.data.local.HabitEntity
import com.ordia.app.data.local.HabitFrequency
import com.ordia.app.data.local.HabitLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HabitRulesTest {
    @Test fun weeklyHabit_onlyRunsOnSelectedDays() {
        val habit = HabitEntity(id = 7, title = "Caminar", frequency = HabitFrequency.WEEKLY, activeDays = "1,3,5")
        assertTrue(HabitRules.isScheduled(habit, LocalDate.of(2026, 7, 29))) // miércoles
        assertFalse(HabitRules.isScheduled(habit, LocalDate.of(2026, 7, 30)))
    }

    @Test fun streak_skipsDaysThatAreNotScheduled() {
        val habit = HabitEntity(id = 7, title = "Caminar", frequency = HabitFrequency.WEEKLY, activeDays = "1,3,5")
        val today = LocalDate.of(2026, 7, 29)
        val logs = listOf(
            HabitLogEntity(7, today.toEpochDay()),
            HabitLogEntity(7, LocalDate.of(2026, 7, 27).toEpochDay()),
            HabitLogEntity(7, LocalDate.of(2026, 7, 24).toEpochDay())
        )
        assertEquals(3, HabitRules.currentStreak(habit, logs, today))
    }

    @Test fun streak_countsLongDailyStreakBeyondGuard() {
        val habit = HabitEntity(id = 1, title = "Meditar", frequency = HabitFrequency.DAILY, activeDays = "")
        val today = LocalDate.of(2026, 7, 29)
        val span = 800
        val logs = (0 until span).map { i ->
            HabitLogEntity(1, today.minusDays(i.toLong()).toEpochDay())
        }
        assertEquals(span, HabitRules.currentStreak(habit, logs, today))
    }

    @Test fun streak_countsLongMonthlyStreakBeyondDayByDayGuard() {
        // Habito mensual el dia 1: cada unidad de racha exige ~30 saltos diarios
        // hacia atras, asi que un guard de 730 iteraciones truncaba ~23 meses.
        val habit = HabitEntity(id = 2, title = "Revisar presupuesto", frequency = HabitFrequency.MONTHLY, activeDays = "1")
        val today = LocalDate.of(2026, 7, 1)
        val months = 30
        val logs = (0 until months).map { i ->
            val d = today.minusMonths(i.toLong())
            HabitLogEntity(2, LocalDate.of(d.year, d.month, 1).toEpochDay())
        }
        assertEquals(months, HabitRules.currentStreak(habit, logs, today))
    }
}
