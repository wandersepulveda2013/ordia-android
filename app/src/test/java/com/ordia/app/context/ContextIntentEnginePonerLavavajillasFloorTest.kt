package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.738: forma "poner el lavavajillas" (2/7 CUARTA clase, sonda
 * `FourthClassChoreProbe.kt` c.734) — piso HOUSEHOLD acotado al objeto
 * `lavavajillas` (familia de pisos acotados [HOUSEHOLD_TRASH_FLOOR] c.717 /
 * [HOUSEHOLD_BED_FLOOR] c.728 / [HOUSEHOLD_WASHER_FLOOR] c.729: "poner"
 * suelto es demasiado genérico para posición libre) + keyword "lavavajillas"
 * (lockstep keyword↔piso) + plantilla "(poner) (el) lavavajillas…"→"Poner el
 * lavavajillas…" (lockstep lección c.717).
 * Interop c.729: "poner la lavadora" sigue siendo forma inconfundible de
 * laundry; el guard del piso lavavajillas exige el literal `lavavajillas`,
 * así "lavadora" no roba lavavajillas ni viceversa.
 * Kind: HOUSEHOLD (quehacer doméstico canónico "poned/pongo el lavavajillas";
 * TASK solo en envolvente c.613).
 */
class ContextIntentEnginePonerLavavajillasFloorTest {

    @Test
    fun `captura poner el lavavajillas plus franja`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "poner el lavavajillas esta noche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Poner el lavavajillas", intent.title)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, poner el lavavajillas esta noche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Poner el lavavajillas", intent.title)
    }

    @Test
    fun `captura franja manana`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "poner el lavavajillas mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Poner el lavavajillas", intent.title)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "esta noche poner el lavavajillas", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Poner el lavavajillas", intent.title)
    }

    @Test
    fun `no poner descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no poner el lavavajillas esta noche", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quizá poner descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá poner el lavavajillas esta noche", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado puse descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "puse el lavavajillas ayer", 1000)
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
    fun `objeto no lavavajillas no roba HOUSEHOLD`() {
        // Objeto inocuo y estable: la película no es quehacer doméstico; NI
        // está en la lista de formas CUARTA clase pendientes (evita fracaso
        // futuro cuando se implementen, p. ej. "poner la mesa" 1/7).
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "poner la película a las 2", 1000)
        )
        if (intent != null) {
            assertNotEquals(ContextIntentKind.HOUSEHOLD, intent.kind)
        }
    }

    @Test
    fun `envolvente c613 gobierna TASK`() {
        // "recuérdame poner el lavavajillas": el piso HOUSEHOLD se descarta
        // vía imperativeIsWrapped (WRAPPABLE_PATTERNS + HOUSEHOLD_FLOORS); el
        // piso TASK c.613 gobierna.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame poner el lavavajillas", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Poner el lavavajillas", intent.title)
    }
}
