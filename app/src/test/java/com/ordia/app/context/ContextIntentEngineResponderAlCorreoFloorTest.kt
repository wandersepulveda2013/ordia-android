package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.870: piso «responder AL correo» — contracción «al» (= a + el) ante el
 * objeto acotado del piso hermano c.860/c.867/c.869, lateral registrada en
 * la sonda c.860 como «contracción no medida»; medición PRE sobre HEAD
 * 7341168 con sonda efímera `/tmp/probe867/PreProbe870.kt`: 6/6 candidatas
 * NULL («responder al correo/email/mensaje…»). Extensión mínima del
 * determinante del piso con `al\s+` en DOS puntos lockstep (piso +
 * plantilla; las keywords «correo»/«email»/«mensaje» ya existen, no hace
 * falta keyword nueva). Guards heredados: negación/duda/pasado FUERA;
 * «responder al jefe» y «responder a la pregunta» FUERA por el objeto
 * acotado. La envolvente «recuérdame responder al correo de Juan hoy» ya
 * enrutaba TASK 0.45 por el candado c.613 (regresión hermana).
 */
class ContextIntentEngineResponderAlCorreoFloorTest {

    @Test
    fun `captura base con fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "responder al correo de Juan hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Responder al correo de Juan", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con email`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "responder al email mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Responder al email", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con mensaje y franja`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "responder al mensaje esta tarde", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Responder al mensaje", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, responder al correo hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Responder al correo", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana responder al mensaje", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Responder al mensaje", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura desnuda`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "responder al correo", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Responder al correo", intent.title)
    }

    @Test
    fun `negada descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no responder al correo hoy", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `duda descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá responder al mensaje mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "respondí al correo ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `persona sin objeto descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "responder al jefe mañana", 1000)
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
    fun `regresion correo base sigue capturando`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "responder el correo de Ana hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `regresion mensaje sigue capturando`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "responder el mensaje de Juan hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
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
    fun `regresion envolvente al correo ya ruteaba`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame responder al correo de Juan hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}
