package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1073: lateral ABIERTA documentada en c.1069/c.1070/c.1072 — duda
 * «no sé si + FUTURO 1ª persona» («no sé si llamaré a mamá») capturada
 * como compromiso firme. Medición PRE con sondas efímeras
 * `/tmp/probe1073/Probe{,2}.kt` (motor real vía `tools/run_probe.sh`,
 * HEAD c.1071 `fea0f09` + marcador EN CURSO `daa7413`): 9 capturas
 * medidas (misma clase P1/P2 que c.649/c.1069/c.1070/c.1072: duda
 * explícita persistida como tarea real — bandeja degradada con items no
 * validados):
 *
 *   «no sé si llamaré a mamá»                 → CALL 0.53 firme
 *   «no sé si podré ir al gimnasio»           → EXERCISE 0.59 firme
 *   «no sé si llamaré a mamá mañana»          → CALL 0.63 ¡con dueAt!
 *   «no sé si iré al médico mañana a las 9»   → APPOINTMENT 0.58 ¡con
 *      dueAt y título corrupto «No sé si iré al médico»!
 *   «no sé muy bien si llamaré a mamá»        → CALL 0.53 (combinación
 *      con el intercalado c.1072)
 *   «no sé si llamaré a mamá mañana a las 9»  → CALL 0.71 ¡con dueAt!
 *   «no sé si llamaré a mamá a las 9»         → CALL 0.71 ¡con dueAt!
 *   «No sé si llamaré a mamá» (mayúscula)     → CALL 0.53
 *   «no sé si tendré que llamar a mamá»       → CALL 0.57 (futuro de
 *      «tener que» + infinitivo subordinado)
 *
 * Fix mínimo (UN punto, mismo sitio que c.1069/c.1070/c.1072): el
 * lookahead del marcador «no sé si» en [HEDGE_PATTERN] admite el FUTURO
 * de 1ª persona como alternativa al infinitivo (con modal opcional):
 * `[a-záéíóúñü]+ré`. TODAS las formas de futuro de 1ª persona del
 * español terminan en «ré» (regulares llamaré/comeré/iré e irregulares
 * haré/diré/querré/sabré/podré/pondré/saldré/tendré/vendré/valdré/
 * cabré/habré) y «ré» no es terminación de ninguna otra palabra común:
 * lookahead mínimo y seguro. La 3ª persona («llamará», «llamarán») NO
 * casa. La penalización post-pisos existente ([HEDGE_PENALTY] = 0.3)
 * descarta toda la forma medida (0.53..0.71 − 0.3 < [MINIMUM_CONFIDENCE]).
 * CERO keywords nuevas (lección c.751). Penaliza, no bloquea (doctrina
 * c.649: la duda no niega la intención).
 *
 * Anti-overreach medido (guards verdes desde RED):
 *  - «sé que llamaré a mamá» → CALL 0.53 intacto (sin «no…si» — el
 *    marcador exige la duda).
 *  - «llamaré a mamá» (futuro plano, compromiso directo) → CALL 0.53
 *    intacto.
 *  - «no sé si es buena idea, llamaré a mamá» → CALL 0.53 intacto (la
 *    duda gobierna «es buena idea»; cierre posicional hermano de
 *    c.650/c.1069/c.1070/c.1072).
 *  - «no sé si ella llamará a mamá» / «no sé si llamará mamá» (3ª
 *    persona) → NULL PRE/POST (no casa «ré»).
 *  - «no sé si él llamó ayer» (pretérito gobernado) → NULL PRE/POST.
 *  - «no sé si sé la respuesta» (presente «sé», no futuro) → NULL
 *    PRE/POST.
 *  - «no sabemos si llamaremos a mamá» (plural — lateral hermana FUERA,
 *    UNA por ciclo) → NULL PRE/POST.
 *  - Pins c.1069/c.1070/c.1072 intactos (infinitivo, modal, «muy bien»).
 *  - Envolventes fieles intactas («tengo que llamar a mamá» TASK 0.45,
 *    «recuérdame llamar a mamá» TASK 0.45).
 *
 * Variantes FUERA (laterales hermanas, UNA por ciclo): «no sabemos si…»
 * (plural → CALL 0.57 medido c.1070), futuro sin tilde «no se si
 * llamare…» (NULL medido PRE — el piso CALL tampoco casa sin tildes).
 *
 * Determinista (regex), cero random, cero IA fingida, cero UI.
 */
class ContextIntentEngineNoSeSiFuturoDoubtDeltaTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- P1/P2: duda «no sé si + FUTURO» → NULL (descartada) ----

    @Test
    fun `no se si llamare a mama queda descartado`() {
        assertNull(analyze("no sé si llamaré a mamá"))
    }

    @Test
    fun `no se si podre ir al gimnasio queda descartado`() {
        assertNull(analyze("no sé si podré ir al gimnasio"))
    }

    @Test
    fun `no se si llamare a mama manana queda descartado`() {
        assertNull(analyze("no sé si llamaré a mamá mañana"))
    }

    @Test
    fun `no se si ire al medico manana a las 9 queda descartado`() {
        assertNull(analyze("no sé si iré al médico mañana a las 9"))
    }

    @Test
    fun `no se muy bien si llamare a mama queda descartado`() {
        assertNull(analyze("no sé muy bien si llamaré a mamá"))
    }

    @Test
    fun `no se si llamare a mama manana a las 9 queda descartado`() {
        assertNull(analyze("no sé si llamaré a mamá mañana a las 9"))
    }

    @Test
    fun `no se si llamare a mama a las 9 queda descartado`() {
        assertNull(analyze("no sé si llamaré a mamá a las 9"))
    }

    @Test
    fun `no se si llamare mayuscula queda descartado`() {
        assertNull(analyze("No sé si llamaré a mamá"))
    }

    @Test
    fun `no se si tendre que llamar a mama queda descartado`() {
        assertNull(analyze("no sé si tendré que llamar a mamá"))
    }

    // ---- Anti-overreach: compromisos fieles intactos ----

    @Test
    fun `se que llamare a mama sigue fiel`() {
        val intent = analyze("sé que llamaré a mamá")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.CALL, intent!!.kind)
    }

    @Test
    fun `llamare a mama plano sigue fiel`() {
        val intent = analyze("llamaré a mamá")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.CALL, intent!!.kind)
    }

    @Test
    fun `no se si es buena idea coma llamare sigue fiel`() {
        val intent = analyze("no sé si es buena idea, llamaré a mamá")
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
    fun `recuerdame llamar a mama sigue fiel`() {
        val intent = analyze("recuérdame llamar a mamá")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    // ---- Guards: tercera persona, pasado, presente, plural — NULL estables ----

    @Test
    fun `no se si ella llamara tercera persona sigue nulo`() {
        assertNull(analyze("no sé si ella llamará a mamá"))
    }

    @Test
    fun `no se si llamara mama tercera persona sigue nulo`() {
        assertNull(analyze("no sé si llamará mamá"))
    }

    @Test
    fun `no se si el llamo ayer pasado sigue nulo`() {
        assertNull(analyze("no sé si él llamó ayer"))
    }

    @Test
    fun `no se si se la respuesta presente sigue nulo`() {
        assertNull(analyze("no sé si sé la respuesta"))
    }

    @Test
    fun `no sabemos si llamaremos plural sigue nulo`() {
        assertNull(analyze("no sabemos si llamaremos a mamá"))
    }

    // ---- Pins de la familia c.1069/c.1070/c.1072 intactos ----

    @Test
    fun `no se si llamar a mama infinitivo sigue nulo`() {
        assertNull(analyze("no sé si llamar a mamá"))
    }

    @Test
    fun `no se si deberia llamar a mama modal sigue nulo`() {
        assertNull(analyze("no sé si debería llamar a mamá"))
    }

    @Test
    fun `no se muy bien si llamar a mama intercalado sigue nulo`() {
        assertNull(analyze("no sé muy bien si llamar a mamá"))
    }

    // ---- Estables NULL PRE/POST (sin piso que case la forma de futuro) ----

    @Test
    fun `no se si hare la compra sigue nulo`() {
        assertNull(analyze("no sé si haré la compra"))
    }

    @Test
    fun `no se si te llamare enclitico previo sigue nulo`() {
        assertNull(analyze("no sé si te llamaré"))
    }

    @Test
    fun `no se si habre llamado perfecto sigue nulo`() {
        assertNull(analyze("no sé si habré llamado a mamá"))
    }

    @Test
    fun `quiza llame a mama hedge familia sigue nulo`() {
        assertNull(analyze("quizá llame a mamá"))
    }
}
