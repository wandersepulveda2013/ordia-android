package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.736: forma "poner la mesa" (1/7 CUARTA clase, sonda
 * `tools/probe/FourthClassChoreProbe.kt` c.734) — piso HOUSEHOLD
 * "poner la mesa" ACOTADO al objeto (precedentes en la misma bivalencia de
 * "poner": lavadora c.729; familia cama c.728 / polvo c.732): "poner" suelto
 * es ambiguo (la música/la alarma/las pilas), así la posición libre se
 * reserva al objeto `mesa(s)`.
 * Keyword "mesa" (lockstep c.717) + plantilla "(poner) (la) mesa(s)…"→
 * "Poner la mesa …".
 * Kind: HOUSEHOLD (quehacer doméstico canónico; TASK solo envolvente c.613).
 */
class ContextIntentEnginePonerMesaFloorTest {

    @Test
    fun `captura poner mesa plus fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "poner la mesa hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Poner la mesa", intent.title)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, poner la mesa mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Poner la mesa", intent.title)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "esta noche poner la mesa", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Poner la mesa", intent.title)
    }

    @Test
    fun `captura mesas plural`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "poner las mesas mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Poner las mesas", intent.title)
    }

    @Test
    fun `no poner mesa descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no poner la mesa mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quizá poner mesa descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá poner la mesa mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado puse mesa descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "puse la mesa ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `poner suelto descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "poner", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `poner otro objeto no roba HOUSEHOLD`() {
        // Objeto no doméstico ("poner la música") — control kind-drift
        // (familia c.732): el piso acotado no le da piso HOUSEHOLD por
        // la keyword "mesa".
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "poner la música mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `envolvente c613 gobierna TASK`() {
        // "recuérdame poner la mesa": piso HOUSEHOLD descartado vía
        // imperativeIsWrapped; piso TASK c.613 gobierna.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame poner la mesa", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Poner la mesa", intent.title)
    }
}
