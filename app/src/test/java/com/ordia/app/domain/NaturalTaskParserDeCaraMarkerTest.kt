package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.926: marcador de intención coloquial «de cara a(l)» (with an eye to / by)
 * pegado a un ancla de día («preparar la presentación de cara al lunes»).
 * Forma cotidiana del habla de vencimientos, hermana leading de «como muy
 * tarde» (c.924): la fecha ya se resolvía bien pero el marcador sobrevivía como
 * residuo gramaticalmente colgado en el título («preparar la presentación de
 * cara»), medido 5/5 por la sonda efímera del ciclo
 * (`/tmp/probe925/DeCaraProbe.kt`, motor real vía `tools/run_probe.sh`,
 * now=domingo 2026-08-23 12:00; familia (E) del barrido persistido
 * `tools/probe/ParserDeadlineSweepProbe.kt` de c.918, marcada débil por riesgo
 * de robar contenido). La medición resolvió el riesgo: se borra SÓLO cuando va
 * seguido DIRECTAMENTE de un ancla de día (weekday, hoy, mañana, pasado mañana,
 * fin de semana/finde); con sustantivo de contenido a continuación («de cara al
 * examen del viernes», «de cara al viaje del lunes», «de cara a la maratón»)
 * el título queda byte-idéntico al PRE. Determinista (regex), cero random,
 * cero IA fingida, cero UI.
 */
class NaturalTaskParserDeCaraMarkerTest {

    private val zone = ZoneId.of("America/Santo_Domingo")
    // domingo 2026-08-23 12:00 (mismo now de la sonda del ciclo)
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 23), LocalTime.NOON, zone)

    private fun parse(text: String) = NaturalTaskParser.parse(text, now, zone)

    private fun date(text: String) = DateRules.toLocalDate(parse(text).dueAt!!, zone)

    // ---- Positivos: marcador «de cara a(l)» + ancla de día ----

    @Test fun deCaraAlLunes_limpiaTitulo() {
        val r = parse("preparar la presentación de cara al lunes")
        assertEquals("preparar la presentación", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), date("preparar la presentación de cara al lunes"))
    }

    @Test fun deCaraAlViernes_limpiaTitulo() {
        val r = parse("tener listo el informe de cara al viernes")
        assertEquals("tener listo el informe", r.title)
        assertEquals(LocalDate.of(2026, 8, 28), date("tener listo el informe de cara al viernes"))
    }

    @Test fun deCaraAManana_limpiaTitulo() {
        val r = parse("dejar todo listo de cara a mañana")
        assertEquals("dejar todo listo", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), date("dejar todo listo de cara a mañana"))
    }

    @Test fun deCaraAlFinDeSemana_limpiaTitulo() {
        val r = parse("organizar la casa de cara al fin de semana")
        assertEquals("organizar la casa", r.title)
        assertEquals(LocalDate.of(2026, 8, 29), date("organizar la casa de cara al fin de semana"))
    }

    @Test fun deCaraAlMiercoles_limpiaTitulo() {
        val r = parse("avisar al cliente de cara al miércoles")
        assertEquals("avisar al cliente", r.title)
        assertEquals(LocalDate.of(2026, 8, 26), date("avisar al cliente de cara al miércoles"))
    }

    // ---- Guards: sustantivo de contenido tras «de cara a» NO se toca ----

    @Test fun guardExamenDelViernes_conservaMarcador() {
        // "de cara al examen del viernes": el sustantivo no es ancla de día;
        // título byte-idéntico al PRE (la fecha del viernes se consume igual
        // que antes — comportamiento preexistente).
        val r = parse("estudiar de cara al examen del viernes")
        assertEquals("estudiar de cara al examen", r.title)
        assertEquals(LocalDate.of(2026, 8, 28), date("estudiar de cara al examen del viernes"))
    }

    @Test fun guardViajeDelLunes_conservaMarcador() {
        // "de cara al viaje del lunes": ídem; "al viaje" es contenido.
        val r = parse("preparar la maleta de cara al viaje del lunes")
        assertEquals("preparar la maleta de cara al viaje", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), date("preparar la maleta de cara al viaje del lunes"))
    }

    @Test fun guardMaraton_conservaFrase() {
        // Sin ancla temporal en absoluto: intacto, dueAt null.
        val r = parse("entrenar de cara a la maratón")
        assertEquals("entrenar de cara a la maratón", r.title)
        assertNull(r.dueAt)
    }

    @Test fun guardFuturo_conservaFrase() {
        val r = parse("ahorrar de cara al futuro")
        assertEquals("ahorrar de cara al futuro", r.title)
        assertNull(r.dueAt)
    }

    @Test fun guardElecciones_conservaFrase() {
        val r = parse("posicionarse de cara a las elecciones")
        assertEquals("posicionarse de cara a las elecciones", r.title)
        assertNull(r.dueAt)
    }

    // ---- Regresiones: hermanas de la familia de marcadores intactas ----

    @Test fun regresionWeekdayPlano_intacto() {
        val r = parse("reunión el lunes")
        assertEquals("reunión", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), date("reunión el lunes"))
    }

    @Test fun regresionMananaMisma_intacta() {
        val r = parse("avisar a Juan por la mañana misma")
        assertEquals("avisar a Juan", r.title)
        assertEquals(LocalDate.of(2026, 8, 23), date("avisar a Juan por la mañana misma"))
    }

    @Test fun regresionComoMuyTarde_intacta() {
        val r = parse("pagar la renta mañana como muy tarde")
        assertEquals("pagar la renta", r.title)
        assertEquals(LocalDate.of(2026, 8, 24), date("pagar la renta mañana como muy tarde"))
    }
}
