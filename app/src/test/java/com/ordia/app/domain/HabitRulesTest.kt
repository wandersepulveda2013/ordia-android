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
}
