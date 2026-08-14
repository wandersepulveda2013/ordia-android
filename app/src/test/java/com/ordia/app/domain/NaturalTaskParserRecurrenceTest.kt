package com.ordia.app.domain

import com.ordia.app.data.local.RecurrenceFrequency
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class NaturalTaskParserRecurrenceTest {
    private val zone = ZoneId.of("America/Santo_Domingo")
    private val now = 1690000000000 // Just a fixed timestamp

    @Test
    fun parse_everyDay() {
        val input = "Llamar mamá todos los días"
        val parsed = NaturalTaskParser.parse(input, now, zone)
        assertEquals("Llamar mamá", parsed.title)
        assertEquals(RecurrenceFrequency.DAILY, parsed.recurrence)
    }

    @Test
    fun parse_everyWeek() {
        val input = "Comprar pan cada semana !alta"
        val parsed = NaturalTaskParser.parse(input, now, zone)
        assertEquals("Comprar pan", parsed.title)
        assertEquals(RecurrenceFrequency.WEEKLY, parsed.recurrence)
    }

    @Test
    fun parse_everyMonday() {
        val input = "Revisar notas todos los lunes"
        val parsed = NaturalTaskParser.parse(input, now, zone)
        assertEquals("Revisar notas", parsed.title)
        assertEquals(RecurrenceFrequency.WEEKLY, parsed.recurrence)
        assertEquals("1", parsed.recurrenceDays) // Monday is 1
    }
}
