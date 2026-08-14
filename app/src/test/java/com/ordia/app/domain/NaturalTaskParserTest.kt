package com.ordia.app.domain

import com.ordia.app.data.local.RecurrenceFrequency
import com.ordia.app.data.local.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    }

    @Test fun parsesRecurrenceDaily() {
        val result = NaturalTaskParser.parse("Correr 5km todos los días a las 6 am", now, zone)
        assertEquals("Correr 5km", result.title)
        assertEquals(RecurrenceFrequency.DAILY, result.recurrence)
        assertEquals(LocalTime.of(6, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun parsesRecurrenceWeeklyAndNextWeek() {
        val result = NaturalTaskParser.parse("Reunión de equipo próxima semana a las 10 am cada semana", now, zone)
        assertEquals("Reunión de equipo", result.title)
        assertEquals(RecurrenceFrequency.WEEKLY, result.recurrence)
        val expectedDate = LocalDate.of(2026, 7, 29).plusDays(7)
        assertEquals(expectedDate, DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(expectedDate.dayOfWeek.value.toString(), result.recurrenceDays)
    }

    @Test fun parsesTonightRelativeTime() {
        val result = NaturalTaskParser.parse("Leer un libro esta noche", now, zone)
        assertEquals("Leer un libro", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(20, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }
}
