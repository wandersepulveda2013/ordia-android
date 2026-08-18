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

    // Hábito MENSUAL agendado el día 31: meses cortos (feb 28/29, abr, jun, sep,
    // nov) NO tienen día 31, así que ningún día de esos meses es agendado. La
    // racha debe CONTINUAR a través del mes corto (saltándolo), no romperse:
    // completar el 31-ene y el 31-mar ⇒ racha 2, aunque feb no tenga 31. El bucle
    // día a día de [currentStreak] ya lo hace bien (isScheduled=false para todo
    // feb ⇒ retrocede sin penalizar), pero NO había test que lo fijara: un
    // "arreglo" futuro que sustituyera el salto día a día por saltos por mes
    // podía romper esta racha silenciosamente. P1: integridad de la racha.
    @Test fun streak_monthly31st_continuesAcrossShortMonth() {
        val habit = HabitEntity(id = 3, title = "Cerrar balance", frequency = HabitFrequency.MONTHLY, activeDays = "31")
        // Feb 2026 no tiene 31: ningún día de feb es agendado.
        assertFalse(HabitRules.isScheduled(habit, LocalDate.of(2026, 2, 28)))
        val today = LocalDate.of(2026, 3, 31) // mar 31, agendado y completado
        val logs = listOf(
            HabitLogEntity(3, LocalDate.of(2026, 1, 31).toEpochDay()), // ene 31 (completado)
            HabitLogEntity(3, LocalDate.of(2026, 3, 31).toEpochDay())  // mar 31 (completado)
        )
        assertEquals(2, HabitRules.currentStreak(habit, logs, today))
    }

    @Test fun isCompleted_trueWhenCountMeetsTarget() {
        assertTrue(HabitRules.isCompleted(3, 3))
        assertTrue(HabitRules.isCompleted(5, 3))
    }

    @Test fun isCompleted_falseBelowTarget() {
        assertFalse(HabitRules.isCompleted(2, 3))
        assertFalse(HabitRules.isCompleted(0, 1))
    }

    @Test fun isCompleted_treatsZeroOrNegativeTargetAsOne() {
        // Un habito sin meta (target 0) no debe ser "siempre cumplado":
        // coherente con el default de Room (targetPerPeriod = 1).
        assertFalse(HabitRules.isCompleted(0, 0))
        assertTrue(HabitRules.isCompleted(1, 0))
    }

    @Test fun nextToggleCount_incrementsBelowTarget() {
        // 0 -> 1, 1 -> 2, 2 -> 3 (aun no cumplido: suma uno)
        assertEquals(1, HabitRules.nextToggleCount(0, 3))
        assertEquals(2, HabitRules.nextToggleCount(1, 3))
        assertEquals(3, HabitRules.nextToggleCount(2, 3))
    }

    @Test fun nextToggleCount_decrementsWhenAtOrAboveTargetInsteadOfResetToZero() {
        // Bug PRE-fix: al desmarcar con target > 1 se hacia removeLog -> 0,
        // perdiendo TODO el progreso (8 vasos -> 0). Ahora resta un registro.
        assertEquals(7, HabitRules.nextToggleCount(8, 8))
        assertEquals(7, HabitRules.nextToggleCount(8, 3)) // por encima de la meta tambien resta
    }

    @Test fun nextToggleCount_targetOneDecrementsToZeroLikeRemoveLog() {
        // Para target = 1, la resta equivale a 0 (mismo efecto que borrar el
        // registro del dia): sin regresion respecto al comportamiento anterior.
        assertEquals(0, HabitRules.nextToggleCount(1, 1))
    }

    @Test fun nextToggleCount_staysConsistentWithIsCompleted() {
        // Tras desmarcar desde "cumplido", el conteo ya no cumple la meta: no hay
        // inconsistencia con currentStreak (que usa count >= target).
        val target = 5
        val afterUnmark = HabitRules.nextToggleCount(target, target)
        assertFalse(HabitRules.isCompleted(afterUnmark, target))
        // Y al registrar de vuelta, vuelve a cumplirse.
        val afterRemark = HabitRules.nextToggleCount(afterUnmark, target)
        assertTrue(HabitRules.isCompleted(afterRemark, target))
    }
}
