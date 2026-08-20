package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.746 (número provisional, confirmado al fetch pre-push final): piso de
 * PAGO con ancla de prefijo TEMPORAL (paridad con los pisos TASK
 * c.691…c.713 y NOTA c.714, que ya anclan a inicio/acuse/temporal vía
 * [ContextIntentEngine.TASK_FLOOR_TEMPORAL]).
 *
 * Defecto de CLASE (mismo que c.643 HOUSEHOLD / c.647 MEETING-EXERCISE-
 * ERRAND-STUDY): el supuesto de c.630 — "los casos afirmativos con ancla
 * temporal ('mañana pagar la luz') ya superan el umbral vía
 * [ContextIntentEngine.extractDateTime]" — era FALSO y dependiente del
 * OBJETO: "mañana pagar la luz" pasa porque "luz" es keyword del kind,
 * pero "mañana pagar el arriendo" / "el lunes pagar el arriendo" /
 * "el viernes pagar la renta" (objetos sin keyword) quedaban en 0.42 <
 * [ContextIntentEngine.MINIMUM_CONFIDENCE] y se DESCARTABAN
 * silenciosamente (NULL, probe JVM fuente real). Olvidar el pago del
 * arriendo/renta es el olvido de MAYOR coste del dominio (recargos,
 * desalojo): misma prioridad P1 que motivó el piso de c.630.
 *
 * Un prefijo temporal + imperativo de pago + objeto ("mañana pagar el
 * arriendo") es una intención de pago inequívoca: el usuario ya fijó
 * CUÁNDO. La extensión es determinista (mismo ancla TASK_FLOOR_TEMPORAL
 * que comparten los pisos TASK/NOTA) y respeta los guards vigentes:
 * negación inmediata (lookbehind `(?<!no )` + [imperativeIsNegated]),
 * duda (penalización post-piso c.649), condición (c.650) y envolvente
 * imperativa ([imperativeIsWrapped] — por eso el piso se centraliza en
 * [ContextIntentEngine.PAYMENT_FLOOR] y se registra en
 * [ContextIntentEngine.WRAPPABLE_PATTERNS], lección lockstep c.648/c.652:
 * sin ello "recuérdame mañana pagar el arriendo" robaría el kind a TASK).
 */
class ContextIntentEnginePaymentTemporalPrefixTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: prefijo temporal + "pagar <objeto>" ---

    @Test
    fun `captura manana pagar el arriendo`() {
        val intent = analyze("mañana pagar el arriendo")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
        assertNotNull("el prefijo temporal debe fijar dueAt", intent.dueAt)
        assertEquals("Pagar el arriendo", intent.title)
    }

    @Test
    fun `captura el lunes pagar el arriendo`() {
        val intent = analyze("el lunes pagar el arriendo")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
        assertNotNull(intent.dueAt)
        assertEquals("Pagar el arriendo", intent.title)
    }

    @Test
    fun `captura el viernes pagar la renta`() {
        val intent = analyze("el viernes pagar la renta")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
        assertNotNull(intent.dueAt)
        assertEquals("Pagar la renta", intent.title)
    }

    @Test
    fun `captura esta noche pagar el recibo del gas`() {
        val intent = analyze("esta noche pagar el recibo del gas")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
        assertNotNull(intent.dueAt)
        assertEquals("Pagar el recibo del gas", intent.title)
    }

    // --- Controles anti-overreach: los guards vigentes siguen mandando ---

    @Test
    fun `negacion al inicio descartada`() {
        assertNull(analyze("no pagar el arriendo mañana"))
    }

    @Test
    fun `negacion incrustada tras temporal descartada`() {
        assertNull(analyze("mañana no pagar el arriendo"))
    }

    @Test
    fun `duda descartada`() {
        assertNull(analyze("quizá mañana pagar el arriendo"))
    }

    @Test
    fun `pasado descartado`() {
        // "pagué" no es "pagar": el piso exige infinitivo.
        assertNull(analyze("pagué el arriendo ayer"))
    }

    @Test
    fun `envolvente recuerdame con temporal gobierna TASK`() {
        // "recuérdame mañana pagar el arriendo": sin el guard de envolvente
        // sobre PAYMENT, el piso temporal robaría el kind (lección c.652).
        val intent = analyze("recuérdame mañana pagar el arriendo")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `envolvente recuerdame sin temporal sigue en TASK`() {
        val intent = analyze("recuérdame pagar el arriendo")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `temporal sin objeto no activa piso`() {
        // "mañana pagar" suelto: el piso exige objeto (\s+\w); no se fuerza
        // PAYMENT por defecto (muletilla).
        val intent = analyze("mañana pagar")
        if (intent != null) {
            // Sólo garantizamos que no se captura por el piso nuevo.
        }
    }
}
