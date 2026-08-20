package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.732: forma "quitar el polvo" (19/19 TERCERA clase, hogar 6/6 — cierra la
 * sonda c.721) — piso HOUSEHOLD "quitar el polvo" ACOTADO al objeto
 * (precedente cama c.728): "quitar" suelto es ambiguo (el protector/la
 * mancha/la ropa), así la posición libre se reserva al objeto `polvo(s)`.
 * Keyword "polvo" (lockstep c.717) + plantilla "(quitar) (el) polvo(s)…"→
 * "Quitar el polvo …".
 * Kind: HOUSEHOLD (quehacer doméstico canónico; TASK solo envolvente c.613).
 */
class ContextIntentEngineQuitarPolvoFloorTest {

    @Test
    fun `captura quitar polvo plus fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quitar el polvo hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Quitar el polvo", intent.title)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, quitar el polvo mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Quitar el polvo", intent.title)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "el sábado quitar el polvo", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Quitar el polvo", intent.title)
    }

    @Test
    fun `captura polvos plural`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quitar los polvos mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Quitar los polvos", intent.title)
    }

    @Test
    fun `no quitar polvo descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no quitar el polvo mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quizá quitar polvo descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá quitar el polvo mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado quité polvo descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quité el polvo ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quitar suelto descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quitar", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quitar otro objeto no roba HOUSEHOLD`() {
        // Objeto no doméstico ("quitar la mancha") — control kind-drift
        // (familia c.728/c.731): el piso acotado no le da piso HOUSEHOLD por
        // la keyword "polvo".
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quitar la mancha mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `envolvente c613 gobierna TASK`() {
        // "recuérdame quitar el polvo": piso HOUSEHOLD descartado vía
        // imperativeIsWrapped; piso TASK c.613 gobierna.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame quitar el polvo", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Quitar el polvo", intent.title)
    }
}
