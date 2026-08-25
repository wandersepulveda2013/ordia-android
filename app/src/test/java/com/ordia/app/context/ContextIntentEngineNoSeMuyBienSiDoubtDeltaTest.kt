package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * c.1072: lateral ABIERTA documentada en c.1069/c.1070 — duda intercalada
 * «no sé muy bien si + infinitivo» (el «muy bien» entre «sé» y «si» rompía
 * el marcador c.1069) capturada como compromiso firme. Medición PRE con
 * sonda efímera `/tmp/probe1072/Probe.kt` (motor real vía
 * `tools/run_probe.sh`, HEAD c.1070 `ba75c84` + marcador `753b28b`):
 * 7 capturas medidas (misma clase P1/P2 que c.649/c.1069/c.1070: duda
 * explícita persistida como tarea real — bandeja degradada con items no
 * validados):
 *
 *   «no sé muy bien si llamar a mamá»        → CALL 0.57 firme
 *   «no se muy bien si llamar a mama»        → CALL 0.57 (sin tilde)
 *   «no sé muy bien si ir al médico»         → APPOINTMENT 0.67
 *   «no sé muy bien si sacar al perro»       → HOUSEHOLD 0.45
 *   «no sé muy bien si llamar a mamá mañana» → CALL 0.67 ¡con dueAt!
 *   «no sé muy bien si debería llamar a mamá»   → CALL 0.57 (combinación
 *      con el modal c.1070)
 *   «no sé muy bien si podría ir al médico»     → APPOINTMENT 0.67
 *
 * Fix mínimo (UN punto, mismo sitio que c.1069/c.1070): el marcador
 * «no sé si» en [HEDGE_PATTERN] admite el intercalado opcional «muy bien»
 * («no\s+s[ée]\s+(?:muy\s+bien\s+)?si»). El lookahead de infinitivo
 * (c.1069) y el modal opcional (c.1070) se reutilizan sin tocar. La
 * penalización post-pisos existente ([HEDGE_PENALTY] = 0.3) descarta toda
 * la forma medida (0.45..0.67 − 0.3 < [MINIMUM_CONFIDENCE]). CERO keywords
 * nuevas (lección c.751). Penaliza, no bloquea (doctrina c.649).
 *
 * Anti-overreach medido (guards verdes desde RED):
 *  - «sé muy bien que tengo que llamar a mamá» → TASK 0.45 intacto (sin
 *    «no» — el marcador exige la negación).
 *  - «no sé muy bien si es buena idea, llamar a mamá» → CALL 0.57 intacto
 *    (la duda gobierna «es buena idea», no el imperativo tras la coma;
 *    cierre posicional hermano de c.650/c.1069).
 *  - «no sé muy bien si debería, llamar a mamá» → CALL 0.57 intacto (la
 *    coma cierra; la duda gobierna el modal solo — hermano c.1070).
 *  - «no sé muy bien si ella llamó ayer» (pretérito gobernado) → NULL
 *    PRE/POST (no casa piso alguno).
 *  - «no sé muy bien si comprar pan» / «…pagar la luz» → NULL PRE/POST.
 *  - Pins c.1069/c.1070 intactos («no sé si llamar a mamá» NULL, «no sé si
 *    debería llamar a mamá» NULL, «no sé si es buena idea, llamar a mamá»
 *    CALL 0.57 fiel).
 *
 * Residual medido y aceptado (doctrina de la familia c.649/c.1069/c.1070):
 *  - «no sé muy bien si ir al médico mañana a las 9» → APPOINTMENT
 *    0.85 − 0.3 = 0.55 ≥ umbral (sobrevive con confianza reducida).
 *
 * Variantes FUERA (laterales hermanas, UNA por ciclo): «no sé si llamaré…»
 * (futuro → CALL 0.53 medido c.1070), «no sabemos si…» (plural → CALL 0.57
 * medido c.1070).
 *
 * Determinista (regex), cero random, cero IA fingida, cero UI.
 */
class ContextIntentEngineNoSeMuyBienSiDoubtDeltaTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- P1/P2: duda «no sé muy bien si + infinitivo» → NULL (descartada) ----

    @Test
    fun `no se muy bien si llamar a mama queda descartado`() {
        assertNull(analyze("no sé muy bien si llamar a mamá"))
    }

    @Test
    fun `no se muy bien si sin tilde llamar a mama queda descartado`() {
        assertNull(analyze("no se muy bien si llamar a mama"))
    }

    @Test
    fun `no se muy bien si ir al medico queda descartado`() {
        assertNull(analyze("no sé muy bien si ir al médico"))
    }

    @Test
    fun `no se muy bien si sacar al perro queda descartado`() {
        assertNull(analyze("no sé muy bien si sacar al perro"))
    }

    @Test
    fun `no se muy bien si llamar a mama manana queda descartado`() {
        assertNull(analyze("no sé muy bien si llamar a mamá mañana"))
    }

    @Test
    fun `no se muy bien si deberia llamar a mama queda descartado`() {
        assertNull(analyze("no sé muy bien si debería llamar a mamá"))
    }

    @Test
    fun `no se muy bien si podria ir al medico queda descartado`() {
        assertNull(analyze("no sé muy bien si podría ir al médico"))
    }

    // ---- Residual aceptado (doctrina c.649/c.1069/c.1070): temporal fuerte sobrevive ----

    @Test
    fun `no se muy bien si ir al medico manana a las 9 sobrevive con confianza reducida`() {
        val intent = analyze("no sé muy bien si ir al médico mañana a las 9")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
        assertTrue(intent.confidence >= 0.45f)
    }

    // ---- Guards / pins (verdes desde RED, NO deben cambiar) ----

    @Test
    fun `no se muy bien si comprar pan sigue descartado`() {
        assertNull(analyze("no sé muy bien si comprar pan"))
    }

    @Test
    fun `no se muy bien si pagar la luz sigue descartado`() {
        assertNull(analyze("no sé muy bien si pagar la luz"))
    }

    @Test
    fun `no se muy bien si ella llamo ayer sigue descartado`() {
        assertNull(analyze("no sé muy bien si ella llamó ayer"))
    }

    @Test
    fun `no se muy bien si es buena idea llamar a mama sigue fiel`() {
        val intent = analyze("no sé muy bien si es buena idea, llamar a mamá")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.CALL, intent!!.kind)
    }

    @Test
    fun `no se muy bien si deberia coma llamar a mama sigue fiel`() {
        val intent = analyze("no sé muy bien si debería, llamar a mamá")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.CALL, intent!!.kind)
    }

    @Test
    fun `se muy bien que tengo que llamar a mama sigue fiel`() {
        val intent = analyze("sé muy bien que tengo que llamar a mamá")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `pin c1069 no se si llamar a mama sigue descartado`() {
        assertNull(analyze("no sé si llamar a mamá"))
    }

    @Test
    fun `pin c1070 no se si deberia llamar a mama sigue descartado`() {
        assertNull(analyze("no sé si debería llamar a mamá"))
    }

    @Test
    fun `pin c1069 no se si es buena idea llamar a mama sigue fiel`() {
        val intent = analyze("no sé si es buena idea, llamar a mamá")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.CALL, intent!!.kind)
    }

    @Test
    fun `llamar a mama afirmativo sigue fiel`() {
        val intent = analyze("llamar a mamá")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.CALL, intent!!.kind)
    }

    @Test
    fun `tengo que llamar a mama sigue fiel`() {
        val intent = analyze("tengo que llamar a mamá")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `avisame manana de la reunion sigue fiel`() {
        val intent = analyze("avísame mañana de la reunión")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.REMINDER, intent!!.kind)
    }

    @Test
    fun `quiza llamar a mama sigue descartado`() {
        assertNull(analyze("quizá llamar a mamá"))
    }
}
