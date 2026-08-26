package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1041 — lateral ABIERTA registrada en c.1039 con pin byte-idéntico
 * ([NaturalTaskParserYaPreteritoNarrativoCliticosMultiplesDeltaTest.yaMeLoPagoElLunes_weekdayFinalLateralFueraPin]):
 * weekday AL FINAL con la narrativa en pretérito EN EL PREFIJO («ya me lo
 * pagó el lunes»). La guard heredada [NaturalTaskParser.weekdayOccurrenceIsPreteriteNarrative]
 * (c.950) sólo mira el SUFIJO del weekday; con el predicado cerrado ANTES del
 * weekday la cadena narrativa no casaba → DOBLE daño P1 medido PRE con sonda
 * efímera `/tmp/probe1040/Probe.kt` (motor real, now=domingo 2026-08-23 12:00
 * America/Santo_Domingo): ancla FALSA (lunes+ 09:00 — relato de hecho
 * cumplido convertido en compromiso que ensucia What Now) y título MUTILADO
 * (sin su weekday).
 *
 * Fix (1 punto, re-uso sin duplicación — lección c.1016): ruta de PREFIJO en
 * la misma guard con la evidencia compartida de la familia
 * ([ordinalHoraPreteriteNarrativePrefixHead] c.1016/c.1035 + candados
 * [ordinalHoraQuedarConArrangement]/[ordinalHoraEmbeddedCommandToken] c.1023).
 * Mismo conservadurismo c.950: formas ambiguas pretérito/presente no
 * disparan; «quedar con» (prefijo O sufijo) sigue ancla; infinitivo/«que» en
 * el complemento bloquea (pins FUERA byte-idénticos abajo).
 *
 * Determinista (regex), cero random, cero IA fingida, cero UI.
 */
class NaturalTaskParserWeekdayFinalPreteritoNarrativoTest {

    private val zone = ZoneId.of("America/Santo_Domingo")
    // domingo 2026-08-23 12:00 (mismo now de la sonda PRE del ciclo)
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

    // ---- Capturas RED medidas (sonda PRE /tmp/probe1040/Probe.kt) ----

    @Test fun yaMeLoPagoElLunes_weekdayFinalEsContenidoNarrativo() =
        assertNarrativeIntact("ya me lo pagó el lunes")

    @Test fun yaSeLoDijeElMartes_weekdayFinalEsContenidoNarrativo() =
        assertNarrativeIntact("ya se lo dije el martes")

    @Test fun llegueElMiercoles_weekdayFinalSinYaEsContenidoNarrativo() =
        assertNarrativeIntact("llegué el miércoles")

    @Test fun yaNosLoTrajeronElJueves_weekdayFinalEsContenidoNarrativo() =
        assertNarrativeIntact("ya nos lo trajeron el jueves")

    @Test fun pagueLaLuzElViernes_weekdayFinalConComplementoEsContenidoNarrativo() =
        assertNarrativeIntact("pagué la luz el viernes")

    @Test fun llegasteElSabado_weekdayFinal2aPersonaEsContenidoNarrativo() =
        assertNarrativeIntact("llegaste el sábado")

    @Test fun yaMeTomeLaPastillaElDomingo_weekdayFinalEsContenidoNarrativo() =
        assertNarrativeIntact("ya me tomé la pastilla el domingo")

    @Test fun llegueElLunesAPrimeraHora_weekdayYOrdinalSonContenidoNarrativo() =
        // Bonus de la ruta prefijo: el ordinal de hora ya era contenido (H5
        // c.1023); sólo el weekday seguía anclando (medido PRE O3).
        assertNarrativeIntact("llegué el lunes a primera hora")

    @Test fun meQuedeEnCasaElLunes_quedarSinConEsContenidoNarrativo() =
        // «quedar» SIN «con»: narrativa («en casa» ≠ cita); medido PRE O5.
        assertNarrativeIntact("me quedé en casa el lunes")

    // ---- Guards ancla (byte-idénticos, medidos PRE) ----

    @Test fun pagarElLunes_comandoInfinitivoSigueAncla() =
        assertAnchors("pagar el lunes", monday, "pagar")

    @Test fun quedeConAnaElLunes_citaPrefijoSigueAncla() =
        assertAnchors("quedé con Ana el lunes", monday, "quedé con Ana")

    @Test fun quedeElLunesConAna_citaSufijoSigueAncla() =
        assertAnchors("quedé el lunes con Ana", monday, "quedé con Ana")

    @Test fun cobraLaLuzElLunes_presenteSigueAncla() =
        assertAnchors("cobra la luz el lunes", monday, "cobra la luz")

    @Test fun laReunionDelLunes_genitivoSigueAncla() =
        assertAnchors("la reunión del lunes", monday, "la reunión")

    @Test fun yaComimosElLunes_ambiguaSigueAncla() =
        // «comimos» 1ª plural: ambigua pretérito/presente (excluida c.950).
        assertAnchors("ya comimos el lunes", monday, "comimos")

    @Test fun elLunesQueVieneLlegue_modificadorFuturoGanaSigueAncla() =
        assertAnchors("el lunes que viene llegué", monday, "llegué")

    // ---- Regresiones hermanas (byte-idénticas, medidas PRE) ----

    @Test fun elLunesLlegoElPaquete_rutaSufijoIntacta() =
        assertNarrativeIntact("el lunes llegó el paquete")

    @Test fun elLunesYaMeLoPago_rutaSufijoDobleCliticoIntacta() =
        assertNarrativeIntact("el lunes ya me lo pagó")

    @Test fun avisarElLunes_comandoSigueAncla() =
        assertAnchors("avisar el lunes", monday, "avisar")

    @Test fun elLunesTengoReunion_presenteSigueAncla() =
        assertAnchors("el lunes tengo reunión", monday, "tengo reunión")

    // ---- Pines FUERA byte-idénticos (laterales registradas) ----

    @Test fun elPaqueteLlegoElLunes_prefijoConSujetoResueltoC1229() =
        // Re-pin c.1229 (precedente pin→resuelto c.1033/c.1035): el prefijo con
        // SUJETO nominal («el paquete llegó») ya se resuelve con la cabeza
        // [NaturalTaskParser.narrativeSubjectPrefixHead]; suite UNIÓN en
        // [NaturalTaskParserWeekdayFinalSubjectPrefixNarrativaTest].
        assertNarrativeIntact("el paquete llegó el lunes")

    @Test fun saliAComprarElLunes_infinitivoEmbebidoLateralFueraPin() =
        // Infinitivo en el complemento (candado conservador c.1023): sigue
        // ancla aunque la lectura humana es narrativa. Lateral FUERA registrada.
        assertAnchors("salí a comprar el lunes", monday, "salí a comprar")
}
