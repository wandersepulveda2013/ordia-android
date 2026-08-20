package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.725: forma "descargar <objeto>" (12/19 TERCERA clase) — piso TASK +
 * keyword TASK (lockstep c.713) + plantilla "(descargar) X"→"Descargar X".
 * Kind: TASK (deliberación contra NOTE/TRAVEL; criterio c.704).
 */
class ContextIntentEngineDescargarFloorTest {

    @Test
    fun `captura descargar plus fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "descargar la factura mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Descargar la factura", intent.title)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, descargar los documentos hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Descargar los documentos", intent.title)
    }

    @Test
    fun `eot con sufijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "descargar la app hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals("Descargar la app", intent!!.title)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana descargar la factura", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Descargar la factura", intent.title)
    }

    @Test
    fun `no descargar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no descargar la factura mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quizá descargar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá descargar la factura mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `descarga sustantivo descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "la descarga de la factura fue ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado descargué descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "descargué la factura ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `descargar suelto descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "descargar", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `envolvente c613 gobierna`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame descargar la factura", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Descargar la factura", intent.title)
    }
}
