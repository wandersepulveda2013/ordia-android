package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.859: extensión del objeto del piso «tomar la medicina» (c.765) con
 * «medicaci[oó]n» — candidata 1/7 de la sonda persistida c.857
 * `tools/probe/EighthClassAdminProbe.kt` (OCTAVA clase de formas de gestiones
 * de adulto; la candidata es de salud cotidiana formal, salida de la medición
 * PRE sobre HEAD 39ff7f8: «tomar la medicación a las 8» → NULL). Es la
 * palabra formal de la medicación diaria en español corriente y su hermana
 * «medicina» ya capturaba (c.765), asimetría silenciosa de máximo coste en
 * autocuidado. Lockstep en tres puntos (lección c.751): piso
 * [hasStrongTaskImperative] + plantilla de título + keyword-OBJETO
 * "medicación". Guards heredados del piso: negación («no tomar…»), duda
 * («quizá…»), pasado («me tomé…»), objeto bivalente («el pelo»/«el sol»/
 * «un café» siguen FUERA), sustantivo suelto. Kind: TASK (misma deliberación
 * que c.765: autocuidado, no cita ni quehacer).
 */
class ContextIntentEngineMedicacionFloorTest {

    @Test
    fun `captura base con hora`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "tomar la medicación a las 8", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Tomar la medicación", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura sin tilde`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "tomar la medicacion a las 8", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        // Doctrina c.653: la grafía del usuario se preserva tal cual.
        assertEquals("Tomar la medicacion", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura enclitica con fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "tomarme la medicación mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Tomarme la medicación", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "esta noche tomar la medicación", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Tomar la medicación", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, tomar la medicación esta noche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Tomar la medicación", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `envolvente gobierna task`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame tomar la medicación mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `negada descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no tomar la medicación mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `duda descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá tomar la medicación a las 8", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "me tomé la medicación ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `objeto bivalente pelo descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "tomar el pelo a mi hermano mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `objeto bivalente sol descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "tomar el sol en la playa", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `objeto bivalente cafe descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "tomar un café con Ana mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `sustantivo suelto descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "la medicación está en el botiquín", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `regresion medicina sigue capturando`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "tomar la medicina a las 8", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Tomar la medicina", intent.title)
        assertNotNull(intent.dueAt)
    }
}
