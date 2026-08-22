package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.867: piso «responder el email» — lateral medida NULL en la sonda c.860
 * (OCTAVA clase explotada: las formas sinónimas del objeto registradas como
 * candidatas propias; medición PRE sobre HEAD 2d75295 con sonda efímera
 * `/tmp/probe867/PreProbe.kt`: 7/7 candidatas NULL). Extensión del objeto del
 * piso hermano «responder el correo» c.860 con `emails?` (lockstep en TRES
 * puntos, lección c.616/c.751 — a diferencia de c.860 SÍ hizo falta keyword:
 * sin «email» en ContextIntent.kt la frase no llega al análisis en
 * producción, hermana de c.859/c.864): piso acotado `(?:correos?|emails?)`
 * en [hasStrongTaskImperative] + plantilla de título en [extractTitle]
 * (grafía del usuario preservada, doctrina c.653) + keyword-OBJETO «email»
 * junto a «correo». Guards heredados del patrón: negación/duda/pasado/
 * verbo aislado/bivalentes FUERA por el objeto acotado. Sin cláusula
 * dedicada en [imperativeIsNegated]: keyword «email» 0.12 + bono temporal
 * 0.1 = 0.22 < umbral (aritmética c.859…c.865). Kind TASK, hermano de
 * «responder el correo» c.860. La envolvente «recuérdame responder el
 * email de Ana hoy» ya enrutaba TASK 0.45 por el candado c.613 (regresión
 * hermana que debe seguir HIT).
 */
class ContextIntentEngineResponderEmailFloorTest {

    @Test
    fun `captura base con fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "responder el email de Ana hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Responder el email de Ana", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con fecha sin complemento`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "responder el email mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Responder el email", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura plural con franja`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "responder los emails esta tarde", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Responder los emails", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, responder el email de Juan hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Responder el email de Juan", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana responder el email", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Responder el email", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura desnuda`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "responder el email", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Responder el email", intent.title)
    }

    @Test
    fun `captura con posesivo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "responder mi email hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Responder mi email", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `negada descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no responder el email hoy", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `duda descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá responder el email mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "respondí el email ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `verbo aislado descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "responder", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `bivalente pregunta descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "responder a la pregunta del examen", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `regresion correo sigue capturando`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "responder el correo de Ana hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Responder el correo de Ana", intent.title)
    }

    @Test
    fun `regresion contestar sigue capturando`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "contestar a Juan esta tarde", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `regresion reclamar factura sigue capturando`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "reclamar la factura del banco mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `regresion envolvente correo sigue capturando`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame responder el correo de Ana hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `regresion envolvente email ya ruteaba`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame responder el email de Ana hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}
