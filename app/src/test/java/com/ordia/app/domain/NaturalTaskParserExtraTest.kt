package com.ordia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class NaturalTaskParserExtraTest {
    private val zone = ZoneId.of("America/Santo_Domingo")
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 7, 29), LocalTime.NOON, zone)

    @Test fun parsesEstaNoche() {
        val result = NaturalTaskParser.parse("Estudiar esta noche", now, zone)
        assertNotNull(result.dueAt)
        assertEquals("Estudiar", result.title)
        assertEquals(LocalDate.of(2026, 7, 29), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(20, 0), DateRules.toLocalTime(result.dueAt, zone))
    }

    @Test fun parsesMediodia() {
        val result = NaturalTaskParser.parse("Comer al mediodía", now, zone)
        assertNotNull(result.dueAt)
        assertEquals("Comer", result.title)
        assertEquals(LocalTime.of(12, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }

    @Test fun parsesPrimeraHora() {
        val result = NaturalTaskParser.parse("Revisar correos mañana a primera hora", now, zone)
        assertNotNull(result.dueAt)
        assertEquals("Revisar correos", result.title)
        assertEquals(LocalDate.of(2026, 7, 30), DateRules.toLocalDate(result.dueAt!!, zone))
        assertEquals(LocalTime.of(8, 0), DateRules.toLocalTime(result.dueAt!!, zone))
    }
}
