package com.ordia.app.context

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1069: lateral ABIERTA documentada en c.1063/c.1064 — duda «no sé si
 * <infinitivo>» (marcador de duda NO cubierto por [HEDGE_PATTERN] c.649)
 * capturada como compromiso firme. Medición PRE con sondas efímeras
 * `/tmp/probe-work/NoSeSiDoubtProbe{,2}.kt` (motor real vía
 * `tools/run_probe.sh`, HEAD c.1064 `6a568ec`): 8 capturas P1/P2 medidas
 * (misma clase que c.649: el usuario expresa duda explícita sobre la acción
 * y la captura pasiva la persistía como tarea real — bandeja degradada con
 * items no validados):
 *
 *   «no sé si llamar a mamá»        → CALL 0.57 firme
 *   «no sé si llamar a mamá mañana» → CALL 0.67 ¡con dueAt!
 *   «no se si llamar a mamá»        → CALL 0.57 (sin tilde)
 *   «no sé si hablar con el jefe»   → CALL 0.52
 *   «no sé si ir al médico»         → APPOINTMENT 0.67
 *   «todavía no sé si llamar al banco» → CALL 0.57
 *   «no sé si sacar al perro»       → HOUSEHOLD 0.45
 *   «aún no sé si ir al gimnasio»   → EXERCISE 0.59
 *   «no sé si quedar con Ana»       → MEETING 0.45
 *   «no sé si estudiar para el examen» → STUDY 0.49
 *   «no sé si pedir cita con el dentista» → APPOINTMENT 0.69
 *   «no sé si cortarme los pelos»   → ERRAND 0.45
 *   «no sé si pasear al perro»      → HOUSEHOLD 0.45
 *   «no sé si darle la pastilla al perro» → HOUSEHOLD 0.45
 *   «no sé si devolver el libro a Ana»    → ERRAND 0.45
 *
 * Fix mínimo (UN punto, doctrina c.649): marcador «no sé si» añadido a
 * [HEDGE_PATTERN] — la penalización post-pisos existente
 * ([HEDGE_PENALTY] = 0.3) descarta toda la forma gobernada medida
 * (0.45..0.69 − 0.3 < [MINIMUM_CONFIDENCE] = 0.45). CERO keywords nuevas
 * (lección c.751): la duda léxica es objetiva, no una keyword de kind.
 * Penaliza, no bloquea: la duda no niega la intención (a diferencia de la
 * negación c.648/c.1063, que descarta el kind).
 *
 * Anti-overreach medido (guards verdes desde RED):
 *  - «no sé si ella llamó ayer» (pretérito gobernado) → NULL PRE/POST
 *    (no casa piso alguno; el marcador es inofensivo donde no hay captura).
 *  - «sé que tengo que llamar a mamá» → TASK 0.45 intacto (sin «no»).
 *  - «llamar a mamá» afirmativo → CALL 0.57 intacto.
 *  - «recuérdame llamar a mamá» → TASK 0.45 intacto (envolvente firme).
 *  - «no sé si puedo» → NULL PRE/POST (nada que capturar).
 *  - «no sé si es buena idea, llamar a mamá mañana» → CALL 0.67 intacto:
 *    la duda gobierna «es buena idea», NO el imperativo tras la coma;
 *    patrón hermano del cierre posicional de c.650 («llamar al banco si
 *    no llega el pago» no penaliza). NO se cierra en este ciclo (UNA
 *    lateral): exigiría análisis posicional, doctrina aparte.
 *
 * Residuales medidos y acotados (doctrina de la familia c.649 — penalizar,
 * no bloquear; la evidencia temporal fuerte puede sobrevivir a la
 * penalización, igual que «quizá ir al médico mañana a las 9»):
 *  - «no sé si ir al médico mañana a las 9» → APPOINTMENT 0.85 − 0.3 =
 *    0.55 ≥ umbral (sobrevive con confianza reducida; residual aceptado).
 *  - Variantes «no sabemos si…», «no sé si debería…», «no sé si llamaré…»,
 *    «no sé muy bien si…» quedan FUERA del marcador (laterales hermanas,
 *    UNA por ciclo).
 *
 * Determinista (regex), cero random, cero IA fingida, cero UI.
 */
class ContextIntentEngineNoSeSiDoubtDeltaTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- P1/P2: duda «no sé si + infinitivo» gobernante → NULL (descartada) ----

    @Test
    fun `no se si llamar a mama queda descartado`() {
        assertNull(analyze("no sé si llamar a mamá"))
    }

    @Test
    fun `no se si llamar a mama manana queda descartado`() {
        assertNull(analyze("no sé si llamar a mamá mañana"))
    }

    @Test
    fun `no se si sin tilde llamar a mama queda descartado`() {
        assertNull(analyze("no se si llamar a mamá"))
    }

    @Test
    fun `no se si hablar con el jefe queda descartado`() {
        assertNull(analyze("no sé si hablar con el jefe"))
    }

    @Test
    fun `no se si ir al medico queda descartado`() {
        assertNull(analyze("no sé si ir al médico"))
    }

    @Test
    fun `todavia no se si llamar al banco queda descartado`() {
        assertNull(analyze("todavía no sé si llamar al banco"))
    }

    @Test
    fun `no se si sacar al perro queda descartado`() {
        assertNull(analyze("no sé si sacar al perro"))
    }

    @Test
    fun `aun no se si ir al gimnasio queda descartado`() {
        assertNull(analyze("aún no sé si ir al gimnasio"))
    }

    @Test
    fun `no se si quedar con Ana queda descartado`() {
        assertNull(analyze("no sé si quedar con Ana"))
    }

    @Test
    fun `no se si estudiar para el examen queda descartado`() {
        assertNull(analyze("no sé si estudiar para el examen"))
    }

    @Test
    fun `no se si pedir cita con el dentista queda descartado`() {
        assertNull(analyze("no sé si pedir cita con el dentista"))
    }

    @Test
    fun `no se si cortarme los pelos queda descartado`() {
        assertNull(analyze("no sé si cortarme los pelos"))
    }

    @Test
    fun `no se si pasear al perro queda descartado`() {
        assertNull(analyze("no sé si pasear al perro"))
    }

    @Test
    fun `no se si darle la pastilla al perro queda descartado`() {
        assertNull(analyze("no sé si darle la pastilla al perro"))
    }

    @Test
    fun `no se si devolver el libro a Ana queda descartado`() {
        assertNull(analyze("no sé si devolver el libro a Ana"))
    }

    // ---- Guards anti-overreach (verdes desde RED — el marcador no daña) ----

    @Test
    fun `no se si ella llamo ayer sigue NULL`() {
        assertNull(analyze("no sé si ella llamó ayer"))
    }

    @Test
    fun `se que tengo que llamar a mama sigue TASK`() {
        val i = analyze("sé que tengo que llamar a mamá")
        assertNotNull(i)
        assert(i!!.kind == ContextIntentKind.TASK)
    }

    @Test
    fun `llamar a mama afirmativo sigue CALL`() {
        val i = analyze("llamar a mamá")
        assertNotNull(i)
        assert(i!!.kind == ContextIntentKind.CALL)
    }

    @Test
    fun `recuerdame llamar a mama sigue TASK`() {
        val i = analyze("recuérdame llamar a mamá")
        assertNotNull(i)
        assert(i!!.kind == ContextIntentKind.TASK)
    }

    @Test
    fun `no se si puedo sigue NULL`() {
        assertNull(analyze("no sé si puedo"))
    }

    @Test
    fun `duda no gobernante tras coma sigue CALL`() {
        // La duda gobierna «es buena idea», no el imperativo tras la coma
        // (hermano del cierre posicional c.650). Lateral NO cerrada (UNA
        // por ciclo): pin del comportamiento POST del marcador acotado.
        val i = analyze("no sé si es buena idea, llamar a mamá mañana")
        assertNotNull(i)
        assert(i!!.kind == ContextIntentKind.CALL)
    }

    // ---- Regresiones de la familia hedge/condicional (c.649/c.650) ----

    @Test
    fun `quiza llamar a mama sigue NULL`() {
        assertNull(analyze("quizá llamar a mamá"))
    }

    @Test
    fun `quiza ir al medico sigue NULL`() {
        assertNull(analyze("quizá ir al médico"))
    }

    @Test
    fun `si puedo llamar a mama sigue NULL`() {
        assertNull(analyze("si puedo llamar a mamá"))
    }

    @Test
    fun `si tengo tiempo ir al gimnasio sigue NULL`() {
        assertNull(analyze("si tengo tiempo ir al gimnasio"))
    }

    @Test
    fun `llamar al banco si no llega el pago sigue CALL`() {
        val i = analyze("llamar al banco si no llega el pago")
        assertNotNull(i)
        assert(i!!.kind == ContextIntentKind.CALL)
    }

    @Test
    fun `tengo que llamar a mama sigue TASK`() {
        val i = analyze("tengo que llamar a mamá")
        assertNotNull(i)
        assert(i!!.kind == ContextIntentKind.TASK)
    }

    @Test
    fun `no llamar a mama sigue NULL`() {
        assertNull(analyze("no llamar a mamá"))
    }
}
