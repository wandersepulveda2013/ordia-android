package com.ordia.app.domain

import com.ordia.app.data.local.HabitEntity
import com.ordia.app.data.local.HabitFrequency
import com.ordia.app.data.local.HabitLogEntity
import java.time.LocalDate

object HabitRules {
    /**
     * Limite defensivo de iteraciones de [currentStreak]. El bucle termina solo
     * al encontrar el primer dia programado SIN completar (los `logs` son
     * finitos, asi que retrocediendo siempre se llega a uno), por lo que este
     * tope solo actua como red contra bucles infinitos hipoteticos.
     *
     * Antes era 730 (~2 anos de pasos DIARIOS), lo que truncaba rachas reales:
     * cada dia no programado consume una iteracion, asi que un habito mensual
     * (un dia programado cada ~30) se truncaba a ~23 meses y un habito diario
     * a ~730 dias. 100_000 cubre ~273 anos de pasos diarios (o ~130 anos de un
     * habito mensual en un dia raro como el 31), muy por encima de cualquier
     * racha humana posible, sin coste apreciable (cada paso es O(1)).
     */
    private const val MAX_STREAK_ITERATIONS = 100_000

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
        while (guard++ < MAX_STREAK_ITERATIONS) {
            if (!isScheduled(habit, date)) { date = date.minusDays(1); continue }
            if (date.toEpochDay() in completed) { streak++; date = date.minusDays(1) } else break
        }
        return streak
    }
}
