package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.909: intensificador de plazo «sin falta» (without fail) junto a un ancla temporal.
 * Forma cotidiana del habla («pagar la luz sin falta mañana», «llamar a Juan el
 * viernes sin falta»): la fecha ya se resolvía bien pero el intensificador sobrevivía
 * como residuo en el título («pagar la luz sin falta»), medido 3/3 por la sonda de
 * descubrimiento del ciclo. Doctrina simétrica a «a más tardar» (c.3486): se borra
 * SÓLO cuando va pegado a un ancla — tras ella («el viernes sin falta») o antes
 * («sin falta mañana»)—; sin ancla («pagar la luz sin falta») se conserva íntegro
 * para no mutar contenido que quizá no es intensificador («el informe sin falta de
 * ortografía»). Determinista (regex), cero random, cero IA fingida, cero UI.
 */
class NaturalTaskParserSinFaltaMarkerTest {

    private val zone = ZoneId.of("America/Santo_Domingo")
    // domingo 2026-08-23 12:00 (mismo now de la sonda del ciclo)
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 23), LocalTime.NOON, zone)

    private fun parse(text: String) = NaturalTaskParser.parse(text, now, zone)

    private fun date(text: String) = DateRules.toLocalDate(parse(text).dueAt!!, zone)

    // ---- Positivos: ancla + intensificador (tras el ancla) ----

    @Test fun trailingWeekday_limpiaTitulo() {
        val r = parse("llamar a Juan el viernes sin falta")
        assertEquals("llamar a Juan", r.title)
        assertEquals(LocalDate.of(2026, 8, 28), date("llamar a Juan el viernes sin falta"))
    }

    @Test fun trailingManana_limpiaTitulo() {
        val r = parse("entregar el informe mañana sin falta")
        assertEquals("entregar el informe", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), date("entregar el informe mañana sin falta"))
    }

    @Test fun trailingHoy_limpiaTitulo() {
        val r = parse("pagar la renta hoy sin falta")
        assertEquals("pagar la renta", r.title)
        assertEquals(LocalDate.of(2026, 8, 23), date("pagar la renta hoy sin falta"))
    }

    @Test fun trailingPasadoManana_limpiaTitulo() {
        val r = parse("revisar el borrador pasado mañana sin falta")
        assertEquals("revisar el borrador", r.title)
        assertEquals(LocalDate.of(2026, 8, 25), date("revisar el borrador pasado mañana sin falta"))
    }

    // ---- Positivos: intensificador antes del ancla ----

    @Test fun leadingManana_limpiaTitulo() {
        val r = parse("pagar la luz sin falta mañana")
        assertEquals("pagar la luz", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), date("pagar la luz sin falta mañana"))
    }

    @Test fun leadingWeekday_limpiaTitulo() {
        val r = parse("entregar el informe sin falta el lunes")
        assertEquals("entregar el informe", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), date("entregar el informe sin falta el lunes"))
    }

    // ---- Guards: sin ancla adyacente NO se toca ----

    @Test fun guardSinAncla_conservaIntensificador() {
        // Sin ancla temporal el intensificador se conserva (no se muta contenido):
        // título íntegro y dueAt null.
        val r = parse("pagar la luz sin falta")
        assertEquals("pagar la luz sin falta", r.title)
        assertNull(r.dueAt)
    }

    @Test fun guardContenido_sinFaltaDe_noSeToca() {
        // «sin falta de ortografía» es contenido (sin ancla adyacente): intacto.
        val r = parse("el informe sin falta de ortografía")
        assertEquals("el informe sin falta de ortografía", r.title)
        assertNull(r.dueAt)
    }

    @Test fun guardLaFalta_sustantivo_noSeToca() {
        // «la falta» (sustantivo) no es el intensificador «sin falta»: intacto.
        val r = parse("revisar la falta de asistencia")
        assertEquals("revisar la falta de asistencia", r.title)
        assertNull(r.dueAt)
    }

    @Test fun guardNoFaltar_verbo_noSeToca() {
        // «no faltar a la cita» es un verbo, no el intensificador: título intacto,
        // el ancla «mañana» sigue resolviendo.
        val r = parse("no faltar a la cita mañana")
        assertEquals("no faltar a la cita", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), date("no faltar a la cita mañana"))
    }

    // ---- Regresiones de la familia de marcadores ya cubierta ----

    @Test fun regresionAMasTardar_sigueLimpio() {
        val r = parse("entregar informe a más tardar el viernes")
        assertEquals("entregar informe", r.title)
        assertEquals(LocalDate.of(2026, 8, 28), date("entregar informe a más tardar el viernes"))
    }

    @Test fun regresionWeekdayPlano_sigueIgual() {
        val r = parse("reunión el viernes")
        assertEquals("reunión", r.title)
        assertEquals(LocalDate.of(2026, 8, 28), date("reunión el viernes"))
    }

    @Test fun regresionMananaPlano_sigueIgual() {
        val r = parse("cita mañana")
        assertEquals("cita", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), date("cita mañana"))
    }
}
