package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.872: piso «contestar AL jefe / A LA vecina» — contracciones del piso
 * hermano c.861 (lateral registrada en la propia sonda c.861); medición PRE
 * sobre HEAD 7a8e138 con sonda efímera `/tmp/probe867/PreProbe871.kt`: 6/6
 * candidatas NULL. Extensión mínima del objeto del piso en DOS puntos
 * lockstep (piso + plantilla; la keyword-FRASE «contestar a» ya es
 * subcadena de «contestar al…»/«contestar a la…», ver ContextIntent.kt).
 * Guards: «contestar al examen» sigue STUDY (bivalente estudio, medido
 * PRE STUDY 0.47); «contestar a la pregunta»/«a tiempo» FUERA (heredados);
 * negación/duda/pasado FUERA. La envolvente «recuérdame contestar al jefe
 * mañana» ya ruteaba TASK 0.54 por el candado c.613 (regresión hermana).
 */
class ContextIntentEngineContestarAlPisoTest {

    @Test
    fun `captura al con fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "contestar al jefe mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Contestar al jefe", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura a la con franja`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "contestar a la vecina esta tarde", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Contestar a la vecina", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura al mensaje con fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "contestar al mensaje del grupo hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Contestar al mensaje del grupo", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, contestar al jefe hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Contestar al jefe", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana contestar a la vecina", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Contestar a la vecina", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura desnuda`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "contestar al jefe", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Contestar al jefe", intent.title)
    }

    @Test
    fun `negada descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no contestar al jefe hoy", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `duda descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá contestar a la vecina mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "contesté al jefe ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `bivalente examen sigue STUDY`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "contestar al examen mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.STUDY, intent!!.kind)
    }

    @Test
    fun `bivalente pregunta descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "contestar a la pregunta del examen", 1000)
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
    fun `regresion a persona sigue capturando`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "contestar a Juan esta tarde", 1000)
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
    fun `regresion envolvente al jefe ya ruteaba`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame contestar al jefe mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}
