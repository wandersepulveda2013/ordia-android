package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * c.1070: lateral ABIERTA documentada en c.1069 — duda «no sé si + MODAL +
 * infinitivo» (el modal intercalado «debería/podría/tendría que/habría que»
 * rompía el lookahead de infinitivo del marcador c.1069) capturada como
 * compromiso firme. Medición PRE con sondas efímeras
 * `/tmp/probe1070/Probe{,2}.kt` (motor real vía `tools/run_probe.sh`,
 * HEAD c.1069 `3df0981` + marcador `369daed`): 12 capturas medidas
 * (misma clase P1/P2 que c.649/c.1069: duda explícita persistida como
 * tarea real — bandeja degradada con items no validados):
 *
 *   «no sé si debería llamar a mamá»     → CALL 0.57 firme
 *   «no se si deberia llamar a mama»     → CALL 0.57 (sin tilde)
 *   «no sé si debería ir al médico»      → APPOINTMENT 0.67
 *   «no sé si podría llamar a mamá»      → CALL 0.57
 *   «no sé si debería pagar la luz»      → TASK 0.45
 *   «no sé si debería sacar al perro»    → TASK 0.45
 *   «no sé si tendría que llamar a mamá» → CALL 0.57
 *   «no sé si habría que llamar a mamá»  → CALL 0.57
 *   «no sé si debería que llamar a mamá» → CALL 0.57
 *   «no sé si debería llamarle mañana»   → TASK 0.45 (enclítico)
 *   «no sé si debería haber llamado a mamá» → TASK 0.45 (arrepentimiento
 *      pasado — «haber llamado» es infinitivo perfecto; tampoco es un
 *      compromiso futuro: correcto descartarlo)
 *   «no sé si debería llamar a mamá mañana» → CALL 0.67 ¡con dueAt!
 *
 * Fix mínimo (UN punto, mismo sitio que c.1069): el lookahead del marcador
 * «no sé si» en [HEDGE_PATTERN] admite un MODAL opcional
 * («debería (que)», «podría», «tendría que», «habría que» — mismo conjunto
 * que OBLIGATION_MODAL_SPAN c.1068 + «podría») antes del infinitivo
 * español. La penalización post-pisos existente ([HEDGE_PENALTY] = 0.3)
 * descarta toda la forma medida (0.45..0.67 − 0.3 < [MINIMUM_CONFIDENCE]).
 * CERO keywords nuevas (lección c.751). Penaliza, no bloquea (doctrina
 * c.649): la duda no niega la intención.
 *
 * Anti-overreach medido (guards verdes desde RED):
 *  - «no sé si debería, llamar a mamá» → CALL 0.57 intacto: la duda
 *    gobierna el modal SOLO (la coma cierra); el infinitivo tras la coma
 *    es recomendación real — cierre posicional hermano de c.650/c.1069.
 *  - «debería/podría/tendría que/habría que llamar a mamá» SIN duda →
 *    CALL 0.57 intactos (captura fiel).
 *  - «quizá debería llamar a mamá» → NULL intacto (hedge c.649 ya casa).
 *  - «no sé si podría comprar pan» / «no sé si podría irme al médico» →
 *    NULL PRE/POST (ya invisibles).
 *  - Pins c.1069 intactos («no sé si llamar a mamá» NULL, «no sé si es
 *    buena idea, llamar a mamá» CALL 0.57 fiel).
 *
 * Residual medido y aceptado (doctrina de la familia c.649/c.1069 — la
 * evidencia temporal fuerte sobrevive a la penalización):
 *  - «no sé si debería ir al médico mañana a las 9» → APPOINTMENT
 *    0.85 − 0.3 = 0.55 ≥ umbral (sobrevive con confianza reducida).
 *
 * Variantes FUERA (laterales hermanas, UNA por ciclo): «no sé si
 * llamaré…» (futuro → CALL 0.53 medido), «no sabemos si…» (plural →
 * CALL 0.57 medido), «no sé muy bien si…» (intercalado → CALL 0.57
 * medido).
 *
 * Determinista (regex), cero random, cero IA fingida, cero UI.
 */
class ContextIntentEngineNoSeSiModalDoubtDeltaTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- P1/P2: duda «no sé si + modal + infinitivo» → NULL (descartada) ----

    @Test
    fun `no se si deberia llamar a mama queda descartado`() {
        assertNull(analyze("no sé si debería llamar a mamá"))
    }

    @Test
    fun `no se si deberia sin tilde llamar a mama queda descartado`() {
        assertNull(analyze("no se si deberia llamar a mama"))
    }

    @Test
    fun `no se si deberia ir al medico queda descartado`() {
        assertNull(analyze("no sé si debería ir al médico"))
    }

    @Test
    fun `no se si podria llamar a mama queda descartado`() {
        assertNull(analyze("no sé si podría llamar a mamá"))
    }

    @Test
    fun `no se si deberia pagar la luz queda descartado`() {
        assertNull(analyze("no sé si debería pagar la luz"))
    }

    @Test
    fun `no se si deberia sacar al perro queda descartado`() {
        assertNull(analyze("no sé si debería sacar al perro"))
    }

    @Test
    fun `no se si tendria que llamar a mama queda descartado`() {
        assertNull(analyze("no sé si tendría que llamar a mamá"))
    }

    @Test
    fun `no se si habria que llamar a mama queda descartado`() {
        assertNull(analyze("no sé si habría que llamar a mamá"))
    }

    @Test
    fun `no se si deberia que llamar a mama queda descartado`() {
        assertNull(analyze("no sé si debería que llamar a mamá"))
    }

    @Test
    fun `no se si deberia llamarle manana queda descartado`() {
        assertNull(analyze("no sé si debería llamarle mañana"))
    }

    @Test
    fun `no se si deberia haber llamado a mama queda descartado`() {
        assertNull(analyze("no sé si debería haber llamado a mamá"))
    }

    @Test
    fun `no se si deberia llamar a mama manana queda descartado`() {
        assertNull(analyze("no sé si debería llamar a mamá mañana"))
    }

    // ---- Residual aceptado (doctrina c.649/c.1069): temporal fuerte sobrevive ----

    @Test
    fun `no se si deberia ir al medico manana a las 9 sobrevive con confianza reducida`() {
        val intent = analyze("no sé si debería ir al médico mañana a las 9")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
        assertTrue(intent.confidence >= 0.45f)
    }

    // ---- Guards / pins (verdes desde RED, NO deben cambiar) ----

    @Test
    fun `no se si podria comprar pan sigue descartado`() {
        assertNull(analyze("no sé si podría comprar pan"))
    }

    @Test
    fun `no se si podria irme al medico sigue descartado`() {
        assertNull(analyze("no sé si podría irme al médico"))
    }

    @Test
    fun `pin c1069 no se si llamar a mama sigue descartado`() {
        assertNull(analyze("no sé si llamar a mamá"))
    }

    @Test
    fun `pin c1069 no se si ir al medico sigue descartado`() {
        assertNull(analyze("no sé si ir al médico"))
    }

    @Test
    fun `pin c1069 no se si es buena idea llamar a mama sigue fiel`() {
        val intent = analyze("no sé si es buena idea, llamar a mamá")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.CALL, intent!!.kind)
    }

    @Test
    fun `no se si deberia coma llamar a mama sigue fiel`() {
        val intent = analyze("no sé si debería, llamar a mamá")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.CALL, intent!!.kind)
    }

    @Test
    fun `deberia llamar a mama sin duda sigue fiel`() {
        val intent = analyze("debería llamar a mamá")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.CALL, intent!!.kind)
    }

    @Test
    fun `podria llamar a mama sin duda sigue fiel`() {
        val intent = analyze("podría llamar a mamá")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.CALL, intent!!.kind)
    }

    @Test
    fun `tendria que llamar a mama sin duda sigue fiel`() {
        val intent = analyze("tendría que llamar a mamá")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.CALL, intent!!.kind)
    }

    @Test
    fun `habria que llamar a mama sin duda sigue fiel`() {
        val intent = analyze("habría que llamar a mamá")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.CALL, intent!!.kind)
    }

    @Test
    fun `quiza deberia llamar a mama sigue descartado`() {
        assertNull(analyze("quizá debería llamar a mamá"))
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
}
