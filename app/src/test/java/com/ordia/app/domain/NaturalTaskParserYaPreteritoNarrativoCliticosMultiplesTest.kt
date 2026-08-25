package com.ordia.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1029: lateral «clíticos múltiples» de la guard narrativa c.1027
 * [NaturalTaskParser.yaPreteriteNarrativeSuffix] — «ya me lo pagó»,
 * «ya se lo dije», «ya nos lo confirmaron». La guard c.1027 admite UN
 * solo clítico opcional; la cadena proclítica estándar del español
 * llega a DOS (indirecto + directo: «me lo», «se lo», «nos las»).
 * Medida PRE con sonda efímera `/tmp/probe1029/Probe.kt` (motor real
 * vía `tools/run_probe.sh`, now=domingo 2026-08-23 12:00
 * America/Santo_Domingo, HEAD `25cce920` [mi re-pin c.1028, PUSHED]):
 * 6/6 candidatas con DOBLE daño P1 — ancla AHORA FALSA (relato de un
 * hecho cumplido convertido en compromiso que vence hoy y ensucia
 * What Now) y título MUTILADO («me lo pagó» sin su «ya»). Regresiones
 * correctas: 1 clítico intacto (c.1027), comandos «avisar ya» /
 * «ya mismo» anclan, ambigua «ya salimos» ancla (conservadora).
 * Fix mínimo (1 punto): el grupo de clítico opcional de
 * [NaturalTaskParser.yaPreteriteNarrativeSuffix] pasa de `?` a `{0,2}`
 * — misma lista cerrada de c.950, mismo conservadurismo (las formas
 * ambiguas pretérito/presente siguen ancla; sólo el «ya» suelto se
 * evalúa). Determinista (regex), cero random, cero IA fingida, cero UI.
 */
class NaturalTaskParserYaPreteritoNarrativoCliticosMultiplesTest {

    private val zone: ZoneId = ZoneId.of("America/Santo_Domingo")
    private val domingo: LocalDate = LocalDate.of(2026, 8, 23)
    private val now: Long = domingo.atTime(LocalTime.NOON).atZone(zone).toInstant().toEpochMilli()

    private fun assertNarrativeIntact(text: String) {
        val r = NaturalTaskParser.parse(text, now, zone)
        assertNull("sin ancla falsa: $text", r.dueAt)
        assertEquals("título intacto: $text", text, r.title)
    }

    // ---------- candidatas: 2 clíticos + pretérito inequívoco ----------
    // (verbos de la lista cerrada c.950; la cobertura de verbos — «devolvió»,
    // «confirmaron», «mandé» no están en la lista — es otra lateral FUERA)

    @Test fun yaMeLoPago() = assertNarrativeIntact("ya me lo pagó")
    @Test fun yaSeLoDije() = assertNarrativeIntact("ya se lo dije")
    @Test fun yaMeLoEnviaron() = assertNarrativeIntact("ya me lo enviaron")
    @Test fun yaSeLoCompre() = assertNarrativeIntact("ya se lo compré")
    @Test fun yaNosLoEscribieron() = assertNarrativeIntact("ya nos lo escribieron")
    @Test fun yaSeLoTermine() = assertNarrativeIntact("ya se lo terminé")

    // ---------- regresiones c.1027 (1 clítico): narrativa intacta ----------

    @Test fun regresionYaMeTomeLaPastilla() = assertNarrativeIntact("ya me tomé la pastilla")
    @Test fun regresionYaLoPague() = assertNarrativeIntact("ya lo pagué")
    @Test fun regresionYaSonoLaAlarma() = assertNarrativeIntact("ya sonó la alarma")

    // ---------- regresiones comando «ya»: ancla AHORA legítima ----------

    @Test fun comandoAvisarYa() {
        val r = NaturalTaskParser.parse("avisar ya", now, zone)
        assertEquals(now, r.dueAt)
        assertEquals("avisar", r.title)
    }

    @Test fun comandoYaMismo() {
        val r = NaturalTaskParser.parse("llamar al banco ya mismo", now, zone)
        assertEquals(now, r.dueAt)
        assertEquals("llamar al banco", r.title)
    }

    // ---------- control ambiguo (pretérito/presente): sigue ancla ----------

    @Test fun ambiguaYaSalimosSigueAncla() {
        val r = NaturalTaskParser.parse("ya salimos", now, zone)
        assertEquals(now, r.dueAt)
        assertEquals("salimos", r.title)
    }
}
