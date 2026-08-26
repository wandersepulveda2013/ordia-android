package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1229 — lateral P1 ABIERTA registrada en c.1041 (UNIÓN) con pin
 * byte-idéntico ([NaturalTaskParserWeekdayFinalPreteritoNarrativoTest]);
 * re-pin pin→resuelto en este ciclo (precedente c.1033/c.1035). Weekday AL
 * FINAL con SUJETO NOMINAL en el PREFIJO («el paquete llegó el lunes», «la
 * alarma sonó el viernes», «mi hermano vino el jueves»): la ruta vocab de
 * [NaturalTaskParser.weekdayOccurrenceIsPreteriteNarrative] exigía que el
 * prefijo ARRANQUE con (ya)?+clíticos{0,2}+pretérito — un sujeto nominal
 * delante lo rompía → DOBLE daño P1 medido PRE con sonda persistida
 * `tools/probe/SubjectPrefixWeekdayProbe.kt` (motor real, now=domingo
 * 2026-08-23 12:00 America/Santo_Domingo): 7/7 targets anclando weekday
 * FALSO (relato convertido en compromiso que ensucia What Now) + título
 * MUTILADO (sin su weekday); 8/8 guards anclando correcto; 5/5 regresiones
 * estables.
 *
 * Fix (1 punto, re-uso sin duplicación — lección c.1016): cabeza NUEVA
 * [narrativeSubjectPrefixHead] — determinante opcional (el/la/los/las/mi/
 * tu/su/mis/tus/sus/un/una/unos/unas) + UNA palabra de sujeto — antes de
 * clíticos{0,2}+pretérito en la MISMA ruta vocab; candados conservadores
 * c.1023 intactos («quedar con» e infinitivo/«que» evaluados sobre el
 * complemento+weekday). Mismo conservadurismo c.950: ambiguas
 * pretérito/presente no disparan.
 *
 * Determinista (regex), cero random, cero IA fingida, cero UI.
 */
class NaturalTaskParserWeekdayFinalSubjectPrefixNarrativaTest {

    private val zone = ZoneId.of("America/Santo_Domingo")
    // domingo 2026-08-23 12:00 (mismo now de la sonda PRE persistida)
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 23), LocalTime.NOON, zone)

    private fun parse(text: String) = NaturalTaskParser.parse(text, now, zone)

    private fun assertNarrativeIntact(text: String) {
        val r = parse(text)
        assertNull("sin ancla falsa: $text", r.dueAt)
        assertEquals("título intacto: $text", text, r.title)
    }

    private fun assertAnchors(text: String, day: LocalDate, expectedTitle: String) {
        val r = parse(text)
        assertEquals(DateRules.toEpochMillis(day, LocalTime.of(9, 0), zone), r.dueAt)
        assertEquals(expectedTitle, r.title)
    }

    private val monday = LocalDate.of(2026, 8, 24)

    // ---- Capturas RED medidas (sonda PRE persistida) ----

    @Test fun elPaqueteLlegoElLunes_narrativaIntacta() =
        assertNarrativeIntact("el paquete llegó el lunes")

    @Test fun elPedidoLlegoElMiercoles_narrativaIntacta() =
        assertNarrativeIntact("el pedido llegó el miércoles")

    @Test fun laAlarmaSonoElViernes_narrativaIntacta() =
        assertNarrativeIntact("la alarma sonó el viernes")

    @Test fun losResultadosLlegaronElMartes_narrativaIntacta() =
        assertNarrativeIntact("los resultados llegaron el martes")

    @Test fun miHermanoVinoElJueves_narrativaIntacta() =
        assertNarrativeIntact("mi hermano vino el jueves")

    @Test fun elCarteroPasoElSabado_narrativaIntacta() =
        assertNarrativeIntact("el cartero pasó el sábado")

    @Test fun lasNoticiasSalieronElDomingo_narrativaIntacta() =
        assertNarrativeIntact("las noticias salieron el domingo")

    // ---- Guards: DEBEN seguir anclando (midieron ancla en PRE) ----

    @Test fun laReunionEsElLunes_presenteAncla() =
        assertAnchors("la reunión es el lunes", monday, "la reunión es")

    @Test fun tengoCitaConElDentistaElLunes_presenteAncla() =
        assertAnchors("tengo cita con el dentista el lunes", monday, "tengo cita con el dentista")

    @Test fun quedeElLunesConAna_quedarConSigueAncla() =
        assertAnchors("quedé el lunes con Ana", monday, "quedé con Ana")

    @Test fun saliAComprarElLunes_infinitivoEmbebidoSigueAncla() =
        assertAnchors("salí a comprar el lunes", monday, "salí a comprar")

    @Test fun elDentistaElLunesPorLaManana_sinVerboAncla() =
        assertAnchors("el dentista el lunes por la mañana", monday, "el dentista")

    @Test fun mananaMeVoyElLunes_prefijoTemporalPresenteAncla() =
        assertAnchors("mañana me voy el lunes", monday, "me voy")

    @Test fun elPaqueteElLunesQueViene_direccionFuturaAncla() =
        assertAnchors("el paquete el lunes que viene", monday, "el paquete")

    @Test fun salimosElLunes_ambiguaPretéritoPresenteSigueAncla() =
        assertAnchors("salimos el lunes", monday, "salimos")

    // ---- c.1229 F2 (complemento hermano): «quedar con» con SUJETO nominal ----
    // Candado c.1023 de clíticos ([ordinalHoraQuedarConArrangement]) sólo admite
    // clíticos; con sujeto nominal inequívoco la CITA real se suprimía en silencio.
    // Nueva cabeza nominal [weekdayNominalSubjectQuedarCon] está pinada aquí.

    @Test fun misPadresQuedaronConAnaElLunes_citaNominalAncla() =
        assertAnchors("mis padres quedaron con Ana el lunes", monday, "mis padres quedaron con Ana")

    @Test fun tuHermanaQuedoConMisPrimosElMartes_citaNominalAncla() =
        assertAnchors("tu hermana quedó con mis primos el martes", LocalDate.of(2026, 8, 25), "tu hermana quedó con mis primos")

    @Test fun miTiaQuedoConElDentistaElViernes_citaNominalAncla() =
        assertAnchors("mi tía quedó con el dentista el viernes", LocalDate.of(2026, 8, 28), "mi tía quedó con el dentista")

    @Test fun elPaqueteLlegoElLunes_narrativaNominalGuardSigueIntacta() =
        assertNarrativeIntact("el paquete llegó el lunes")

    @Test fun losResultadosLlegaronElMartes_narrativaNominalGuardSigueIntacta() =
        assertNarrativeIntact("los resultados llegaron el martes")

    @Test fun quedeConAnaAPrimeraHora_rutaOrdinalHermanaAncla() {
        val r = parse("quedé con Ana a primera hora")
        // ordinal «a primera hora» ancla hoy 09:00 (ruta historial c.1023 intacta; valor medido de la sonda)
        assertEquals(1787490000000L, r.dueAt)
        assertEquals("quedé con Ana", r.title)
    }

    @Test fun misPadresQuedaronConAnaElLunesPorLaTarde_citaConParteDelDiaAncla() {
        val r = parse("mis padres quedaron con Ana el lunes por la tarde")
        // parte del día «por la tarde» desplaza a 15:00 del weekday anclado (valor medido de la sonda)
        assertEquals(1787598000000L, r.dueAt)
        assertEquals("mis padres quedaron con Ana", r.title)
    }

    // ---- Regresiones (rutas hermanas intactas) ----

    @Test fun lunesLlegoElPaquete_rutaSufijoNarrativa() =
        assertNarrativeIntact("lunes llegó el paquete")

    @Test fun yaMeLoPagoElLunes_rutaYaNarrativa() =
        assertNarrativeIntact("ya me lo pagó el lunes")

    @Test fun llegueElMiercoles_rutaVerboPrimeroNarrativa() =
        assertNarrativeIntact("llegué el miércoles")

    @Test fun elDentistaElLunes_anclaReal() =
        assertAnchors("el dentista el lunes", monday, "el dentista")

    @Test fun comprarPanElLunes_anclaReal() =
        assertAnchors("comprar pan el lunes", monday, "comprar pan")
}
