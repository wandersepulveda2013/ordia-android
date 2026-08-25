package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * c.1078: lateral ABIERTA registrada en c.1076 — duda «no sabemos si +
 * MODAL PLURAL + infinitivo» («no sabemos si deberíamos llamar a
 * mamá») capturada como compromiso firme. Medición PRE con sonda
 * efímera `/tmp/probe1078/ModalPluralPreProbe.kt` (motor real vía
 * `tools/run_probe.sh`, HEAD `01d9e0e` — con el fix c.1076 ya
 * aplicado): 7 capturas medidas (misma clase P1/P2 que c.649/c.1069/
 * c.1070/c.1072/c.1073/c.1076: duda explícita persistida como tarea
 * real — bandeja degradada con items no validados):
 *
 *   «no sabemos si deberíamos llamar a mamá»     → CALL 0.57 firme
 *   «no sabemos si deberíamos ir al médico»      → APPOINTMENT 0.67
 *      ¡título corrupto «No sabemos si deberíamos ir al médico»!
 *   «no sabemos si podríamos sacar al perro»     → HOUSEHOLD 0.45
 *   «no sabemos si tendríamos que pagar la luz»  → TASK 0.45 ¡título
 *      corrupto!
 *   «no sabemos muy bien si deberíamos llamar…»  → CALL 0.57
 *      (intercalado «muy bien», hermano de c.1072)
 *   «No sabemos si deberíamos llamar a mamá»     → CALL 0.57
 *      (mayúscula)
 *   «no sabemos si habríamos que llamar a mamá»  → CALL 0.57
 *
 * El marcador plural de c.1076 ya casa; el que rompe es el MODAL
 * condicional SINGULAR del lookahead de c.1070 («debería/podría/
 * tendría que/habría que»), que no admite la 1ª persona plural
 * («deberíamos/podríamos/tendríamos que/habríamos que»).
 *
 * Fix mínimo (UN punto, mismo sitio que c.1070): el conjunto modal
 * del lookahead admite el plural condicional `(?:mos)?`. El resto del
 * patrón (marcador, intercalado «muy bien», infinitivo con
 * enclíticos, futuro 1ª persona) se reutiliza SIN TOCAR. La
 * penalización post-pisos existente ([HEDGE_PENALTY] = 0.3) descarta
 * toda la forma medida (0.45..0.67 − 0.3 < [MINIMUM_CONFIDENCE]).
 * CERO keywords nuevas (lección c.751). Penaliza, no bloquea
 * (doctrina c.649: la duda no niega la intención).
 *
 * Cambio de comportamiento aceptado (consistente con el pin singular
 * de c.1070 «no sé si debería haber llamado a mamá» — arrepentimiento
 * pasado correctamente descartado): «no sabemos si deberíamos haber
 * llamado a mamá» voltea TASK 0.45 → NULL (medido PRE).
 *
 * Anti-overreach medido (guards verdes desde RED):
 *  - «no sabemos si deberíamos, llamar a mamá» (la coma cierra; la
 *    duda gobierna el modal solo) → CALL 0.57 intacto (pin hermano
 *    del singular c.1070).
 *  - «sabemos que deberíamos llamar a mamá» (sin «no») y
 *    «deberíamos llamar a mamá» (modal plural SIN duda) → CALL 0.57
 *    intactos.
 *  - «no sabemos si deberían llamar a mamá» (3ª persona plural) →
 *    CALL 0.57 intacto EN ESTE CICLO (FUERA — lateral ABIERTA
 *    siguiente, medida; RESUELTA en c.1080: el pin abajo volteó a
 *    NULL, documentado).
 *  - «no sabemos si podemos llamar a mamá» (presente plural) → NULL
 *    estable PRE/POST (medido).
 *  - Pins de la familia intactos (c.1070 modal singular, c.1076
 *    plural infinitivo).
 *  - Envolventes fieles intactas («tengo que llamar a mamá» TASK
 *    0.45, «recuérdame llamar a mamá» TASK 0.45) y compromiso directo
 *    («llamar a mamá» CALL 0.57).
 *
 * Residual medido y aceptado (doctrina de la familia): «no sabemos si
 * deberíamos ir al médico mañana a las 9» → APPOINTMENT 0.85 − 0.3 =
 * 0.55 ≥ umbral (sobrevive con confianza reducida).
 *
 * Laterales ABIERTAS registradas (UNA por ciclo, medidas PRE):
 *  - 3ª persona plural modal «no sabemos si deberían llamar a mamá»
 *    → CALL 0.57 firme. → RESUELTA en c.1080 (este pin volteó a
 *    NULL con documentación).
 *  - 2ª persona plural «no sabéis si deberíais llamar a mamá» → CALL
 *    0.57 firme (el marcador no admite «sabéis»).
 *
 * RED exacto esperado: EXACTAMENTE 8 fallos (las 7 capturas + el
 * volteo aceptado «deberíamos haber llamado»). SIN re-pins.
 */
class ContextIntentEngineModalPluralDoubtDeltaTest {

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
    fun `no sabemos si deberiamos llamar a mama se descarta`() =
        assertNullIntent("no sabemos si deberíamos llamar a mamá")

    @Test
    fun `no sabemos si deberiamos ir al medico se descarta`() =
        assertNullIntent("no sabemos si deberíamos ir al médico")

    @Test
    fun `no sabemos si podriamos sacar al perro se descarta`() =
        assertNullIntent("no sabemos si podríamos sacar al perro")

    @Test
    fun `no sabemos si tendriamos que pagar la luz se descarta`() =
        assertNullIntent("no sabemos si tendríamos que pagar la luz")

    @Test
    fun `no sabemos muy bien si deberiamos llamar a mama se descarta`() =
        assertNullIntent("no sabemos muy bien si deberíamos llamar a mamá")

    @Test
    fun `No sabemos si deberiamos llamar a mama mayuscula se descarta`() =
        assertNullIntent("No sabemos si deberíamos llamar a mamá")

    @Test
    fun `no sabemos si habriamos que llamar a mama se descarta`() =
        assertNullIntent("no sabemos si habríamos que llamar a mamá")

    // ── Volteo aceptado (consistente con pin singular c.1070) ───────

    @Test
    fun `no sabemos si deberiamos haber llamado a mama se descarta`() =
        assertNullIntent("no sabemos si deberíamos haber llamado a mamá")

    // ── Residual aceptado (evidencia temporal fuerte) ───────────────

    @Test
    fun `no sabemos si deberiamos ir al medico manana a las 9 sobrevive con confianza reducida`() {
        val intent = analyze("no sabemos si deberíamos ir al médico mañana a las 9")
        assertNotNull("residual temporal fuerte debería sobrevivir", intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
        assertTrue(
            "residual 0.85−0.3=0.55 ≥ umbral: ${intent.confidence}",
            intent.confidence >= 0.45f
        )
    }

    // ── Guards fieles (verdes desde RED) ────────────────────────────

    @Test
    fun `no sabemos si deberiamos COMA llamar a mama persiste`() =
        assertKind("no sabemos si deberíamos, llamar a mamá", ContextIntentKind.CALL)

    @Test
    fun `sabemos que deberiamos llamar a mama persiste`() =
        assertKind("sabemos que deberíamos llamar a mamá", ContextIntentKind.CALL)

    @Test
    fun `deberiamos llamar a mama sin duda persiste`() =
        assertKind("deberíamos llamar a mamá", ContextIntentKind.CALL)

    // Pin VOLTEADO por c.1080: era «persiste» (comportamiento PRE del
    // lateral ABIERTA que c.1080 resolvió). La resolución está cubierta
    // además por ContextIntentEngineThirdPluralModalDoubtDeltaTest.
    @Test
    fun `no sabemos si deberian llamar a mama tercera plural se descarta c1080`() =
        assertNullIntent("no sabemos si deberían llamar a mamá")

    @Test
    fun `no sabemos si podemos llamar a mama presente plural NULL estable`() =
        assertNullIntent("no sabemos si podemos llamar a mamá")

    // ── Pins de la familia (sin re-pins) ────────────────────────────

    @Test
    fun `pin c1070 no se si deberia llamar a mama se descarta`() =
        assertNullIntent("no sé si debería llamar a mamá")

    @Test
    fun `pin c1070 no se si deberia ir al medico se descarta`() =
        assertNullIntent("no sé si debería ir al médico")

    @Test
    fun `pin c1076 no sabemos si llamar a mama se descarta`() =
        assertNullIntent("no sabemos si llamar a mamá")

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
