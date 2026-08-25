package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1035 — DELTA residual de la lateral «clíticos múltiples» guard c.1027
 * tras la colisión convergente con SU c.1033 (`b2d6611d`, {0,2} en
 * [NaturalTaskParser.yaPreteriteNarrativeSuffix]) y SU c.1034 (`75be2375`,
 * verbos). Medida PRE con sonda efímera `/tmp/probe1035/Probe.kt` contra la
 * UNIÓN `3e036371` (motor real, now=domingo 2026-08-23 12:00
 * America/Santo_Domingo):
 *  - O1/O2 (ordinal narrativo con doble clítico): SU fix no tocaba
 *    [NaturalTaskParser.ordinalHoraPreteriteNarrativeLonePrefix] ni
 *    [NaturalTaskParser.ordinalHoraPreteriteNarrativePrefixHead] → «ya se lo
 *    dije a última hora» nacía con due=18:00 FALSO y título mutilado
 *    («ya se lo dije», sin su marca temporal narrativa) — doble daño P1.
 *  - C4/C5 (acusativos plurales «las»/«los»): la lista proclítica heredada
 *    c.950 no los incluía → «ya se las di» / «ya los vi» anclaban AHORA con
 *    título mutilado aun con SU {0,2}.
 *  - C1–C3: superficies doble clítico disjuntas ya cubiertas por SU {0,2}
 *    (pins de cobertura, verdes desde RED).
 *  - G1–G4: guards ancla byte-idénticos (presente/futuro/comando sufijo).
 *
 * Fix (1 punto, consistente con SU estilo): grupo proclítico de los cuatro
 * regex narrativos de la familia c.950 → {0,2} con la lista completada
 * (me|te|se|nos|os|lo|la|los|las|le|les). [NaturalTaskParser.ordinalHoraQuedarConArrangement]
 * («quedar con» = compromiso) intacto a propósito.
 *
 * Determinista (regex), cero random, cero IA fingida, cero UI.
 */
class NaturalTaskParserYaPreteritoNarrativoCliticosMultiplesDeltaTest {

    private val zone = ZoneId.of("America/Santo_Domingo")
    // domingo 2026-08-23 12:00 (mismo now de la sonda del ciclo)
    private val now = DateRules.toEpochMillis(LocalDate.of(2026, 8, 23), LocalTime.NOON, zone)

    private fun parse(text: String) = NaturalTaskParser.parse(text, now, zone)

    private fun assertNarrativeIntact(text: String) {
        val r = parse(text)
        assertNull("sin ancla falsa: $text", r.dueAt)
        assertEquals("título intacto: $text", text, r.title)
    }

    private fun assertAnchorNow(text: String, expectedTitle: String) {
        val r = parse(text)
        assertEquals(now, r.dueAt)
        assertEquals(expectedTitle, r.title)
    }

    // ---- Capturas RED medidas (sonda PRE sobre la UNIÓN 3e036371) ----

    @Test fun yaSeLoDijeAUltimaHora_esContenidoNarrativo() =
        assertNarrativeIntact("ya se lo dije a última hora")

    @Test fun yaMeLoCompreALaPrimeraHora_esContenidoNarrativo() =
        assertNarrativeIntact("ya me lo compré a la primera hora")

    @Test fun yaSeLasDi_acusativoPluralEsContenidoNarrativo() =
        assertNarrativeIntact("ya se las di")

    @Test fun yaLosVi_acusativoPluralSoloEsContenidoNarrativo() =
        assertNarrativeIntact("ya los vi")

    // ---- Pins de cobertura SU c.1033 (verdes desde RED, superficies disjuntas) ----

    @Test fun yaTeLoEnvie_esContenidoNarrativo() =
        assertNarrativeIntact("ya te lo envié")

    @Test fun yaNosLoTrajeron_esContenidoNarrativo() =
        assertNarrativeIntact("ya nos lo trajeron")

    @Test fun yaMeLaTrajo_esContenidoNarrativo() =
        assertNarrativeIntact("ya me la trajo")

    // ---- Weekday: bonus de la extensión (sufijo) + lateral residual (final) ----

    @Test fun elLunesYaMeLoPago_weekdaySufijoEsContenidoNarrativo() =
        // PRE (UNIÓN 3e036371): due=lunes 09:00 falso + título sin «el lunes».
        assertNarrativeIntact("el lunes ya me lo pagó")

    @Test fun yaMeLoPagoElLunes_weekdayFinalLateralFueraPin() =
        // Weekday AL FINAL con narrativa en prefijo: FUERA (otra ruta de
        // guard); lateral registrada ABIERTA en BACKLOG (pin byte-idéntico).
        parse("ya me lo pagó el lunes").let { r ->
            assertEquals(
                DateRules.toEpochMillis(LocalDate.of(2026, 8, 24), LocalTime.of(9, 0), zone),
                r.dueAt
            )
            assertEquals("ya me lo pagó", r.title)
        }

    // ---- Guards ancla (byte-idénticos): presente/futuro/comando ----

    @Test fun yaLoHago_presenteSigueAncla() =
        assertAnchorNow("ya lo hago", "lo hago")

    @Test fun yaMeLoDira_futuroSigueAncla() =
        assertAnchorNow("ya me lo dirá", "me lo dirá")

    @Test fun diseloYa_comandoSufijoSigueAncla() =
        assertAnchorNow("díselo ya", "díselo")

    @Test fun yaMeLoMerezco_presenteSigueAncla() =
        assertAnchorNow("ya me lo merezco", "me lo merezco")
}
