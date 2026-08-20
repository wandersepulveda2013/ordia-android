package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.727: forma "tender <objeto>" (14/19 TERCERA clase, hogar 1/6) — piso
 * HOUSEHOLD (`HOUSEHOLD_VERBS`) + keyword HOUSEHOLD (`ContextIntent.kt`,
 * lockstep c.639) + bono houseSpecific en scoreSpecificPatterns + plantilla
 * "(tender) X"→"Tender X" en lockstep (lección c.639/c.713).
 * Kind: HOUSEHOLD (deliberación contra TASK — quehacer doméstico canónico;
 * la familia HOUSEHOLD ya gobierna los imperativos diarios del hogar).
 */
class ContextIntentEngineTenderFloorTest {

    @Test
    fun `captura tender plus fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "tender la ropa hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Tender la ropa", intent.title)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, tender la ropa mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Tender la ropa", intent.title)
    }

    @Test
    fun `título sin sufijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "tender la cama mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals("Tender la cama", intent!!.title)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana tender la ropa", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Tender la ropa", intent.title)
    }

    @Test
    fun `no tender descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no tender la ropa hoy", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quizá tender descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá tender la ropa hoy", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `sustantivo tendedero descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "el tendedero nuevo está en la terraza", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado tendí descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "tendí la ropa ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `tender suelto descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "tender", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `envolvente c613 gobierna TASK`() {
        // "recuérdame tender la ropa": el piso HOUSEHOLD se descarta vía
        // imperativeIsWrapped (WRAPPABLE_PATTERNS + HOUSEHOLD_FLOORS); el piso
        // TASK c.613 gobierna con template "recuérdame X"→"X".
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame tender la ropa", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Tender la ropa", intent.title)
    }
}
