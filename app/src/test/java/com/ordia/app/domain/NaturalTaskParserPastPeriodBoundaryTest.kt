package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * c.985-(iii): palabra-límite («finales/mediados/principios/fin») + período relativo
 * PASADO («del mes pasado», «de la semana pasada», «del año pasado»). Antes
 * `lastPeriodPattern` consumía solo «el mes pasado» (resolviendo a hoy−1 período) y
 * la palabra-límite sobrevivía como residuo en el título («revisar las facturas a
 * finales») con fecha que ignoraba el límite. Ahora la frase íntegra se consume y se
 * resuelve al límite del período anterior (fin=último día, mediados=15/miércoles/30-jun,
 * principios=1/lunes/1-ene), hora 09:00 como el resto de límites.
 *
 * Semana = lunes→domingo (doctrina c.778). El artículo «la» es OBLIGATORIO para
 * semana: «fin de semana pasado» (sin artículo) sigue siendo territorio del patrón
 * de fin de semana (sábado), no de esta clase.
 */
class NaturalTaskParserPastPeriodBoundaryTest {

    private val zone = ZoneId.of("America/Santo_Domingo")
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 7, 29), LocalTime.NOON, zone)

    private fun date(text: String) =
        DateRules.toLocalDate(NaturalTaskParser.parse(text, now, zone).dueAt!!, zone)

    // --- mes pasado ---

    @Test fun finalesDelMesPasadoAnclaUltimoDiaDelMesAnterior() {
        val r = NaturalTaskParser.parse("revisar las facturas a finales del mes pasado", now, zone)
        assertEquals("revisar las facturas", r.title)
        assertEquals(LocalDate.of(2026, 6, 30), DateRules.toLocalDate(r.dueAt!!, zone))
        assertEquals(LocalTime.of(9, 0), DateRules.toLocalDate(r.dueAt!!, zone).let {
            java.time.Instant.ofEpochMilli(r.dueAt!!).atZone(zone).toLocalTime()
        })
    }

    @Test fun mediadosDelMesPasadoAnclaDia15DelMesAnterior() {
        val r = NaturalTaskParser.parse("revisar las facturas a mediados del mes pasado", now, zone)
        assertEquals("revisar las facturas", r.title)
        assertEquals(LocalDate.of(2026, 6, 15), DateRules.toLocalDate(r.dueAt!!, zone))
    }

    @Test fun principiosDelMesPasadoAnclaDia1DelMesAnterior() {
        val r = NaturalTaskParser.parse("revisar las facturas a principios del mes pasado", now, zone)
        assertEquals("revisar las facturas", r.title)
        assertEquals(LocalDate.of(2026, 6, 1), DateRules.toLocalDate(r.dueAt!!, zone))
    }

    @Test fun finDeMesPasadoSingularAnclaUltimoDiaDelMesAnterior() {
        val r = NaturalTaskParser.parse("pagar la renta a fin de mes pasado", now, zone)
        assertEquals("pagar la renta", r.title)
        assertEquals(LocalDate.of(2026, 6, 30), DateRules.toLocalDate(r.dueAt!!, zone))
    }

    @Test fun mesAnteriorSinonimoDePasado() {
        assertEquals(LocalDate.of(2026, 6, 30), date("revisar las facturas a finales del mes anterior"))
    }

    // --- semana pasada (lunes→domingo) ---

    @Test fun finalesDeLaSemanaPasadaAnclaDomingoAnterior() {
        val r = NaturalTaskParser.parse("revisar las facturas a finales de la semana pasada", now, zone)
        assertEquals("revisar las facturas", r.title)
        assertEquals(LocalDate.of(2026, 7, 26), DateRules.toLocalDate(r.dueAt!!, zone))
    }

    @Test fun mediadosDeLaSemanaPasadaAnclaMiercolesAnterior() {
        assertEquals(LocalDate.of(2026, 7, 22), date("revisar las facturas a mediados de la semana pasada"))
    }

    @Test fun principiosDeLaSemanaPasadaAnclaLunesAnterior() {
        assertEquals(LocalDate.of(2026, 7, 20), date("revisar las facturas a principios de la semana pasada"))
    }

    // c.983: variantes de conector/posición medidas en sonda (follow-up (iii) c.646
    // VERIFICADO YA RESUELTO por esta clase) — sin «a», «para», prefija, «fin»
    // singular y sinónimo «anterior» anclan igual al domingo de la semana pasada.

    @Test fun finalesDeLaSemanaPasadaSinConectorAnclaDomingoAnterior() {
        val r = NaturalTaskParser.parse("revisar las facturas finales de la semana pasada", now, zone)
        assertEquals("revisar las facturas", r.title)
        assertEquals(LocalDate.of(2026, 7, 26), DateRules.toLocalDate(r.dueAt!!, zone))
    }

    @Test fun finalesDeLaSemanaPasadaConParaAnclaDomingoAnterior() {
        val r = NaturalTaskParser.parse("revisar las facturas para finales de la semana pasada", now, zone)
        assertEquals("revisar las facturas", r.title)
        assertEquals(LocalDate.of(2026, 7, 26), DateRules.toLocalDate(r.dueAt!!, zone))
    }

    @Test fun finalesDeLaSemanaPasadaPrefijaAnclaDomingoAnterior() {
        val r = NaturalTaskParser.parse("finales de la semana pasada revisar las facturas", now, zone)
        assertEquals("revisar las facturas", r.title)
        assertEquals(LocalDate.of(2026, 7, 26), DateRules.toLocalDate(r.dueAt!!, zone))
    }

    @Test fun finSingularDeLaSemanaPasadaAnclaDomingoAnterior() {
        val r = NaturalTaskParser.parse("revisar las facturas a fin de la semana pasada", now, zone)
        assertEquals("revisar las facturas", r.title)
        assertEquals(LocalDate.of(2026, 7, 26), DateRules.toLocalDate(r.dueAt!!, zone))
    }

    @Test fun finalesDeLaSemanaAnteriorSinonimoAnclaDomingoAnterior() {
        val r = NaturalTaskParser.parse("revisar las facturas a finales de la semana anterior", now, zone)
        assertEquals("revisar las facturas", r.title)
        assertEquals(LocalDate.of(2026, 7, 26), DateRules.toLocalDate(r.dueAt!!, zone))
    }

    // --- año pasado ---

    @Test fun finalesDelAnoPasadoAncla31DiciembreAnterior() {
        val r = NaturalTaskParser.parse("hacer el balance a finales del año pasado", now, zone)
        assertEquals("hacer el balance", r.title)
        assertEquals(LocalDate.of(2025, 12, 31), DateRules.toLocalDate(r.dueAt!!, zone))
    }

    @Test fun finDeAnoPasadoSinArticuloAncla31DiciembreAnterior() {
        assertEquals(LocalDate.of(2025, 12, 31), date("informe a fin de año pasado"))
    }

    @Test fun mediadosDelAnoPasadoAncla30JunioAnterior() {
        assertEquals(LocalDate.of(2025, 6, 30), date("hacer el balance a mediados del año pasado"))
    }

    @Test fun principiosDelAnoPasadoAncla1EneroAnterior() {
        assertEquals(LocalDate.of(2025, 1, 1), date("hacer el balance a principios del año pasado"))
    }

    // --- regresiones: formas vecinas NO deben cambiar ---

    @Test fun elMesPasadoSinLimiteSinCambio() {
        val r = NaturalTaskParser.parse("revisar las facturas el mes pasado", now, zone)
        assertEquals("revisar las facturas", r.title)
        assertEquals(LocalDate.of(2026, 6, 29), DateRules.toLocalDate(r.dueAt!!, zone))
    }

    @Test fun finDeSemanaPasadoSinArticuloSigueSiendoSabado() {
        val r = NaturalTaskParser.parse("salida el fin de semana pasado", now, zone)
        assertEquals("salida", r.title)
        assertEquals(LocalDate.of(2026, 7, 25), DateRules.toLocalDate(r.dueAt!!, zone))
    }

    @Test fun pasadoMananaSinCambio() {
        val r = NaturalTaskParser.parse("comprar el regalo pasado mañana", now, zone)
        assertEquals("comprar el regalo", r.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(r.dueAt!!, zone))
    }

    @Test fun finalesDeOctubreSinCambio() {
        val r = NaturalTaskParser.parse("cita a finales de octubre", now, zone)
        assertEquals("cita", r.title)
        assertEquals(LocalDate.of(2026, 10, 31), DateRules.toLocalDate(r.dueAt!!, zone))
    }

    @Test fun ultimoViernesDelMesPasadoSinCambio() {
        val r = NaturalTaskParser.parse("pagar la renta el último viernes del mes pasado", now, zone)
        assertEquals("pagar la renta", r.title)
        assertEquals(LocalDate.of(2026, 6, 26), DateRules.toLocalDate(r.dueAt!!, zone))
    }

    @Test fun finDeMesSinPasadoSinCambio() {
        val r = NaturalTaskParser.parse("pagar la renta a fin de mes", now, zone)
        assertEquals("pagar la renta", r.title)
        assertEquals(LocalDate.of(2026, 7, 31), DateRules.toLocalDate(r.dueAt!!, zone))
    }

    @Test fun viernesPasadoSinCambio() {
        val r = NaturalTaskParser.parse("reunión el viernes pasado", now, zone)
        assertEquals("reunión", r.title)
        assertEquals(LocalDate.of(2026, 7, 24), DateRules.toLocalDate(r.dueAt!!, zone))
    }
}
