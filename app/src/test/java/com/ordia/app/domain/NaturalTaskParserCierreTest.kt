package com.ordia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class NaturalTaskParserCierreTest {
    private val now = ZonedDateTime.of(2026, 7, 15, 10, 0, 0, 0, ZoneId.of("UTC"))
    private val nowMs = now.toInstant().toEpochMilli()
    private val zone = ZoneId.of("UTC")

    private fun dueDate(text: String): LocalDate? =
        NaturalTaskParser.parse(text, nowMs, zone).dueAt
            ?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

    private val endOfJuly = LocalDate.of(2026, 7, 31)

    @Test fun cierreDeMesAnclaFinDeMesActual() {
        val due = dueDate("pago cierre de mes")
        assertNotNull("cierre de mes debe producir vencimiento (no null)", due)
        assertEquals(endOfJuly, due)
    }

    @Test fun cierreDelMesAnclaFinDeMesActual() {
        val due = dueDate("renta cierre del mes")
        assertNotNull("cierre del mes debe producir vencimiento (no null)", due)
        assertEquals(endOfJuly, due)
    }

    @Test fun cierreDelMesQueVieneAnclaFinMesSiguiente() {
        // El modificador "que viene" desplaza al mes siguiente, no al actual.
        val due = dueDate("factura cierre del mes que viene")
        assertNotNull(due)
        assertEquals(LocalDate.of(2026, 8, 31), due)
    }

    @Test fun cierreDeMesRespetaHoraExplicita() {
        val due = dueDate("pago cierre de mes a las 18")
        assertNotNull(due)
        assertEquals(endOfJuly, due)
    }
}

