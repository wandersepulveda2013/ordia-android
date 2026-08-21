package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.828: forma "vaciar <objeto>" (familia hogar de la sonda
 * `tools/probe/CaptureCoverageProbe.kt` c.822: "vaciar la nevera el
 * domingo" / "vaciar el armario el domingo" — olvido silencioso P1).
 * "vaciar" se añade al piso doméstico de posición libre [HOUSEHOLD_FLOOR]
 * (precedente c.727 "tender": verbo de quehacer monosémico — siempre es
 * "vaciar un contenedor/espacio"; sin acepción figurada frecuente como la
 * de "aspirar a un cargo", que exigió piso propio c.730). Lockstep c.639:
 * keyword "vaciar" en HOUSEHOLD (ContextIntent.kt) + bonus 0.15f +
 * plantilla "(vaciar) X"→"Vaciar X" en extractTitle.
 * Kind: HOUSEHOLD (quehacer doméstico canónico, criterio c.643).
 */
class ContextIntentEngineVaciarFloorTest {

    @Test
    fun `captura vaciar la nevera con fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vaciar la nevera el domingo", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Vaciar la nevera", intent.title)
    }

    @Test
    fun `captura vaciar el armario con fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vaciar el armario el domingo", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Vaciar el armario", intent.title)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, vaciar la nevera", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Vaciar la nevera", intent.title)
    }

    @Test
    fun `captura objeto generico cajas`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vaciar las cajas mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Vaciar las cajas", intent.title)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "hoy vaciar el armario", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Vaciar el armario", intent.title)
    }

    @Test
    fun `no vaciar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no vaciar la nevera", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quizá vaciar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá vaciar la nevera mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado vacié descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vacié la nevera ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `sustantivo vaciado descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "el vaciado del armario", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `vaciar suelto descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vaciar", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `envolvente c613 gobierna TASK`() {
        // "recuérdame vaciar la nevera": el piso HOUSEHOLD se descarta vía
        // imperativeIsWrapped (WRAPPABLE_PATTERNS + HOUSEHOLD_FLOORS); el
        // piso TASK c.613 gobierna.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame vaciar la nevera", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Vaciar la nevera", intent.title)
    }

    @Test
    fun `regresion hermana tender sigue HOUSEHOLD`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "tender la ropa el domingo", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Tender la ropa", intent.title)
    }

    // ---- c.828b: refuerzo de guards de borde tras STALE_RUN (duplicado del
    // hermano). Formas verificadas por sonda efímera fuente real sobre la
    // implementación del hermano; ninguna estaba cubierta por sus tests. ----

    @Test
    fun `captura segunda familia de objeto lavavajillas`() {
        // «lavavajillas» ya es objeto-keyword del piso c.738 `poner el
        // lavavajillas`: «vaciar el lavavajillas» debe capturar vía el piso
        // de posición libre sin robar la ruta de c.738 (guard literal).
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vaciar el lavavajillas esta noche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Vaciar el lavavajillas", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `pasado tercera persona vacio descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vació la nevera ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado primera plural vaciamos descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vaciamos la nevera ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado segunda persona vaciaste descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vaciaste el armario ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `afirmacion nominal esta vacia descartada`() {
        // «vacía» (adjetivo) ni contiene la subcadena «vaciar»: la keyword
        // sola no dispara nada; afirmar el estado no compromete nada.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "la nevera está vacía", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `futuro conjugado vaciaran descartado`() {
        // «vaciarán» CONTIENE la subcadena «vaciar» (keyword 0.12 sola) pero
        // queda bajo el umbral 0.45: el piso exige el infinitivo literal.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vaciarán el local mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `regresion hermana llenar sigue TASK`() {
        // Cara simétrica (c.726, TASK): «llenar la nevera» NO debe ser
        // robada por el piso doméstico de posición libre.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llenar la nevera mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Llenar la nevera", intent.title)
    }
}
