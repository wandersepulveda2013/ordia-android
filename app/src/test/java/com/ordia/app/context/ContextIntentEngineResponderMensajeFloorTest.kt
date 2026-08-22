package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.869: piso «responder el mensaje» — lateral medida NULL en la sonda
 * c.860 (OCTAVA clase: las formas sinónimas del objeto registradas como
 * candidatas propias, una por ciclo; medición PRE sobre HEAD 2115224 con
 * sonda efímera `/tmp/probe867/PreProbe868.kt`: 7/7 candidatas NULL).
 * Segunda extensión del objeto del piso hermano «responder el correo»
 * c.860 (tras `emails?` c.867) con `mensajes?` (lockstep en TRES puntos,
 * lección c.616/c.751 — como en c.867 SÍ hizo falta keyword: sin «mensaje»
 * en ContextIntent.kt la frase no llega al análisis en producción): piso
 * acotado `(?:correos?|emails?|mensajes?)` en [hasStrongTaskImperative] +
 * plantilla de título en [extractTitle] (grafía del usuario preservada,
 * doctrina c.653) + keyword-OBJETO «mensaje» junto a «correo»/«email».
 * Guards heredados del patrón: negación/duda/pasado/verbo aislado/
 * bivalentes FUERA por el objeto acotado. Sin cláusula dedicada en
 * [imperativeIsNegated]: keyword «mensaje» 0.12 + bono temporal 0.1 = 0.22
 * < umbral (aritmética c.859…c.867). Kind TASK, hermano de c.860/c.867. La
 * envolvente «recuérdame responder el mensaje de Juan hoy» ya enrutaba
 * TASK 0.45 por el candado c.613 (regresión hermana que debe seguir HIT).
 */
class ContextIntentEngineResponderMensajeFloorTest {

    @Test
    fun `captura base con fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "responder el mensaje de Juan hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Responder el mensaje de Juan", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con fecha sin complemento`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "responder el mensaje mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Responder el mensaje", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura plural con franja`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "responder los mensajes esta tarde", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Responder los mensajes", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, responder el mensaje de Ana hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Responder el mensaje de Ana", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana responder el mensaje", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Responder el mensaje", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura desnuda`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "responder el mensaje", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Responder el mensaje", intent.title)
    }

    @Test
    fun `captura con posesivo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "responder mi mensaje hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Responder mi mensaje", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `negada descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no responder el mensaje hoy", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `duda descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá responder el mensaje mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "respondí el mensaje ayer", 1000)
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
    fun `regresion email sigue capturando`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "responder el email de Ana hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Responder el email de Ana", intent.title)
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
    fun `regresion envolvente correo sigue capturando`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame responder el correo de Ana hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `regresion envolvente mensaje ya ruteaba`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame responder el mensaje de Juan hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}
