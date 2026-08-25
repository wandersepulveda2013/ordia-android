package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * c.1076: lateral ABIERTA documentada en c.1069/c.1070 (y ÚLTIMA
 * variante registrada de la familia de la duda) — duda PLURAL
 * «no sabemos si + infinitivo» («no sabemos si llamar a mamá»)
 * capturada como compromiso firme. Medición PRE con sonda efímera
 * `/tmp/probe1076/Probe.kt` (motor real vía `tools/run_probe.sh`,
 * HEAD `48a0767`): 6 capturas medidas (misma clase P1/P2 que
 * c.649/c.1069/c.1070/c.1072/c.1073: duda explícita persistida como
 * tarea real — bandeja degradada con items no validados):
 *
 *   «no sabemos si llamar a mamá»          → CALL 0.57 firme
 *   «no sabemos si ir al médico»           → APPOINTMENT 0.67 ¡título
 *      corrupto «No sabemos si ir al médico»!
 *   «no sabemos si sacar al perro»         → HOUSEHOLD 0.45 firme
 *   «no sabemos si llamar a mamá mañana»   → CALL 0.67 ¡con dueAt!
 *   «no sabemos muy bien si llamar a mamá» → CALL 0.57 (intercalado
 *      «muy bien», hermano de c.1072)
 *   «No sabemos si llamar a mamá»          → CALL 0.57 (mayúscula)
 *
 * Fix mínimo (UN punto, mismo sitio que c.1069/c.1070/c.1072/c.1073):
 * el marcador de la duda en [HEDGE_PATTERN] admite el PLURAL
 * «sabemos»: `no\s+(?:s[ée]|sabemos)\s+(?:muy\s+bien\s+)?si`. El
 * lookahead existente (infinitivo con enclíticos, modal opcional
 * c.1070, futuro 1ª persona c.1073) se reutiliza SIN TOCAR. La
 * penalización post-pisos existente ([HEDGE_PENALTY] = 0.3) descarta
 * toda la forma medida (0.45..0.67 − 0.3 < [MINIMUM_CONFIDENCE]).
 * CERO keywords nuevas (lección c.751). Penaliza, no bloquea (doctrina
 * c.649: la duda no niega la intención).
 *
 * Anti-overreach medido (guards verdes desde RED):
 *  - «llamar a mamá» y «sabemos que llamar a mamá» (compromiso
 *    directo, sin «no…si») → CALL 0.57 intactos.
 *  - «no sabemos si es buena idea, llamar a mamá» → CALL 0.57 intacto
 *    (la duda gobierna «es buena idea»; cierre posicional hermano de
 *    c.650/c.1069/c.1070/c.1072/c.1073).
 *  - «no sabemos si ella llamará a mamá» (3ª persona gobernada) →
 *    NULL PRE/POST.
 *  - «no sabemos si llamó ayer» (pretérito gobernado) → NULL PRE/POST.
 *  - «no sabemos si sabemos la respuesta» (presente, no infinitivo) →
 *    NULL PRE/POST.
 *  - «no sabemos nada de mamá» (sin «si») → NULL PRE/POST.
 *  - «no sabemos si es buena idea» (sin acción) → NULL PRE/POST.
 *  - Envolventes fieles intactas («tengo que llamar a mamá» TASK 0.45,
 *    «recuérdame llamar a mamá» TASK 0.45).
 *  - Pins de la familia singular intactos (c.1069 infinitivo, c.1070
 *    modal, c.1072 «muy bien», c.1073 futuro).
 *
 * Residual medido y aceptado (doctrina de la familia c.649/c.1069/
 * c.1070/c.1072/c.1073):
 *  - «no sabemos si ir al médico mañana a las 9» → APPOINTMENT
 *    0.85 − 0.3 = 0.55 ≥ umbral (sobrevive con confianza reducida).
 *
 * Variantes FUERA (laterales hermanas, UNA por ciclo, medidas PRE):
 *  - «no sabemos si deberíamos/podríamos…» (modal PLURAL → CALL 0.57 /
 *    APPOINTMENT 0.67 medidos: el lookahead de modal c.1070 sólo
 *    admite la 1ª persona singular).
 *  - «no sabemos si llamaremos…» (futuro PLURAL → NULL medido PRE/POST:
 *    ni el piso CALL ni el lookahead «ré» casan «llamaremos»).
 *
 * Determinista (regex), cero random, cero IA fingida, cero UI.
 */
class ContextIntentEngineNoSabemosSiDoubtDeltaTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- P1/P2: duda plural «no sabemos si + infinitivo» → NULL ----

    @Test
    fun `no sabemos si llamar a mama queda descartado`() {
        assertNull(analyze("no sabemos si llamar a mamá"))
    }

    @Test
    fun `no sabemos si ir al medico queda descartado`() {
        assertNull(analyze("no sabemos si ir al médico"))
    }

    @Test
    fun `no sabemos si sacar al perro queda descartado`() {
        assertNull(analyze("no sabemos si sacar al perro"))
    }

    @Test
    fun `no sabemos si llamar a mama manana queda descartado`() {
        assertNull(analyze("no sabemos si llamar a mamá mañana"))
    }

    @Test
    fun `no sabemos muy bien si llamar a mama queda descartado`() {
        assertNull(analyze("no sabemos muy bien si llamar a mamá"))
    }

    @Test
    fun `no sabemos si llamar a mama con mayuscula queda descartado`() {
        assertNull(analyze("No sabemos si llamar a mamá"))
    }

    // ---- Residual aceptado (doctrina de la familia) ----

    @Test
    fun `no sabemos si ir al medico manana a las 9 sobrevive con confianza reducida`() {
        val intent = analyze("no sabemos si ir al médico mañana a las 9")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
        assertTrue(intent.confidence >= 0.45f)
    }

    // ---- Regresiones (verdes desde RED, NO deben cambiar) ----

    @Test
    fun `llamar a mama directo sigue capturando`() {
        val intent = analyze("llamar a mamá")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.CALL, intent!!.kind)
    }

    @Test
    fun `sabemos que llamar a mama sigue capturando`() {
        val intent = analyze("sabemos que llamar a mamá")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.CALL, intent!!.kind)
    }

    @Test
    fun `no sabemos si es buena idea coma llamar a mama sigue capturando`() {
        val intent = analyze("no sabemos si es buena idea, llamar a mamá")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.CALL, intent!!.kind)
    }

    @Test
    fun `tengo que llamar a mama sigue TASK`() {
        val intent = analyze("tengo que llamar a mamá")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `recuerdame llamar a mama sigue TASK`() {
        val intent = analyze("recuérdame llamar a mamá")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    // ---- Guards (verdes desde RED, NO deben cambiar) ----

    @Test
    fun `no sabemos si ella llamara a mama tercera persona sigue NULL`() {
        assertNull(analyze("no sabemos si ella llamará a mamá"))
    }

    @Test
    fun `no sabemos si llamo ayer pasado sigue NULL`() {
        assertNull(analyze("no sabemos si llamó ayer"))
    }

    @Test
    fun `no sabemos si sabemos la respuesta presente sigue NULL`() {
        assertNull(analyze("no sabemos si sabemos la respuesta"))
    }

    @Test
    fun `no sabemos nada de mama sin si sigue NULL`() {
        assertNull(analyze("no sabemos nada de mamá"))
    }

    @Test
    fun `no sabemos si es buena idea sin accion sigue NULL`() {
        assertNull(analyze("no sabemos si es buena idea"))
    }

    @Test
    fun `no sabemos si llamaremos a mama futuro plural sigue NULL`() {
        assertNull(analyze("no sabemos si llamaremos a mamá"))
    }

    // ---- Pins de la familia singular (verdes desde RED) ----

    @Test
    fun `pin c1069 no se si llamar a mama sigue NULL`() {
        assertNull(analyze("no sé si llamar a mamá"))
    }

    @Test
    fun `pin c1070 no se si deberia llamar a mama sigue NULL`() {
        assertNull(analyze("no sé si debería llamar a mamá"))
    }

    @Test
    fun `pin c1072 no se muy bien si llamar a mama sigue NULL`() {
        assertNull(analyze("no sé muy bien si llamar a mamá"))
    }

    @Test
    fun `pin c1073 no se si llamare a mama sigue NULL`() {
        assertNull(analyze("no sé si llamaré a mamá"))
    }
}
