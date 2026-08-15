package com.ordia.app.domain

import com.ordia.app.data.local.RecurrenceFrequency
import com.ordia.app.data.local.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class NaturalTaskParserTest {
    private val zone = ZoneId.of("America/Santo_Domingo")
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 7, 29), LocalTime.NOON, zone)

    @Test fun parsesTomorrowTimeAndPriority() {
        val result = NaturalTaskParser.parse("Llamar a Ana mañana a las 9:30 !alta", now, zone)
        assertEquals("Llamar a Ana", result.title)
        assertEquals(TaskPriority.HIGH, result.priority)
        assertNotNull(result.dueAt)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 30), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun textWithoutCommand_remainsUnchanged() {
        val result = NaturalTaskParser.parse("Revisar informe de calidad", now, zone)
        assertEquals("Revisar informe de calidad", result.title)
        assertEquals(null, result.dueAt)
    }

    @Test fun parsesNextWeekdayInSpanish() {
        val result = NaturalTaskParser.parse("Entregar reporte el viernes a las 15:00", now, zone)
        assertEquals("Entregar reporte", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun parsesRelativeDuration() {
        val result = NaturalTaskParser.parse("Revisar el horno en 45 minutos", now, zone)
        assertEquals("Revisar el horno", result.title)
        assertEquals(now + 45 * 60_000L, result.dueAt)
        assertNull("La fecha relativa no debe leerse como duración", result.durationMinutes)
    }

    @Test fun parsesWeeklyRecurrence() {
        val result = NaturalTaskParser.parse("Preparar informe todos los viernes", now, zone)
        assertEquals("Preparar informe", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("5", result.recurrenceDays)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun parsesMultipleWeekdaysRecurrence() {
        val result = NaturalTaskParser.parse("Revisar reporte cada lunes y jueves", now, zone)
        assertEquals("Revisar reporte", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("1,4", result.recurrenceDays)
        // Desde miércoles 29-jul, la primera ocurrencia es el jueves 30-jul.
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun parsesDailyRecurrence() {
        val result = NaturalTaskParser.parse("Tomar vitaminas cada día", now, zone)
        assertEquals("Tomar vitaminas", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(1, result.recurrenceInterval)
        assertNull(result.dueAt)
    }

    @Test fun parsesIntervalRecurrence() {
        val result = NaturalTaskParser.parse("Lavar el coche cada 2 semanas", now, zone)
        assertEquals("Lavar el coche", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals(2, result.recurrenceInterval)
        assertNull(result.dueAt)
    }

    @Test fun parsesReminderOffsetBeforeDue() {
        val result = NaturalTaskParser.parse("Presentar propuesta el viernes recuérdame 2 horas antes", now, zone)
        assertEquals("Presentar propuesta", result.title)
        assertEquals(120, result.reminderOffsetMinutes)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun parsesDurationPhrase() {
        val result = NaturalTaskParser.parse("Preparar presentación durante 45 minutos", now, zone)
        assertEquals("Preparar presentación", result.title)
        assertEquals(45, result.durationMinutes)
        assertNull(result.dueAt)
    }

    @Test fun parsesStandaloneDuration() {
        val result = NaturalTaskParser.parse("Estudiar 30 minutos", now, zone)
        assertEquals("Estudiar", result.title)
        assertEquals(30, result.durationMinutes)
    }

    @Test fun parsesMonthNameDate() {
        val result = NaturalTaskParser.parse("Entregar reporte antes del 5 de agosto", now, zone)
        assertEquals("Entregar reporte", result.title)
        assertEquals(LocalDate.of(2026, 8, 5), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun monthNameDateBeforeTodayRollsToNextYear() {
        val result = NaturalTaskParser.parse("Llamar al dentista el 5 de julio", now, zone)
        assertEquals("Llamar al dentista", result.title)
        assertEquals(LocalDate.of(2027, 7, 5), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun detectsCategoryByContext() {
        val result = NaturalTaskParser.parse("Comprar leche en el supermercado", now, zone)
        assertEquals("compras", result.category)
        assertEquals(0.6f, result.confidence, 0.001f)
    }

    @Test fun plainTextWithoutSignalsHasLowConfidence() {
        val result = NaturalTaskParser.parse("Revisar la factura del teléfono", now, zone)
        assertEquals("", result.category)
        assertEquals(0.35f, result.confidence, 0.001f)
    }

    @Test fun explicitTimeHasFullConfidence() {
        val result = NaturalTaskParser.parse("Llamar a Ana a las 9:30", now, zone)
        assertEquals("Llamar a Ana", result.title)
        assertEquals(1.0f, result.confidence, 0.001f)
        assertEquals(LocalTime.of(9, 30), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun highPriorityWithoutDateStaysInboxCandidate() {
        val result = NaturalTaskParser.parse("Revisar informe de calidad", now, zone)
        assertEquals("Revisar informe de calidad", result.title)
        assertEquals(null, result.dueAt)
        assertEquals("trabajo", result.category)
        assertEquals(0.6f, result.confidence, 0.001f)
    }

    // --- Mejoras de lenguaje natural (fase 2) ---

    @Test fun timeOfDayPhraseImpliesToday() {
        val result = NaturalTaskParser.parse("Llamar a Juan esta tarde", now, zone)
        assertEquals("Llamar a Juan", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun nightPhraseUsesImplicitHour() {
        val result = NaturalTaskParser.parse("Preparar la cena esta noche", now, zone)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(20, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun hourWithDeLaTardeShiftsToAfternoon() {
        val result = NaturalTaskParser.parse("Reunión a las 3 de la tarde", now, zone)
        assertEquals("Reunión", result.title)
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun hourWithDeLaNocheShiftsToNight() {
        val result = NaturalTaskParser.parse("Llamar a Ana a las 9 de la noche", now, zone)
        assertEquals(LocalTime.of(21, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun compactHourSuffix() {
        val result = NaturalTaskParser.parse("Llamar al banco a las 15h", now, zone)
        assertEquals(LocalTime.of(15, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun weekendResolvesToNextSaturday() {
        val result = NaturalTaskParser.parse("Hacer deporte el fin de semana", now, zone)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun nextWeekResolvesToNextMonday() {
        val result = NaturalTaskParser.parse("Entregar informe la próxima semana", now, zone)
        assertEquals(LocalDate.of(2026, 8, 3), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun dayOfMonthFallsForwardWhenPast() {
        val result = NaturalTaskParser.parse("Pagar el alquiler el día 15", now, zone)
        assertEquals(LocalDate.of(2026, 8, 15), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun monthOnlyResolvesThisYear() {
        val result = NaturalTaskParser.parse("Revisar presupuesto en agosto", now, zone)
        assertEquals(LocalDate.of(2026, 8, 1), DateRules.toLocalDate(result.dueAt!!, zone))
    }

    @Test fun naturalUrgentPriority() {
        val result = NaturalTaskParser.parse("Entregar informe urgente", now, zone)
        assertEquals(TaskPriority.URGENT, result.priority)
        assertEquals("Entregar informe", result.title)
    }

    @Test fun naturalImportantPriority() {
        val result = NaturalTaskParser.parse("Preparar la presentación importante", now, zone)
        assertEquals(TaskPriority.HIGH, result.priority)
        assertEquals("Preparar la presentación", result.title)
    }

    @Test fun wordDurationMediaHora() {
        val result = NaturalTaskParser.parse("Media hora de lectura", now, zone)
        assertEquals(30, result.durationMinutes)
    }

    @Test fun wordDurationUnaHoraYMedia() {
        val result = NaturalTaskParser.parse("Estudiar una hora y media", now, zone)
        assertEquals(90, result.durationMinutes)
        assertEquals("Estudiar", result.title)
    }

    @Test fun combinedHourMinuteDurationIsNotTime() {
        val result = NaturalTaskParser.parse("Entrenar 1h30m", now, zone)
        assertEquals(90, result.durationMinutes)
        assertNull("1h30m es duración, no hora", result.dueAt)
    }

    @Test fun workdaysRecurrence() {
        val result = NaturalTaskParser.parse("Ir al gimnasio días laborables", now, zone)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("1,2,3,4,5", result.recurrenceDays)
    }

    @Test fun weekdayRangeRecurrence() {
        val result = NaturalTaskParser.parse("Revisar correo de lunes a viernes", now, zone)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        assertEquals("1,2,3,4,5", result.recurrenceDays)
    }

    @Test fun relativeWordDurationEnUnaHora() {
        val result = NaturalTaskParser.parse("Llamar a María en una hora", now, zone)
        assertEquals(now + 60 * 60_000L, result.dueAt)
        assertEquals("Llamar a María", result.title)
    }
}
