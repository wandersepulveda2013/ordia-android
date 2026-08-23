package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.908: palabra-límite dialectal «últimos» (= «finales») como límite de fin de mes/
 * período. Forma cotidiana en España y LatAm («pagar la renta a últimos de agosto»,
 * «a últimos del mes», «a últimos del mes que viene», «a últimos del mes pasado»).
 * Antes: (a) con mes nombrado caía a dueAt=null con la frase íntegra de residuo en el
 * título (vencimiento olvidado, P1); (b) con período relativo («del mes que viene» /
 * «del mes pasado») los patrones genéricos de período robaban el período y dejaban
 * «a últimos» como residuo con una fecha ERRÓNEA (09-20/07-22 genérica, no el límite).
 * Ahora «últimos» se reconoce en las mismas tres clases que «finales»:
 * [monthBoundaryNamePattern] (mes nombrado), [endOfMonthPattern] (mes actual/que viene)
 * y [lastPeriodBoundaryPattern] (período pasado), más [endOfYearPattern] («a últimos
 * de año»). Resolución idéntica a «finales»: último día del mes/período, hora 09:00.
 */
class NaturalTaskParserUltimosBoundaryTest {

    private val zone = ZoneId.of("America/Santo_Domingo")
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 21), LocalTime.NOON, zone)

    private fun date(text: String) =
        DateRules.toLocalDate(NaturalTaskParser.parse(text, now, zone).dueAt!!, zone)

    // --- mes nombrado ---

    @Test fun ultimosDeAgostoAnclaUltimoDiaDelMesNombrado() {
        val r = NaturalTaskParser.parse("pagar la renta a últimos de agosto", now, zone)
        assertEquals("pagar la renta", r.title)
        assertEquals(LocalDate.of(2026, 8, 31), DateRules.toLocalDate(r.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), java.time.Instant.ofEpochMilli(r.dueAt!!).atZone(zone).toLocalTime())
    }

    @Test fun ultimosSinAcentoDeSeptiembre() {
        val r = NaturalTaskParser.parse("entregar el informe a ultimos de septiembre", now, zone)
        assertEquals("entregar el informe", r.title)
        assertEquals(LocalDate.of(2026, 9, 30), DateRules.toLocalDate(r.dueAt!!, zone))
    }

    @Test fun ultimosDeOctubreConAnioExplicito() {
        assertEquals(LocalDate.of(2026, 10, 31), date("cita con el banco a últimos de octubre de 2026"))
    }

    @Test fun ultimosDeFebreroRuedaAlAnioSiguiente() {
        assertEquals(LocalDate.of(2027, 2, 28), date("pago a últimos de febrero"))
    }

    // --- mes relativo futuro/actual ---

    @Test fun ultimosDelMesAnclaFinDelMesEnCurso() {
        val r = NaturalTaskParser.parse("pagar a últimos del mes", now, zone)
        assertEquals("pagar", r.title)
        assertEquals(LocalDate.of(2026, 8, 31), DateRules.toLocalDate(r.dueAt!!, zone))
    }

    @Test fun ultimosDelMesQueVieneAnclaFinDelMesSiguiente() {
        val r = NaturalTaskParser.parse("pagar a últimos del mes que viene", now, zone)
        assertEquals("pagar", r.title)
        assertEquals(LocalDate.of(2026, 9, 30), DateRules.toLocalDate(r.dueAt!!, zone))
    }

    // --- período pasado ---

    @Test fun ultimosDelMesPasadoAnclaUltimoDiaDelMesAnterior() {
        val r = NaturalTaskParser.parse("revisar a últimos del mes pasado", now, zone)
        assertEquals("revisar", r.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(r.dueAt!!, zone))
    }

    @Test fun ultimosDeLaSemanaPasadaAnclaDomingoAnterior() {
        assertEquals(LocalDate.of(2026, 8, 16), date("revisar las facturas a últimos de la semana pasada"))
    }

    @Test fun ultimosDelAnioPasadoAncla31DiciembreAnterior() {
        assertEquals(LocalDate.of(2025, 12, 31), date("cierre a últimos del año pasado"))
    }

    // --- año ---

    @Test fun ultimosDeAnioAncla31Diciembre() {
        val r = NaturalTaskParser.parse("cierre fiscal a últimos de año", now, zone)
        assertEquals("cierre fiscal", r.title)
        assertEquals(LocalDate.of(2026, 12, 31), DateRules.toLocalDate(r.dueAt!!, zone))
    }

    @Test fun ultimosDelAnioQueVieneAncla31DiciembreSiguiente() {
        assertEquals(LocalDate.of(2027, 12, 31), date("renovación a últimos del año que viene"))
    }

    // --- guards (NO deben capturar como límite) ---

    @Test fun losUltimosDeLaFilaNoEsLimite() {
        val r = NaturalTaskParser.parse("los últimos de la fila", now, zone)
        assertNull(r.dueAt)
        assertEquals("los últimos de la fila", r.title)
    }

    @Test fun repasarLosUltimosDetallesNoEsLimite() {
        val r = NaturalTaskParser.parse("repasar los últimos detalles", now, zone)
        assertNull(r.dueAt)
        assertEquals("repasar los últimos detalles", r.title)
    }

    @Test fun aUltimosDesnudoSinGenitivoNoEsLimite() {
        val r = NaturalTaskParser.parse("a últimos", now, zone)
        assertNull(r.dueAt)
        assertEquals("a últimos", r.title)
    }

    // --- regresiones de la familia ya cubierta ---

    @Test fun regresionFinDeMes() {
        assertEquals(LocalDate.of(2026, 8, 31), date("pago a fin de mes"))
    }

    @Test fun regresionFinalesDeOctubre() {
        assertEquals(LocalDate.of(2026, 10, 31), date("renta a finales de octubre"))
    }

    @Test fun regresionFinalesDelMesQueViene() {
        assertEquals(LocalDate.of(2026, 9, 30), date("pagar a finales del mes que viene"))
    }

    @Test fun regresionFinalesDelMesPasado() {
        assertEquals(LocalDate.of(2026, 7, 31), date("revisar las facturas a finales del mes pasado"))
    }

    @Test fun regresionUltimoDiaDelMesSingular() {
        assertEquals(LocalDate.of(2026, 8, 31), date("pagar el último día del mes"))
    }

    @Test fun regresionPrimerosDeSeptiembre() {
        assertEquals(LocalDate.of(2026, 9, 1), date("entregar el informe a primeros de septiembre"))
    }
}
