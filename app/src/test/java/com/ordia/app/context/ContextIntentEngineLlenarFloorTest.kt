package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.726: forma "llenar <objeto>" (13/19 TERCERA clase) — piso TASK +
 * keyword TASK (lockstep c.713) + plantilla "(llenar) X"→"Llenar X".
 * Kind: TASK (deliberación contra NOTE/FORM; criterio c.704 — FORM no existe).
 */
class ContextIntentEngineLlenarFloorTest {

    @Test
    fun `captura llenar plus fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llenar la solicitud mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Llenar la solicitud", intent.title)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, llenar la forma hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Llenar la forma", intent.title)
    }

    @Test
    fun `eot con sufijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llenar el formulario hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals("Llenar el formulario", intent!!.title)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana llenar la solicitud", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Llenar la solicitud", intent.title)
    }

    @Test
    fun `no llenar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no llenar la solicitud mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quizá llenar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá llenar la solicitud mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `llenado sustantivo descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "el llenado de la solicitud fue ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado llené descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llené la solicitud ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `llenar suelto descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llenar", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `envolvente c613 gobierna`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame llenar la solicitud", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Llenar la solicitud", intent.title)
    }
}
