package com.ordia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * "el lunes 24" / "el martes 25": día de la semana SEGUIDO de un número de día del mes
 * suelto, SIN nombre de mes. Antes weekdayPattern capturaba "el lunes" y el "24" sobrevivía
 * como residuo del título ("reunión 24" — contenido capturado degradado, P1) y, cuando el
 * próximo lunes no caía en ese día, la fecha se anclaba al weekday ignorando el número
 * explícito (cita en día erróneo, P1 datos/fechas). El número explícito ancla al día N del
 * mes. Casos de no-regresión: formas CON mes ("de septiembre"/"de cada mes"/"del mes que
 * viene") y numéricas ("/","-") las resuelven sus patrones propios; contenido sin weekday
 * ("reunión 24") no se toca.
 */
class NaturalTaskParserWeekdayDayTest {
    private val zone = ZoneId.of("America/Santiago")
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 17), java.time.LocalTime.NOON, zone)

    @Test fun weekdayDiaAnclaDiaDelMesYLimpiaTitulo() {
        // "el lunes 24" (hoy 17/8, próximo lunes 24): ancla al 24, título limpio.
        val r = NaturalTaskParser.parse("reunión el lunes 24", now, zone)
        assertNotNull("el lunes 24 debe anclar fecha", r.dueAt)
        assertEquals("reunión", r.title.trim())
        val fecha = Instant.ofEpochMilli(r.dueAt!!).atZone(zone).toLocalDate()
        assertEquals(LocalDate.of(2026, 8, 24), fecha)
    }

    @Test fun weekdayDiaNoCaeHoyAnclaAlNumeroNoAlWeekday() {
        // "el lunes 25": el próximo lunes es el 24, pero el 25 (martes) es el día explícito.
        // El número es más específico → ancla al 25 (no al próximo lunes 24).
        val r = NaturalTaskParser.parse("reunión el lunes 25", now, zone)
        assertNotNull(r.dueAt)
        assertEquals("reunión", r.title.trim())
        val fecha = Instant.ofEpochMilli(r.dueAt!!).atZone(zone).toLocalDate()
        assertEquals(LocalDate.of(2026, 8, 25), fecha)
    }

    @Test fun weekdayDiaConHora() {
        val r = NaturalTaskParser.parse("reunión el lunes 24 a las 9", now, zone)
        assertNotNull(r.dueAt)
        assertEquals("reunión", r.title.trim())
        val zdt = Instant.ofEpochMilli(r.dueAt!!).atZone(zone)
        assertEquals(LocalDate.of(2026, 8, 24), zdt.toLocalDate())
        assertEquals(9, zdt.hour)
    }

    @Test fun weekdayDia30AnclaFinDeMes() {
        val r = NaturalTaskParser.parse("reunión el sábado 30", now, zone)
        assertNotNull(r.dueAt)
        assertEquals("reunión", r.title.trim())
        val fecha = Instant.ofEpochMilli(r.dueAt!!).atZone(zone).toLocalDate()
        assertEquals(LocalDate.of(2026, 8, 30), fecha)
    }

    @Test fun weekdayDia31ConHoraDeNoche() {
        val r = NaturalTaskParser.parse("reunión el domingo 31 a las 8 de la noche", now, zone)
        assertNotNull(r.dueAt)
        assertEquals("reunión", r.title.trim())
        val zdt = Instant.ofEpochMilli(r.dueAt!!).atZone(zone)
        assertEquals(LocalDate.of(2026, 8, 31), zdt.toLocalDate())
        assertEquals(20, zdt.hour)
    }

    @Test fun proximoWeekdayDiaAnclaAlNumero() {
        val r = NaturalTaskParser.parse("reunión el próximo lunes 25", now, zone)
        assertNotNull(r.dueAt)
        assertEquals("reunión", r.title.trim())
        val fecha = Instant.ofEpochMilli(r.dueAt!!).atZone(zone).toLocalDate()
        assertEquals(LocalDate.of(2026, 8, 25), fecha)
    }

    @Test fun weekdayDiaConNombreMesUsaMonthName() {
        // No-regresión: "el lunes 24 de septiembre" lo resuelve monthNamePattern (mes+ día).
        val r = NaturalTaskParser.parse("reunión el lunes 24 de septiembre", now, zone)
        assertNotNull(r.dueAt)
        assertEquals("reunión", r.title.trim())
        val fecha = Instant.ofEpochMilli(r.dueAt!!).atZone(zone).toLocalDate()
        assertEquals(LocalDate.of(2026, 9, 24), fecha)
    }

    @Test fun numeroSinWeekdayEsContenidoNoFecha() {
        // No-falso-positivo: "reunión 24" (número tras sustantivo, sin weekday) no se toca.
        val r = NaturalTaskParser.parse("reunión 24 del comité", now, zone)
        assertNull(r.dueAt)
        assertEquals("reunión 24 del comité", r.title.trim())
    }

    @Test fun weekdaySoloSinNumeroSigueResolviendoWeekday() {
        // No-regresión: "el lunes" (sin número) sigue anclando al próximo lunes.
        val r = NaturalTaskParser.parse("reunión el lunes", now, zone)
        assertNotNull(r.dueAt)
        assertEquals("reunión", r.title.trim())
        val fecha = Instant.ofEpochMilli(r.dueAt!!).atZone(zone).toLocalDate()
        assertEquals(LocalDate.of(2026, 8, 24), fecha)
    }

    @Test fun weekdayQueVieneSinNumeroSigueIntacto() {
        // No-regresión: "el lunes que viene" (modificador, sin número) intacto.
        val r = NaturalTaskParser.parse("reunión el lunes que viene", now, zone)
        assertNotNull(r.dueAt)
        assertEquals("reunión", r.title.trim())
        val fecha = Instant.ofEpochMilli(r.dueAt!!).atZone(zone).toLocalDate()
        assertEquals(LocalDate.of(2026, 8, 24), fecha)
    }
}
