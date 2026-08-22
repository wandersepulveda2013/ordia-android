package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.873: piso «contestar el correo/mensaje/email…» — objeto acotado de
 * comunicación pendiente (lateral registrada en la sonda c.861, hermana del
 * piso «responder el correo» c.860/c.867/c.869); medición PRE sobre HEAD
 * d153b82 con sonda efímera `/tmp/probe867/PreProbe873.kt`: 6/6 candidatas
 * NULL. Extensión mínima en DOS puntos lockstep (piso + plantilla;
 * keywords-OBJETO «correo»/«email»/«mensaje» ya existen en
 * ContextIntent.kt l.168). Guards: «contestar el examen» sigue STUDY
 * (bivalente estudio, medido PRE STUDY 0.47); «contestar el teléfono»
 * FUERA (atender llamada, no gestión de mensaje); negación/duda/pasado
 * FUERA. «Contestar la carta» queda como lateral (sin keyword propia).
 */
class ContextIntentEngineContestarObjetoFloorTest {

    @Test
    fun `captura el correo con fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "contestar el correo de Ana hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Contestar el correo de Ana", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura el mensaje con fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "contestar el mensaje de Juan mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Contestar el mensaje de Juan", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura el email con franja`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "contestar el email esta tarde", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Contestar el email", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, contestar el correo hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Contestar el correo", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana contestar el mensaje del grupo", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Contestar el mensaje del grupo", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura desnuda`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "contestar el correo", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Contestar el correo", intent.title)
    }

    @Test
    fun `negada descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no contestar el correo hoy", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `duda descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá contestar el mensaje mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "contesté el correo ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `bivalente examen sigue STUDY`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "contestar el examen mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.STUDY, intent!!.kind)
    }

    @Test
    fun `telefono atender descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "contestar el teléfono hoy", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `adverbial a tiempo descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "contestar a tiempo", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `regresion al jefe sigue capturando`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "contestar al jefe mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `regresion responder correo sigue capturando`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "responder el correo de Ana hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `regresion contestar a persona sigue capturando`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "contestar a Juan esta tarde", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    // c.874: residuales aportados tras colisión gestionada (mi implementación
    // paralela del mismo lateral descartada; estos 7 casos no estaban
    // cubiertos por los tests del hermano — verificados end-to-end con sonda
    // efímera /tmp/probe872/ResidualProbe.kt sobre HEAD 42c09db: 7/7 OK).

    @Test
    fun `captura plural los correos`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "contestar los correos mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Contestar los correos", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura posesivo mi correo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "contestar mi correo hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Contestar mi correo", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura contraccion al ante objeto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "contestar al correo de Juan hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Contestar al correo de Juan", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `bivalente la pregunta queda fuera`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "contestar la pregunta del examen", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `envolvente sigue ruteando por el candado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame contestar el correo de Juan hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Contestar el correo de Juan", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `regresion responder al correo c870`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "responder al correo hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Responder al correo", intent.title)
    }

    @Test
    fun `verbo suelto no casa`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "contestar", 1000)
        )
        assertNull(intent)
    }
}
