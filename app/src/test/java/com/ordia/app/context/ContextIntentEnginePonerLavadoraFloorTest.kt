package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.729: forma "poner la lavadora" (16/19 TERCERA clase, hogar 3/6) — piso
 * HOUSEHOLD acotado al objeto `lavadora` (familia de los pisos acotados
 * [HOUSEHOLD_TRASH_FLOOR] c.717 / [HOUSEHOLD_BED_FLOOR] c.728: "poner" suelto
 * es demasiado genérico para posición libre) + keyword "lavadora" (lockstep
 * keyword↔piso) + plantilla "(poner) (la) lavadora…"→"Poner la lavadora…"
 * (lockstep lección c.717).
 * Kind: HOUSEHOLD (deliberación contra TASK — quehacer doméstico canónico
 * "poned/pongo la lavadora"; TASK solo en envolvente c.613).
 */
class ContextIntentEnginePonerLavadoraFloorTest {

    @Test
    fun `captura poner la lavadora plus franja`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "poner la lavadora esta tarde", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Poner la lavadora", intent.title)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, poner la lavadora esta noche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Poner la lavadora", intent.title)
    }

    @Test
    fun `captura franja noche`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "poner la lavadora por la noche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Poner la lavadora", intent.title)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "esta tarde poner la lavadora", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Poner la lavadora", intent.title)
    }

    @Test
    fun `no poner descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no poner la lavadora esta tarde", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quizá poner descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá poner la lavadora esta tarde", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado puse descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "puse la lavadora ayer", 1000)
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
    fun `objeto no lavadora no roba HOUSEHOLD`() {
        // "poner la música" es hacer-objeto genérico: el piso queda acotado a
        // `lavadora`, así el hogar NO lo captura (kind-drift anti-overreach).
        // c.736: "poner la mesa" deja de servir de contraejemplo — pasó a ser
        // piso HOUSEHOLD propio (`FourthClassChoreProbe` 1/7); el control de
        // deriva se mantiene con un objeto NO doméstico.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "poner la música a las 2", 1000)
        )
        if (intent != null) {
            assertNotEquals(ContextIntentKind.HOUSEHOLD, intent.kind)
        }
    }

    @Test
    fun `envolvente c613 gobierna TASK`() {
        // "recuérdame poner la lavadora": el piso HOUSEHOLD se descarta vía
        // imperativeIsWrapped (WRAPPABLE_PATTERNS + HOUSEHOLD_FLOORS); el piso
        // TASK c.613 gobierna.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame poner la lavadora", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Poner la lavadora", intent.title)
    }
}
