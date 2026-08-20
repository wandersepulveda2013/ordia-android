package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.754: forma "quitar la mesa" (CUARTA clase, sonda
 * `tools/probe/FourthClassVerbDiscoveryProbe.kt`) — piso HOUSEHOLD
 * "quitar la mesa" ACOTADO al objeto (par complementario de "poner la
 * mesa" c.736; hermano del piso "quitar el polvo" c.732): "quitar"
 * suelto es bivalente (el polvo/la música/las pilas), así la posición
 * libre se reserva al objeto `mesa(s)`.
 * Keyword "mesa" ya existía (lockstep por el OBJETO, como "celular"
 * c.751); el verbo "quitar" NO se añade (bivalente). Plantilla
 * "(quitar) (la) mesa(s)…"→"Quitar la mesa…".
 * Kind: HOUSEHOLD (quehacer doméstico canónico; TASK solo envolvente
 * c.613 vía guard lockstep).
 */
class ContextIntentEngineQuitarMesaFloorTest {

    @Test
    fun `captura quitar mesa plus fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quitar la mesa hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Quitar la mesa", intent.title)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, quitar la mesa mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Quitar la mesa", intent.title)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "esta noche quitar la mesa", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Quitar la mesa", intent.title)
    }

    @Test
    fun `captura mesas plural`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quitar las mesas mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Quitar las mesas", intent.title)
    }

    @Test
    fun `no quitar mesa descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no quitar la mesa mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quizá quitar mesa descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá quitar la mesa mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado quité mesa descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quité la mesa ayer", 1000)
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
    fun `quitar otro objeto no captura`() {
        // Objeto no doméstico ("quitar la música") — control kind-drift
        // (familia c.732): el piso acotado no dispara con objeto abierto.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quitar la música mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `mesita diminutivo descartado`() {
        // `\b` final excluye diminutivos (familia "jardincito" c.748).
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quitar la mesita mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `envolvente c613 gobierna TASK`() {
        // "recuérdame quitar la mesa": piso HOUSEHOLD descartado vía
        // imperativeIsWrapped; piso TASK c.613 gobierna.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame quitar la mesa", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Quitar la mesa", intent.title)
    }

    @Test
    fun `regresión poner la mesa sigue HOUSEHOLD`() {
        // Par complementario c.736 intacto.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "poner la mesa hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Poner la mesa", intent.title)
    }

    @Test
    fun `regresión quitar el polvo sigue HOUSEHOLD`() {
        // Hermano "quitar" c.732 intacto (verbos/objetos disjuntos).
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quitar el polvo hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Quitar el polvo", intent.title)
    }
}
