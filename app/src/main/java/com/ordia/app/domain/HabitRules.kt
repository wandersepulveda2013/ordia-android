package com.ordia.app.domain

import com.ordia.app.data.local.HabitEntity
import com.ordia.app.data.local.HabitFrequency
import com.ordia.app.data.local.HabitLogEntity
import java.time.LocalDate

object HabitRules {
    fun isScheduled(habit: HabitEntity, date: LocalDate): Boolean {
        val days = habit.activeDays.split(',').mapNotNull { it.trim().toIntOrNull() }
        return when (habit.frequency) {
            HabitFrequency.DAILY, HabitFrequency.WEEKLY -> days.isEmpty() || date.dayOfWeek.value in days
            HabitFrequency.MONTHLY -> days.isEmpty() || date.dayOfMonth in days
        }
    }
    fun countFor(logs: List<HabitLogEntity>, habitId: Long, date: LocalDate): Int =
        logs.firstOrNull { it.habitId == habitId && it.epochDay == date.toEpochDay() }?.count ?: 0

    fun currentStreak(habit: HabitEntity, logs: List<HabitLogEntity>, today: LocalDate = LocalDate.now()): Int {
        val completed = logs.filter { it.habitId == habit.id && it.count >= habit.targetPerPeriod }.map { it.epochDay }.toSet()
        var date = if (isScheduled(habit, today) && today.toEpochDay() !in completed) today.minusDays(1) else today
        var streak = 0
        var guard = 0
        while (guard++ < 730) {
            if (!isScheduled(habit, date)) { date = date.minusDays(1); continue }
            if (date.toEpochDay() in completed) { streak++; date = date.minusDays(1) } else break
        }
        return streak
    }
}
