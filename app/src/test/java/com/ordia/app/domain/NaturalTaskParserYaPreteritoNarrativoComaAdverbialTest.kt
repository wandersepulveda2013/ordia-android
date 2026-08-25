package com.ordia.app.domain

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins de la lateral «ya, <adverbial>, <pretérito>» (pin FUERA c.1027
 * `yaComaAPrimeraHoraSonoLaAlarma_lateralFueraPin`), RESUELTA en c.1035.
 *
 * La guard c.1027 (`yaPreteriteNarrativeSuffix`) admite UNA coma tras «ya»
 * pero exigía el pretérito ADYACENTE; con un adverbial intermedio entre
 * comas («a primera hora,», «sin querer,»…) no casaba → DOBLE daño P1:
 * ancla AHORA FALSA (relato de hecho cumplido convertido en compromiso que
 * vence hoy, ensucia What Now) y título MUTILADO (sin su «ya»).
 *
 * Medida PRE con sonda efímera /tmp/probe1034/Probe2.kt (motor real,
 * now=domingo 2026-08-23 12:00 America/Santo_Domingo): 5/5 candidatas con
 * ancla falsa + título mutilado; «ya, sonó la alarma» (pretérito adyacente
 * tras coma) YA funcionaba; comandos con adverbial anclan correctamente.
 *
 * Fix mínimo (1 punto): la guard admite UNA cláusula adverbial acotada
 * entre comas (`[^,.;:!?]{1,60},`) antes de los clíticos y el pretérito.
 * Mismo conservadurismo c.950: un encargo real jamás abre su predicado en
 * pretérito; las formas ambiguas no se tocan.
 *
 * Residual REGISTRADO (lateral distinta, pre-existente): cuando el
 * adverbial ES una expresión temporal reconocible («por la mañana») el
 * ancla temporal sigue disparando — mismo hueco que la narrativa sin coma
 * «ya me tomé la pastilla a las 8» (medida sonda Probe3). Fuera de alcance.
 */
class NaturalTaskParserYaPreteritoNarrativoComaAdverbialTest {

    private val zone: ZoneId = ZoneId.of("America/Santo_Domingo")
    private val now: Long =
        LocalDateTime.of(2026, 8, 23, 12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun assertNarrativeIntact(input: String) {
        val result = NaturalTaskParser.parse(input, now, zone)
        assertNull("«$input» no debe tener fecha (es relato, no compromiso)", result.dueAt)
        assertEquals("«$input» debe conservar el título íntegro", input, result.title)
    }

    private fun assertAnchored(input: String, expectedTitle: String) {
        val result = NaturalTaskParser.parse(input, now, zone)
        assertNotNull("«$input» es un encargo y debe anclar", result.dueAt)
        assertEquals(expectedTitle, result.title)
    }

    // ---------- candidatas: «ya,» + adverbial NO temporal + pretérito inequívoco ----------

    @Test
    fun yaComaAPrimeraHoraSonoLaAlarma_resueltaEnC1035() =
        assertNarrativeIntact("ya, a primera hora, sonó la alarma")

    @Test
    fun yaComaEnLaReunionLoConfirmaron() =
        assertNarrativeIntact("ya, en la reunión, lo confirmaron")

    @Test
    fun yaComaSinQuererLoRompió() =
        assertNarrativeIntact("ya, sin querer, lo rompió")

    @Test
    fun yaComaPorFinMePago() =
        assertNarrativeIntact("ya, por fin, me pagó")

    // ---------- comandos con adverbial: deben seguir anclando ----------

    @Test
    fun yaComaAPrimeraHoraRecogeLaRopa_comandoSigueAncla() =
        assertAnchored("ya, a primera hora, recoge la ropa", "recoge la ropa")

    @Test
    fun yaComaPorFavorLlamaAlBanco_comandoSigueAncla() =
        assertAnchored("ya, por favor, llama al banco", "por favor, llama al banco")

    // ---------- regresiones c.1027/c.1033: intactas ----------

    @Test
    fun yaComaSonoLaAlarma_pretéritoAdyacenteYaCubierto() =
        assertNarrativeIntact("ya, sonó la alarma")

    @Test
    fun yaMeLoPago_regresionC1033() =
        assertNarrativeIntact("ya me lo pagó")

    @Test
    fun avisarYa_encargoInmediatoSigueAncla() =
        assertAnchored("avisar ya", "avisar")

    @Test
    fun yaSalimos_formaAmbiguaSigueAncla() =
        assertAnchored("ya salimos", "salimos")

    // ---------- residual REGISTRADO: adverbial que ES expresión temporal ----------

    @Test
    fun yaComaPorLaMananaLlegoElCartero_lateralResidualFueraPin() {
        // «por la mañana» SÍ es ancla temporal reconocible: sigue disparando
        // (mismo hueco pre-existente que «ya me tomé la pastilla a las 8»).
        // Pin del comportamiento actual; lateral distinta registrada en BACKLOG.
        val result = NaturalTaskParser.parse("ya, por la mañana, llegó el cartero", now, zone)
        assertNotNull(result.dueAt)
    }
}
