package com.ordia.app.domain

import com.ordia.app.data.local.RecurrenceFrequency
import com.ordia.app.data.local.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class RecurrenceEngineTest {
    private val zone = ZoneId.of("America/Santo_Domingo")

    @Test fun none_hasNoNextOccurrence() {
        assertNull(RecurrenceEngine.nextOccurrence(TaskEntity(title = "Una vez"), zone = zone))
    }

    @Test fun daily_preservesTimeAndReminderOffset() {
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 7, 29), LocalTime.of(9, 30), zone)
        val task = TaskEntity(title = "Diaria", dueAt = due, reminderAt = due - 30 * 60_000L, recurrence = RecurrenceFrequency.DAILY)
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = due, zone = zone))
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(next.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 30), DateRules.toLocalTime(next.dueAt, zone))
        assertEquals(30 * 60_000L, next.dueAt - next.reminderAt!!)
        assertFalse(next.completed)
    }

    // "cada otro día" / "un día sí y otro no" (DAILY interval=2): al completar avanza
    // exactamente 2 días, no 1. Valida que el intervalo desde el parser se respeta en
    // el motor (medicación cada-dos-días no se vuelve diaria al completar).
    @Test fun dailyInterval2_advancesTwoDays() {
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 7, 29), LocalTime.of(9, 30), zone)
        val task = TaskEntity(title = "Pastilla cada otro día", dueAt = due, recurrence = RecurrenceFrequency.DAILY, recurrenceInterval = 2)
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = due, zone = zone))
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(next.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 30), DateRules.toLocalTime(next.dueAt, zone))
    }

    @Test fun weekly_usesSelectedWeekdays() {
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 7, 29), LocalTime.NOON, zone) // miércoles
        val task = TaskEntity(title = "Lunes y viernes", dueAt = due, recurrence = RecurrenceFrequency.WEEKLY, recurrenceDays = "1,5")
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = due, zone = zone))
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(next.dueAt!!, zone))
    }

    @Test fun monthly_anchorsToDayOfMonthAndSkipsMonthsLackingIt() {
        // "el 31 de cada mes": ene 31 + 1 mes NO debe dar feb 28 (clamp),
        // sino saltar a mar 31 (feb no tiene 31). Coincide con el anclaje del parser.
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 1, 31), LocalTime.of(8, 0), zone)
        val task = TaskEntity(title = "Mensual 31", dueAt = due, recurrence = RecurrenceFrequency.MONTHLY)
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = due, zone = zone))
        assertEquals(LocalDate.of(2026, 3, 31), DateRules.toLocalDate(next.dueAt!!, zone))
        assertEquals(LocalTime.of(8, 0), DateRules.toLocalTime(next.dueAt, zone))
    }

    @Test fun monthly_preservesDayForCommonDays() {
        // Dias 1-28: comportamiento estable, sin deriva (caso mas comun: "el 15 de cada mes").
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 1, 15), LocalTime.of(9, 0), zone)
        val task = TaskEntity(title = "Renta", dueAt = due, recurrence = RecurrenceFrequency.MONTHLY)
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = due, zone = zone))
        assertEquals(LocalDate.of(2026, 2, 15), DateRules.toLocalDate(next.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(next.dueAt, zone))
    }

    @Test fun monthly_advancesPastCompletedAt() {
        // Si se completa despues del vencimiento, no regresa al pasado.
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 1, 31), LocalTime.NOON, zone)
        val task = TaskEntity(title = "Mensual 31", dueAt = due, recurrence = RecurrenceFrequency.MONTHLY)
        val late = DateRules.toEpochMillis(LocalDate.of(2026, 3, 1), LocalTime.NOON, zone)
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = late, zone = zone))
        assertEquals(LocalDate.of(2026, 3, 31), DateRules.toLocalDate(next.dueAt!!, zone))
    }

    @Test fun yearly_leapDayAnchorSkipsNonLeapYears() {
        // "aniversario el 29 de febrero de cada año": 29/2/2024 +1 año NO debe dar
        // 28/2/2025 (clamp que deriva el ancla para siempre), sino saltar al próximo
        // 29 de febrero real (2028). Simétrico al mensual 31 de ciclo 18.
        val due = DateRules.toEpochMillis(LocalDate.of(2024, 2, 29), LocalTime.of(12, 0), zone)
        val task = TaskEntity(title = "Aniversario bisiesto", dueAt = due, reminderAt = due - 60 * 60_000L, recurrence = RecurrenceFrequency.YEARLY)
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = due, zone = zone))
        assertEquals(LocalDate.of(2028, 2, 29), DateRules.toLocalDate(next.dueAt!!, zone))
        assertEquals(LocalTime.of(12, 0), DateRules.toLocalTime(next.dueAt, zone))
        assertEquals(60 * 60_000L, next.dueAt - next.reminderAt!!)
    }

    @Test fun yearly_leapDayAnchorDoesNotDriftAcrossCycles() {
        // Tras completar la ocurrencia de 2028, la siguiente vuelve a ser un 29 de
        // febrero (2032), no 28/2: el ancla NO deriva. Confirma la no-regresión del
        // comportamiento de clamp anterior (que habría dado 28/2/2029).
        val due = DateRules.toEpochMillis(LocalDate.of(2028, 2, 29), LocalTime.of(12, 0), zone)
        val task = TaskEntity(title = "Aniversario bisiesto", dueAt = due, recurrence = RecurrenceFrequency.YEARLY)
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = due, zone = zone))
        assertEquals(LocalDate.of(2032, 2, 29), DateRules.toLocalDate(next.dueAt!!, zone))
    }

    @Test fun yearly_nonLeapDayAnchorUsesPlainPlusYears() {
        // Cualquier fecha común (no 29/2) es estable con +1 año: sin cambio de
        // comportamiento. Regresión de que la nueva rama no altera el caso normal.
        val due = DateRules.toEpochMillis(LocalDate.of(2026, 8, 15), LocalTime.of(9, 0), zone)
        val task = TaskEntity(title = "Cumple", dueAt = due, recurrence = RecurrenceFrequency.YEARLY)
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = due, zone = zone))
        assertEquals(LocalDate.of(2027, 8, 15), DateRules.toLocalDate(next.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(next.dueAt, zone))
    }

    @Test fun yearly_leapDayAnchorRespectsInterval() {
        // "cada 4 años" anclado a 29/2: el paso mínimo se respeta; como 29/2/2024 + 4
        // = 29/2/2028 (bisiesto), no necesita saltar más. Valida que interval > 1 no
        // se ignora.
        val due = DateRules.toEpochMillis(LocalDate.of(2024, 2, 29), LocalTime.of(12, 0), zone)
        val task = TaskEntity(title = "Olimpiadas", dueAt = due, recurrence = RecurrenceFrequency.YEARLY, recurrenceInterval = 4)
        val next = requireNotNull(RecurrenceEngine.nextOccurrence(task, completedAt = due, zone = zone))
        assertEquals(LocalDate.of(2028, 2, 29), DateRules.toLocalDate(next.dueAt!!, zone))
    }
}
