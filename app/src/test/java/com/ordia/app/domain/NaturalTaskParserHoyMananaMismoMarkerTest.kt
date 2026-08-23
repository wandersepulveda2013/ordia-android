package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.923: enfático «mismo» pegado a «hoy»/«mañana» («hoy mismo», «mañana mismo»).
 * Forma cotidianísima del habla de vencimientos («terminar el informe hoy mismo»,
 * «llamar al médico mañana mismo»): la fecha ya se resolvía bien pero el enfático
 * sobrevivía como residuo en el título («terminar el informe mismo»), medido 4/4
 * por la sonda del ciclo (hermana de la familia (C) del barrido c.918: «ahora
 * mismo»/«ya mismo» ya estaban cubiertos por su patrón, pero «hoy/mañana mismo»
 * no). Doctrina simétrica a «sin falta» (c.918): se borra SÓLO tras el ancla
 * «hoy»/«mañana»; «mismo» no adyacente («el mismo lugar», «el mismo día») es
 * contenido y se conserva íntegro. Determinista (regex), cero random, cero IA
 * fingida, cero UI.
 */
class NaturalTaskParserHoyMananaMismoMarkerTest {

    private val zone = ZoneId.of("America/Santo_Domingo")
    // domingo 2026-08-23 12:00 (mismo now de la sonda del ciclo)
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 23), LocalTime.NOON, zone)

    private fun parse(text: String) = NaturalTaskParser.parse(text, now, zone)

    private fun date(text: String) = DateRules.toLocalDate(parse(text).dueAt!!, zone)

    // ---- Positivos: ancla + enfático (tras el ancla) ----

    @Test fun trailingHoy_limpiaTitulo() {
        val r = parse("terminar el informe hoy mismo")
        assertEquals("terminar el informe", r.title)
        assertEquals(LocalDate.of(2026, 8, 23), date("terminar el informe hoy mismo"))
    }

    @Test fun trailingManana_limpiaTitulo() {
        val r = parse("llamar al médico mañana mismo")
        assertEquals("llamar al médico", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), date("llamar al médico mañana mismo"))
    }

    @Test fun trailingPasadoManana_limpiaTitulo() {
        // «pasado mañana mismo»: el enfático sigue a «mañana» dentro de la ancla
        // compuesta; quitarlo deja «pasado mañana», que resuelve igual.
        val r = parse("revisar el borrador pasado mañana mismo")
        assertEquals("revisar el borrador", r.title)
        assertEquals(LocalDate.of(2026, 8, 25), date("revisar el borrador pasado mañana mismo"))
    }

    @Test fun leadingHoy_limpiaTitulo() {
        val r = parse("hoy mismo terminar el informe")
        assertEquals("terminar el informe", r.title)
        assertEquals(LocalDate.of(2026, 8, 23), date("hoy mismo terminar el informe"))
    }

    // ---- Guards: «mismo» no adyacente al ancla NO se toca ----

    @Test fun guardMismoNoAdyacente_conservaContenido() {
        // «el mismo lugar» es contenido: el enfático no va pegado al ancla.
        val r = parse("visitar el mismo lugar mañana")
        assertEquals("visitar el mismo lugar", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), date("visitar el mismo lugar mañana"))
    }

    @Test fun guardElMismoDia_contenido_noSeToca() {
        // «el mismo día» es contenido (sin ancla adyacente): título íntegro, dueAt null.
        val r = parse("devolver el libro el mismo día")
        assertEquals("devolver el libro el mismo día", r.title)
        assertNull(r.dueAt)
    }

    @Test fun guardLaMismaSemana_contenido_noSeToca() {
        // «la misma semana» es contenido: intacta, dueAt null.
        val r = parse("la misma semana")
        assertEquals("la misma semana", r.title)
        assertNull(r.dueAt)
    }

    @Test fun guardPorLaManana_sinMismo_sigueIgual() {
        // «por la mañana» (parte del día, sin enfático) no se ve afectada.
        val r = parse("revisar el correo por la mañana")
        assertEquals("revisar el correo", r.title)
        assertEquals(LocalDate.of(2026, 8, 23), date("revisar el correo por la mañana"))
    }

    // ---- Regresiones de la familia ya cubierta ----

    @Test fun regresionAhoraMismo_sigueLimpio() {
        val r = parse("revisar el correo ahora mismo")
        assertEquals("revisar el correo", r.title)
        assertEquals(LocalDate.of(2026, 8, 23), date("revisar el correo ahora mismo"))
    }

    @Test fun regresionHoyPlano_sigueIgual() {
        val r = parse("terminar el informe hoy")
        assertEquals("terminar el informe", r.title)
        assertEquals(LocalDate.of(2026, 8, 23), date("terminar el informe hoy"))
    }

    @Test fun regresionMananaPlano_sigueIgual() {
        val r = parse("cita mañana")
        assertEquals("cita", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), date("cita mañana"))
    }

    @Test fun regresionWeekdayPlano_sigueIgual() {
        val r = parse("reunión el viernes")
        assertEquals("reunión", r.title)
        assertEquals(LocalDate.of(2026, 8, 28), date("reunión el viernes"))
    }
}
