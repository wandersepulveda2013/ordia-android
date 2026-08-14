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

    @Test fun parsesEstaNoche() {
        val result = NaturalTaskParser.parse("Comprar pan esta noche", now, zone)
        assertEquals("Comprar pan", result.title)
        assertNotNull(result.dueAt)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(20, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun parsesMediodia() {
        val result = NaturalTaskParser.parse("Reunión al mediodía", now, zone)
        assertEquals("Reunión", result.title)
        assertNotNull(result.dueAt)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(12, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun parsesMedianoche() {
        val result = NaturalTaskParser.parse("Lanzamiento a medianoche", now, zone)
        assertEquals("Lanzamiento", result.title)
        assertNotNull(result.dueAt)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(0, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun parsesDentroDe() {
        val result = NaturalTaskParser.parse("Llamar dentro de 15 minutos", now, zone)
        assertEquals("Llamar", result.title)
        assertEquals(now + 15 * 60_000L, result.dueAt)
    }

    @Test fun parsesTodosLosLunes() {
        val result = NaturalTaskParser.parse("Correr todos los lunes", now, zone)
        assertEquals("Correr", result.title)
        assertNotNull(result.dueAt)
        val date = DateRules.toLocalDate(result.dueAt!!, zone)
        assertEquals(LocalDate.of(2026, 8, 3), date)
    }

    @Test fun parsesCadaMartes() {
        val result = NaturalTaskParser.parse("Revisión de métricas cada martes", now, zone)
        assertEquals("Revisión de métricas", result.title)
        assertNotNull(result.dueAt)
        val date = DateRules.toLocalDate(result.dueAt!!, zone)
        assertEquals(LocalDate.of(2026, 8, 4), date)
    }

    @Test fun parsesParaElLunes() {
        val result = NaturalTaskParser.parse("Entregar informe para el lunes", now, zone)
        assertEquals("Entregar informe", result.title)
        assertNotNull(result.dueAt)
        val date = DateRules.toLocalDate(result.dueAt!!, zone)
        assertEquals(LocalDate.of(2026, 8, 3), date)
    }
}
