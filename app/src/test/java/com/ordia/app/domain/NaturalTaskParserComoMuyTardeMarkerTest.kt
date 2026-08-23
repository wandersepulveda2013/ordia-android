package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.924: marcador de plazo coloquial «como muy tarde» (no later than) junto a un
 * ancla temporal. Forma cotidiana del habla de vencimientos («pagar la renta mañana
 * como muy tarde»), hermana directa de «a más tardar» (c.3486): la fecha ya se
 * resolvía bien pero el marcador sobrevivía como residuo en el título («pagar la
 * renta como muy tarde»), medido 6/6 por la sonda del ciclo (4 trailing + 2
 * leading; familia (B) del barrido persistido `tools/probe/ParserDeadlineSweepProbe.kt`
 * de c.918, 3/3 trailing). Doctrina simétrica a «sin falta» (c.918): se borra SÓLO
 * cuando va pegado a un ancla — tras ella («mañana como muy tarde») o antes
 * («como muy tarde mañana»)—; sin ancla («terminarlo como muy tarde») se conserva
 * íntegro para no mutar contenido que quizá no es marcador de plazo («llegó como
 * muy tarde a la reunión»). Determinista (regex), cero random, cero IA fingida,
 * cero UI.
 */
class NaturalTaskParserComoMuyTardeMarkerTest {

    private val zone = ZoneId.of("America/Santo_Domingo")
    // domingo 2026-08-23 12:00 (mismo now de la sonda del ciclo)
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 23), LocalTime.NOON, zone)

    private fun parse(text: String) = NaturalTaskParser.parse(text, now, zone)

    private fun date(text: String) = DateRules.toLocalDate(parse(text).dueAt!!, zone)

    // ---- Positivos: ancla + marcador (tras el ancla) ----

    @Test fun trailingWeekday_limpiaTitulo() {
        val r = parse("entregar el informe el viernes como muy tarde")
        assertEquals("entregar el informe", r.title)
        assertEquals(LocalDate.of(2026, 8, 28), date("entregar el informe el viernes como muy tarde"))
    }

    @Test fun trailingManana_limpiaTitulo() {
        val r = parse("pagar la renta mañana como muy tarde")
        assertEquals("pagar la renta", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), date("pagar la renta mañana como muy tarde"))
    }

    @Test fun trailingHoy_limpiaTitulo() {
        val r = parse("terminar la tarea hoy como muy tarde")
        assertEquals("terminar la tarea", r.title)
        assertEquals(LocalDate.of(2026, 8, 23), date("terminar la tarea hoy como muy tarde"))
    }

    @Test fun trailingPasadoManana_limpiaTitulo() {
        val r = parse("revisar el borrador pasado mañana como muy tarde")
        assertEquals("revisar el borrador", r.title)
        assertEquals(LocalDate.of(2026, 8, 25), date("revisar el borrador pasado mañana como muy tarde"))
    }

    // ---- Positivos: marcador antes del ancla ----

    @Test fun leadingManana_limpiaTitulo() {
        val r = parse("como muy tarde mañana pagar la renta")
        assertEquals("pagar la renta", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), date("como muy tarde mañana pagar la renta"))
    }

    @Test fun leadingWeekday_limpiaTitulo() {
        val r = parse("como muy tarde el lunes entregar el informe")
        assertEquals("entregar el informe", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), date("como muy tarde el lunes entregar el informe"))
    }

    // ---- Guards: sin ancla adyacente NO se toca ----

    @Test fun guardSinAncla_conservaMarcador() {
        // Sin ancla temporal el marcador se conserva (no se muta contenido):
        // título íntegro y dueAt null.
        val r = parse("terminarlo como muy tarde")
        assertEquals("terminarlo como muy tarde", r.title)
        assertNull(r.dueAt)
    }

    @Test fun guardPasado_conservaFrase() {
        // "llegó como muy tarde" es pasado declarativo, no plazo: intacto.
        val r = parse("llegó como muy tarde a la reunión")
        assertEquals("llegó como muy tarde a la reunión", r.title)
        assertNull(r.dueAt)
    }

    @Test fun guardLeadingSinAncla_conservaFrase() {
        // "como muy tarde" al inicio sin ancla posterior: intacto.
        val r = parse("como muy tarde llegué a casa")
        assertEquals("como muy tarde llegué a casa", r.title)
        assertNull(r.dueAt)
    }

    // ---- Regresiones: hermanas de la familia de marcadores intactas ----

    @Test fun regresionAMasTardar_intacta() {
        val r = parse("entregar informe a más tardar el viernes")
        assertEquals("entregar informe", r.title)
        assertEquals(LocalDate.of(2026, 8, 28), date("entregar informe a más tardar el viernes"))
    }

    @Test fun regresionSinFalta_intacta() {
        val r = parse("llamar a Juan el viernes sin falta")
        assertEquals("llamar a Juan", r.title)
        assertEquals(LocalDate.of(2026, 8, 28), date("llamar a Juan el viernes sin falta"))
    }

    @Test fun regresionHoyMismo_intacta() {
        val r = parse("terminar el informe hoy mismo")
        assertEquals("terminar el informe", r.title)
        assertEquals(LocalDate.of(2026, 8, 23), date("terminar el informe hoy mismo"))
    }
}
