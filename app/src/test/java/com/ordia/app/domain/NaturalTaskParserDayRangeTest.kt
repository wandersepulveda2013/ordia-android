package com.ordia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.ordia.app.data.local.RecurrenceFrequency
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

    // c.380: "hasta" como conector de cierre de rango, simétrico a "al"/"a".
    // Antes "del 15 hasta el 20 de diciembre" no casaba y dejaba "hasta" como residuo
    // en el título (contenido mutilado, P1 captura/datos).

    @Test fun rangoHastaAnclaAlCierreYlimpiaTitulo() {
        val r = parse("vacaciones del 15 hasta el 20 de diciembre")
        assertEquals("vacaciones", r.title.trim())
        assertEquals(LocalDate.of(2026, 12, 20), r.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() })
    }

    @Test fun rangoHastaConAnioExplicito() {
        val r = parse("viaje del 3 hasta el 8 de enero del 2027")
        assertEquals("viaje", r.title.trim())
        assertEquals(LocalDate.of(2027, 1, 8), r.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() })
    }

    @Test fun rangoHastaNoDejaResiduoConector() {
        val title = parse("curso del 10 hasta el 14 de marzo").title
        assertFalse("el título no debe contener residuo 'hasta'", title.contains("hasta"))
        assertFalse("el título no debe contener el día inicial 10", title.contains("10"))
    }

    // Rangos que cruzan de mes (o de año): "del 28 de febrero al 1 de marzo".
    // Antes el [dayRangePattern] sólo casaba la forma "del N al M de <mesÚnico>"
    // (un solo mes al final); al llevar cada extremo su propio mes, no casaba, cada
    // fecha caía separada y el vencimiento se anclaba al día INICIAL (28 de febrero)
    // en vez del CIERRE (1 de marzo), perdiendo la fecha real del compromiso y
    // dejando "del al" como residuo del título. Se ancla al CIERRE (P1 captura/datos).

    @Test fun rangoCruzandoMesAnclaAlCierre() {
        val r = parse("feria del 28 de febrero al 1 de marzo")
        assertEquals("feria", r.title.trim())
        // 1 de marzo de 2026 ya pasó (now=2026-08-17) -> rueda al año siguiente.
        assertEquals(LocalDate.of(2027, 3, 1), dueDate("feria del 28 de febrero al 1 de marzo"))
    }

    @Test fun rangoCruzandoMesNoDejaResiduoDelAl() {
        val title = parse("feria del 28 de febrero al 1 de marzo").title
        assertFalse("el título no debe contener residuo 'al'", title.contains(" al"))
        assertFalse("el título no debe contener el día inicial 28", title.contains("28"))
    }

    @Test fun rangoCruzandoAnioAnclaAlCierre() {
        val r = parse("conferencia del 31 de diciembre al 2 de enero")
        assertEquals("conferencia", r.title.trim())
        assertEquals(LocalDate.of(2027, 1, 2), dueDate("conferencia del 31 de diciembre al 2 de enero"))
    }

    @Test fun rangoCruzandoMesRespetaHoraExplicita() {
        val r = parse("trabajo del 31 de enero al 1 de febrero a las 9")
        assertEquals("trabajo", r.title.trim())
        val dt = r.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone) }
        assertNotNull("debe producir vencimiento", dt)
        // 1 de febrero ya pasó este año (now=2026-08-17) -> rueda al año siguiente.
        assertEquals(LocalDate.of(2027, 2, 1), dt!!.toLocalDate())
        assertEquals(9, dt.hour)
    }

    @Test fun rangoCruzandoMesConAnioExplicito() {
        val r = parse("viaje del 28 de diciembre del 2026 al 3 de enero del 2027")
        assertEquals("viaje", r.title.trim())
        assertEquals(LocalDate.of(2027, 1, 3), r.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() })
    }

    @Test fun rangoWeekdayHastaAnclaAlCierreYlimpiaTitulo() {
        // "del lunes hasta el viernes": evento único anclado al cierre (viernes).
        val r = parse("reunión del lunes hasta el viernes")
        assertEquals("reunión", r.title.trim())
        val dt = r.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone) }
        assertNotNull("debe producir vencimiento", dt)
        assertEquals(LocalDate.of(2026, 8, 21), dt!!.toLocalDate())
    }

    @Test fun rangoWeekdayHastaSinArticulo() {
        // "del lunes hasta viernes" (sin "el"): misma resolución.
        val r = parse("taller del martes hasta jueves")
        assertEquals("taller", r.title.trim())
        val dt = r.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone) }
        assertNotNull("debe producir vencimiento", dt)
        assertEquals(LocalDate.of(2026, 8, 20), dt!!.toLocalDate())
    }

    @Test fun semanaLaboralHastaEsRecurrente() {
        // "de lunes hasta viernes": semana laboral recurrente Lun-Vie, simétrico a
        // "de lunes a viernes" (no evento único). c.380.
        val r = parse("gym de lunes hasta viernes")
        assertEquals("gym", r.title.trim())
        assertEquals(RecurrenceFrequency.WEEKLY, r.recurrence)
        assertEquals("1,2,3,4,5", r.recurrenceDays)
    }

    @Test fun hastaSueltoNoEsRango() {
        // "hasta el viernes" suelto (fecha límite) NO debe casar como rango: exige
        // weekday a ambos lados. Regresión de seguridad: el conector de plazo sigue
        // funcionando y no se falsifica un rango sin día inicial.
        val r = parse("entregar hasta el viernes")
        assertFalse("no debe dejar residuo 'hasta'", r.title.contains("hasta"))
    }
}
