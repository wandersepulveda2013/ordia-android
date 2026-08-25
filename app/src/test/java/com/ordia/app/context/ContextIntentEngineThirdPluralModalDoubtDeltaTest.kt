package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * c.1080: lateral ABIERTA registrada en c.1078 (1/2) — duda «no
 * sabemos si + MODAL 3ª PERSONA PLURAL + infinitivo» («no sabemos si
 * deberían llamar a mamá») capturada como compromiso firme. Medición
 * PRE con sonda efímera `/tmp/probe1080/ThirdPluralModalPreProbe.kt`
 * (motor real vía `tools/run_probe.sh`, HEAD `8c57d7a` — con los fixes
 * c.1076/c.1078 ya aplicados): 7 capturas medidas (espejo EXACTO de
 * c.1078; misma clase P1/P2 que c.649/c.1069/c.1070/c.1072/c.1073/
 * c.1076/c.1078: duda explícita persistida como tarea real — bandeja
 * degradada con items no validados, y aquí aún peor: la acción dudosa
 * es de TERCEROS, no del usuario):
 *
 *   «no sabemos si deberían llamar a mamá»       → CALL 0.57 firme
 *   «no sabemos si deberían ir al médico»        → APPOINTMENT 0.67
 *      ¡título corrupto «No sabemos si deberían ir al médico»!
 *   «no sabemos si podrían sacar al perro»       → HOUSEHOLD 0.45
 *   «no sabemos si tendrían que pagar la luz»    → TASK 0.45 ¡título
 *      corrupto!
 *   «no sabemos muy bien si deberían llamar…»    → CALL 0.57
 *      (intercalado «muy bien», hermano de c.1072)
 *   «No sabemos si deberían llamar a mamá»       → CALL 0.57
 *      (mayúscula)
 *   «no sabemos si habrían que llamar a mamá»    → CALL 0.57
 *
 * El conjunto modal del lookahead (c.1070 singular, c.1078 1ª persona
 * plural «…mos») no admite la 3ª persona plural «…n».
 *
 * Fix mínimo (UN punto, mismo sitio que c.1070/c.1078): el conjunto
 * modal admite la 3ª persona plural `(?:mos|n)?`. El resto del patrón
 * (marcador c.1076, intercalado «muy bien» c.1072, infinitivo con
 * enclíticos, futuro 1ª persona c.1073) se reutiliza SIN TOCAR. La
 * penalización post-pisos existente ([HEDGE_PENALTY] = 0.3) descarta
 * toda la forma medida (0.45..0.67 − 0.3 < [MINIMUM_CONFIDENCE]).
 * CERO keywords nuevas (lección c.751). Penaliza, no bloquea
 * (doctrina c.649: la duda no niega la intención).
 *
 * Cambio de comportamiento aceptado (consistente con los pines c.1070
 * singular y c.1078 1ª plural): «no sabemos si deberían haber llamado
 * a mamá» (arrepentimiento pasado, infinitivo perfecto) voltea TASK
 * 0.45 → NULL (medido PRE).
 *
 * Anti-overreach medido (guards verdes desde RED):
 *  - «no sabemos si deberían, llamar a mamá» (la coma cierra; la
 *    duda gobierna el modal solo) → CALL 0.57 intacto.
 *  - «sabemos que deberían llamar a mamá» (sin «no») y «deberían
 *    llamar a mamá» (modal 3ª plural SIN duda) → CALL 0.57 intactos.
 *  - «no sabéis si deberíais llamar a mamá» (2ª persona plural) →
 *    CALL 0.57 intacto (FUERA — el marcador no admite «sabéis»;
 *    lateral ABIERTA siguiente 2/2, medida).
 *  - «no sabemos si llamaron a mamá ayer» (pretérito 3ª plural) →
 *    NULL estable PRE/POST (medido).
 *  - Pins de la familia intactos (c.1070 modal singular, c.1076
 *    plural infinitivo, c.1078 1ª plural modal).
 *  - Envolventes fieles intactas («tengo que llamar a mamá» TASK
 *    0.45, «recuérdame llamar a mamá» TASK 0.45) y compromiso directo
 *    («llamar a mamá» CALL 0.57).
 *
 * Residual medido y aceptado (doctrina de la familia): «no sabemos si
 * deberían ir al médico mañana a las 9» → APPOINTMENT 0.85 − 0.3 =
 * 0.55 ≥ umbral (sobrevive con confianza reducida; pin de
 * supervivencia ≥0.45 estilo c.1076, valor 0.55 pineado en la sonda
 * POST).
 *
 * Lateral ABIERTA registrada (UNA por ciclo, medida PRE): 2ª persona
 * plural «no sabéis si deberíais llamar a mamá» → CALL 0.57 firme
 * (el marcador no admite «sabéis»).
 *
 * RED exacto esperado: EXACTAMENTE 8 fallos (las 7 capturas + el
 * volteo aceptado «deberían haber llamado»). SIN re-pins.
 */
class ContextIntentEngineThirdPluralModalDoubtDeltaTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    private fun assertNullIntent(text: String) {
        assertNull("«$text» debería descartarse (duda ≠ compromiso)", analyze(text))
    }

    private fun assertKind(text: String, kind: ContextIntentKind) {
        val intent = analyze(text)
        assertNotNull("«$text» debería persistir", intent)
        assertEquals(kind, intent!!.kind)
    }

    // ── Capturas (7 medidas PRE) ────────────────────────────────────

    @Test
    fun `no sabemos si deberian llamar a mama se descarta`() =
        assertNullIntent("no sabemos si deberían llamar a mamá")

    @Test
    fun `no sabemos si deberian ir al medico se descarta`() =
        assertNullIntent("no sabemos si deberían ir al médico")

    @Test
    fun `no sabemos si podrian sacar al perro se descarta`() =
        assertNullIntent("no sabemos si podrían sacar al perro")

    @Test
    fun `no sabemos si tendrian que pagar la luz se descarta`() =
        assertNullIntent("no sabemos si tendrían que pagar la luz")

    @Test
    fun `no sabemos muy bien si deberian llamar a mama se descarta`() =
        assertNullIntent("no sabemos muy bien si deberían llamar a mamá")

    @Test
    fun `No sabemos si deberian llamar a mama mayuscula se descarta`() =
        assertNullIntent("No sabemos si deberían llamar a mamá")

    @Test
    fun `no sabemos si habrian que llamar a mama se descarta`() =
        assertNullIntent("no sabemos si habrían que llamar a mamá")

    // ── Volteo aceptado (consistente con pines c.1070/c.1078) ───────

    @Test
    fun `no sabemos si deberian haber llamado a mama se descarta`() =
        assertNullIntent("no sabemos si deberían haber llamado a mamá")

    // ── Residual aceptado (evidencia temporal fuerte) ───────────────

    @Test
    fun `no sabemos si deberian ir al medico manana a las 9 sobrevive con confianza reducida`() {
        val intent = analyze("no sabemos si deberían ir al médico mañana a las 9")
        assertNotNull("residual temporal fuerte debería sobrevivir", intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
        assertTrue(
            "residual 0.85−0.3=0.55 ≥ umbral: ${intent.confidence}",
            intent.confidence >= 0.45f
        )
    }

    // ── Guards fieles (verdes desde RED) ────────────────────────────

    @Test
    fun `no sabemos si deberian COMA llamar a mama persiste`() =
        assertKind("no sabemos si deberían, llamar a mamá", ContextIntentKind.CALL)

    @Test
    fun `sabemos que deberian llamar a mama persiste`() =
        assertKind("sabemos que deberían llamar a mamá", ContextIntentKind.CALL)

    @Test
    fun `deberian llamar a mama sin duda persiste`() =
        assertKind("deberían llamar a mamá", ContextIntentKind.CALL)

    @Test
    fun `no sabeis si deberiais llamar a mama segunda plural FUERA persiste`() =
        assertKind("no sabéis si deberíais llamar a mamá", ContextIntentKind.CALL)

    @Test
    fun `no sabemos si llamaron a mama ayer preterito NULL estable`() =
        assertNullIntent("no sabemos si llamaron a mamá ayer")

    // ── Pins de la familia (sin re-pins) ────────────────────────────

    @Test
    fun `pin c1070 no se si deberia llamar a mama se descarta`() =
        assertNullIntent("no sé si debería llamar a mamá")

    @Test
    fun `pin c1076 no sabemos si llamar a mama se descarta`() =
        assertNullIntent("no sabemos si llamar a mamá")

    @Test
    fun `pin c1078 no sabemos si deberiamos llamar a mama se descarta`() =
        assertNullIntent("no sabemos si deberíamos llamar a mamá")

    // ── Envolventes fieles y compromiso directo ─────────────────────

    @Test
    fun `tengo que llamar a mama persiste`() =
        assertKind("tengo que llamar a mamá", ContextIntentKind.TASK)

    @Test
    fun `recuerdame llamar a mama persiste`() =
        assertKind("recuérdame llamar a mamá", ContextIntentKind.TASK)

    @Test
    fun `llamar a mama compromiso directo persiste`() =
        assertKind("llamar a mamá", ContextIntentKind.CALL)
}
