package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.731: forma "cortar el césped" (18/19 TERCERA clase, hogar 5/6) — piso
 * HOUSEHOLD "cortar el césped" ACOTADO al objeto (precedente cama c.728):
 * "cortar" suelto es ambiguo (pelo/pan/pastel/comunicación), así la posición
 * libre se reserva al objeto `césped(es)`. Keyword "césped" (lockstep c.717)
 * + plantilla "(cortar) (el/la) césped(es)…"→"Cortar el césped …".
 * Kind: HOUSEHOLD (quehacer doméstico canónico; TASK solo envolvente c.613).
 */
class ContextIntentEngineCortarCespedFloorTest {

    @Test
    fun `captura cortar cesped plus fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cortar el césped el sábado", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Cortar el césped", intent.title)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, cortar el césped mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Cortar el césped", intent.title)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "el sábado cortar el césped", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Cortar el césped", intent.title)
    }

    @Test
    fun `captura cespedes plural`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cortar los céspedes mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Cortar los céspedes", intent.title)
    }

    @Test
    fun `no cortar cesped descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no cortar el césped mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quizá cortar cesped descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá cortar el césped mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado corté cesped descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "corté el césped ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `cortar suelto descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cortar", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `cortar otro objeto no roba HOUSEHOLD`() {
        // Objeto no doméstico ("cortar la comunicación") — control kind-drift
        // tipo "hacer la tarea" (c.728): el piso acotado no le da piso
        // HOUSEHOLD por la keyword "césped".
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cortar la comunicación mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `envolvente c613 gobierna TASK`() {
        // "recuérdame cortar el césped": piso HOUSEHOLD descartado vía
        // imperativeIsWrapped; piso TASK c.613 gobierna.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame cortar el césped", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cortar el césped", intent.title)
    }
}
