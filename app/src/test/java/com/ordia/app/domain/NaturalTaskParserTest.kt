package com.ordia.app.domain

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

    @Test fun parsesColloquialExpressions() {
        val result1 = NaturalTaskParser.parse("Cenar esta noche", now, zone)
        assertEquals(LocalTime.of(20, 0), DateRules.toLocalTime(result1.dueAt!!, zone))

        val result2 = NaturalTaskParser.parse("Ir al gimnasio después del trabajo", now, zone)
        assertEquals(LocalTime.of(18, 0), DateRules.toLocalTime(result2.dueAt!!, zone))

        val result3 = NaturalTaskParser.parse("Llamar al médico al mediodía", now, zone)
        assertEquals(LocalTime.of(12, 0), DateRules.toLocalTime(result3.dueAt!!, zone))
    }

    @Test fun parsesSpecialRelativeDurations() {
        val result1 = NaturalTaskParser.parse("Descansar en media hora", now, zone)
        assertEquals(now + 30 * 60_000L, result1.dueAt)

        val result2 = NaturalTaskParser.parse("Volver en una hora y media", now, zone)
        assertEquals(now + 90 * 60_000L, result2.dueAt)
    }

    @Test fun parsesHastaElViernes() {
        val result = NaturalTaskParser.parse("Terminar reporte hasta el viernes", now, zone)
        assertEquals("Terminar reporte", result.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(result.dueAt!!, zone))
    }
}
