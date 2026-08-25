package com.ordia.app.domain

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins de la lateral residual «ahora/ahorita + pretérito» narrativa
 * (registrada en c.1028 como FUERA de la guard c.1027, que sólo cubría
 * «ya»), RESUELTA en c.1037.
 *
 * «ahora llegó el cartero» / «ahorita me llamó el médico» son acabativos:
 * relato de un hecho RECIÉN cumplido, no un encargo. La guard c.1027 sólo
 * evaluaba la ocurrencia «ya»; con «ahora»/«ahorita» + pretérito inequívoco
 * el ancla AHORA disparaba igual → DOBLE daño: ancla AHORA FALSA (relato
 * convertido en compromiso que vence hoy, ensucia What Now) y título
 * MUTILADO (sin su «ahora»).
 *
 * Medida PRE con sonda efímera /tmp/probe1034/Probe4.kt (motor real,
 * now=domingo 2026-08-23 12:00 America/Santo_Domingo): 5/5 candidatas con
 * ancla falsa + título mutilado; comandos en presente/imperativo/infinitivo
 * («ahora llamo al banco», «hazlo ahora») anclan correctamente.
 *
 * Fix mínimo (1 punto): la guard del ancla AHORA evalúa también las
 * ocurrencias «ahora»/«ahorita» con la MISMA regex de sufijo narrativo
 * (sin duplicarla ni arriesgar deriva — doctrina c.1016). Mismo
 * conservadurismo c.950: un encargo real jamás abre su predicado en
 * pretérito; «ahora mismo» (frase completa) y las ambiguas no se tocan.
 */
class NaturalTaskParserAhoraPreteritoNarrativoTest {

    private val zone: ZoneId = ZoneId.of("America/Santo_Domingo")
    private val now: Long =
        LocalDateTime.of(2026, 8, 23, 12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun assertNarrativeIntact(input: String) {
        val result = NaturalTaskParser.parse(input, now, zone)
        assertNull("«$input» no debe tener fecha (es relato, no compromiso)", result.dueAt)
        assertEquals("«$input» debe conservar el título íntegro", input, result.title)
    }

    private fun assertAnchorNow(input: String, expectedTitle: String) {
        val result = NaturalTaskParser.parse(input, now, zone)
        assertEquals("«$input» es un encargo inmediato y debe vencer ahora", now, result.dueAt)
        assertEquals(expectedTitle, result.title)
    }

    // ---------- candidatas: «ahora/ahorita» + pretérito inequívoco (acabativo) ----------

    @Test
    fun ahoraLlegoElCartero() = assertNarrativeIntact("ahora llegó el cartero")

    @Test
    fun ahoraMePago() = assertNarrativeIntact("ahora me pagó")

    @Test
    fun ahoraSonoLaAlarma() = assertNarrativeIntact("ahora sonó la alarma")

    @Test
    fun ahoritaMeLlamoElMedico() = assertNarrativeIntact("ahorita me llamó el médico")

    @Test
    fun ahoritaSeLoDije() = assertNarrativeIntact("ahorita se lo dije")

    // ---------- comandos en presente/imperativo/infinitivo: siguen anclando ----------

    @Test
    fun ahoraLlamoAlBanco_comandoSigueAncla() = assertAnchorNow("ahora llamo al banco", "llamo al banco")

    @Test
    fun ahoritaVoyAlBanco_comandoSigueAncla() = assertAnchorNow("ahorita voy al banco", "voy al banco")

    @Test
    fun ahoraMismoSalgo_fraseCompletaSigueAncla() = assertAnchorNow("ahora mismo salgo", "salgo")

    @Test
    fun hazloAhora_imperativoSigueAncla() = assertAnchorNow("hazlo ahora", "hazlo")

    @Test
    fun llamarAhorita_infinitivoSigueAncla() = assertAnchorNow("llamar ahorita", "llamar")

    // ---------- regresiones «ya» c.1027/c.1033: intactas ----------

    @Test
    fun yaSonoLaAlarma_regresionC1027() = assertNarrativeIntact("ya sonó la alarma")

    @Test
    fun yaMeLoPago_regresionC1033() = assertNarrativeIntact("ya me lo pagó")
}
