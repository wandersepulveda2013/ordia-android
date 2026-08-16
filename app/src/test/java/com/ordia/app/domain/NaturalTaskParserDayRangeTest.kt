package com.ordia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Regresión del rango de días multi-evento ("del N al M de mes"):
 * vacaciones, viajes, cursos y ferias. Antes [NaturalTaskParser] capturaba
 * sólo el día final y dejaba "del N al" como residuo pegado al título
 * (contenido mutilado, P1 captura/datos); o, sin día final reconocible,
 * caía a `dueAt=null` con título basura. Se normaliza al CIERRE del rango.
 */
class NaturalTaskParserDayRangeTest {
    private val now = ZonedDateTime.of(2026, 8, 17, 12, 0, 0, 0, ZoneId.of("UTC"))
    private val nowMs = now.toInstant().toEpochMilli()
    private val zone = ZoneId.of("UTC")

    private fun parse(text: String) = NaturalTaskParser.parse(text, nowMs, zone)
    private fun dueDate(text: String): LocalDate? =
        parse(text).dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

    @Test fun rangoAnclaAlCierreYlimpiaTitulo() {
        val r = parse("vacaciones del 15 al 20 de diciembre")
        assertEquals("vacaciones", r.title.trim())
        assertEquals(LocalDate.of(2026, 12, 20), dueDate("vacaciones del 15 al 20 de diciembre"))
    }

    @Test fun rangoConAnioExplicito() {
        val r = parse("viaje del 3 al 8 de enero del 2027")
        assertEquals("viaje", r.title.trim())
        assertEquals(LocalDate.of(2027, 1, 8), r.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() })
    }

    @Test fun rangoRespetaHoraExplicita() {
        val r = parse("trabajo del 2 al 4 de septiembre a las 9")
        assertEquals("trabajo", r.title.trim())
        val dt = r.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone) }
        assertNotNull("debe producir vencimiento", dt)
        assertEquals(LocalDate.of(2026, 9, 4), dt!!.toLocalDate())
        assertEquals(9, dt.hour)
    }

    @Test fun rangoNoDejaResiduoDelAl() {
        val title = parse("curso del 10 al 14 de marzo").title
        assertFalse("el título no debe contener residuo 'al'", title.contains("al"))
        assertFalse("el título no debe contener el día inicial 10", title.contains("10"))
    }

    @Test fun rangoSinMesNoInventaFecha() {
        // "congreso del 20 al 25" sin mes: no se agenda una fecha inventada (honesto),
        // coherente con no falsar compromisos sin ancla de mes real.
        val r = parse("congreso del 20 al 25")
        assertNull("sin mes explícito no se debe inventar vencimiento", r.dueAt)
        assertEquals("congreso del 20 al 25", r.title.trim())
    }

    @Test fun rangoPreservaContenidoDespuesDeLaFecha() {
        val r = parse("feria del 1 al 5 de octubre en madrid")
        assertTrue("el contenido 'madrid' debe sobrevivir", r.title.contains("madrid", ignoreCase = true))
    }

    @Test fun rangoConCualificadorRelativoMesQueViene() {
        val r = parse("feria del 1 al 5 del mes que viene")
        assertEquals("feria", r.title.trim())
        assertEquals(LocalDate.of(2026, 9, 5), dueDate("feria del 1 al 5 del mes que viene"))
    }

    @Test fun rangoConCualificadorRelativoProximoMes() {
        val r = parse("taller del 10 al 12 del próximo mes")
        assertEquals("taller", r.title.trim())
        assertEquals(LocalDate.of(2026, 9, 12), r.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() })
    }
}
