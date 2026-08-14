package com.ordia.app.domain

import com.ordia.app.data.local.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class NaturalTaskParserTest {
    private val zone = ZoneId.of("UTC")
    // Base time: Monday, 2026-08-10 10:00:00 UTC
    private val baseTime = 1786356000000L

    @Test
    fun parse_noDate() {
        val r = NaturalTaskParser.parse("Comprar pan", baseTime, zone)
        assertEquals("Comprar pan", r.title)
        assertEquals(null, r.dueAt)
        assertEquals(TaskPriority.NORMAL, r.priority)
    }

    @Test
    fun parse_relative_minutes() {
        val r = NaturalTaskParser.parse("Llamar en 15 mins", baseTime, zone)
        assertEquals("Llamar", r.title)
        assertEquals(baseTime + 15 * 60_000L, r.dueAt)
    }

    @Test
    fun parse_relative_hours() {
        val r = NaturalTaskParser.parse("Revisar dentro de 3 horas", baseTime, zone)
        assertEquals("Revisar", r.title)
        assertEquals(baseTime + 3 * 3600_000L, r.dueAt)
    }

    @Test
    fun parse_conceptual_tonight() {
        val r = NaturalTaskParser.parse("Estudiar esta noche", baseTime, zone)
        assertEquals("Estudiar", r.title)
        val expected = LocalDate.of(2026, 8, 10).atTime(20, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, r.dueAt)
    }

    @Test
    fun parse_conceptual_after_lunch() {
        val r = NaturalTaskParser.parse("Llamar a mamá después de comer", baseTime, zone)
        assertEquals("Llamar a mamá", r.title)
        val expected = LocalDate.of(2026, 8, 10).atTime(15, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, r.dueAt)
    }

    @Test
    fun parse_conceptual_after_lunch_past() {
        // Base time: Monday, 2026-08-10 16:00:00 UTC
        val lateBaseTime = 1786377600000L
        val r = NaturalTaskParser.parse("Llamar a mamá después de comer", lateBaseTime, zone)
        assertEquals("Llamar a mamá", r.title)
        val expected = LocalDate.of(2026, 8, 11).atTime(15, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, r.dueAt)
    }

    @Test
    fun parse_priority() {
        val r = NaturalTaskParser.parse("Terminar reporte !urgente", baseTime, zone)
        assertEquals("Terminar reporte", r.title)
        assertEquals(TaskPriority.URGENT, r.priority)
    }
}
