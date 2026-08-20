package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.743 (número provisional, confirmado al fetch pre-push final): forma
 * "colgar la ropa" (6/7 CUARTA clase, sonda `FourthClassChoreProbe.kt`
 * c.734) — piso HOUSEHOLD acotado al objeto `ropa(s)` sobre el verbo
 * bivalente "colgar" (familia de pisos acotados [HOUSEHOLD_TRASH_FLOOR]
 * c.717 / [HOUSEHOLD_BED_FLOOR] c.728 / [HOUSEHOLD_WASHER_FLOOR] c.729 /
 * [HOUSEHOLD_VACUUM_CLEANER_FLOOR] c.742: "colgar" suelto es demasiado
 * genérico para posición libre — el cuadro/el teléfono/de la barra) +
 * keyword "ropa" (lockstep keyword↔piso).
 * Interop: "tender la ropa" ya captura vía keyword-verb "tender" (c.639);
 * aquí es el verbo "colgar" acotado al sustantivo — no hay solape.
 * Kind: HOUSEHOLD (quehacer doméstico canónico; TASK solo en envolvente
 * c.613).
 */
class ContextIntentEngineColgarRopaFloorTest {

    @Test
    fun `captura colgar la ropa plus franja`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "colgar la ropa esta tarde", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Colgar la ropa", intent.title)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, colgar la ropa esta noche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Colgar la ropa", intent.title)
    }

    @Test
    fun `captura franja manana`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "colgar la ropa mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Colgar la ropa", intent.title)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "esta tarde colgar la ropa", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Colgar la ropa", intent.title)
    }

    @Test
    fun `no colgar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no colgar la ropa mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quizá colgar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá colgar la ropa mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado colgue descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "colgué la ropa ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `colgar suelto descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "colgar", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `objeto no ropa no roba HOUSEHOLD`() {
        // Objeto inocuo y estable: "el cuadro" no es quehacer doméstico; NI
        // está en la lista de formas CUARTA clase pendientes (evita fracaso
        // futuro cuando se implementen).
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "colgar el cuadro del salón", 1000)
        )
        if (intent != null) {
            assertNotEquals(ContextIntentKind.HOUSEHOLD, intent.kind)
        }
    }

    @Test
    fun `envolvente c613 gobierna TASK`() {
        // "recuérdame colgar la ropa": el piso HOUSEHOLD se descarta
        // vía imperativeIsWrapped (WRAPPABLE_PATTERNS + HOUSEHOLD_FLOORS); el
        // piso TASK c.613 gobierna.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame colgar la ropa", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Colgar la ropa", intent.title)
    }
}
