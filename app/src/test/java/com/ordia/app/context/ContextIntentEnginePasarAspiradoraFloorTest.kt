package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.742 (renumerada de c.740 por colisión de cycle-ID con la mascota y sus docs, c.740/c.741): forma "pasar la aspiradora"
 * (5/7 CUARTA clase, sonda `FourthClassChoreProbe.kt` c.734) — piso HOUSEHOLD
 * acotado al objeto `aspiradora(s)` sobre el verbstin bivalente "pasar"
 * (familia de pisos acotados [HOUSEHOLD_TRASH_FLOOR] c.717 /
 * [HOUSEHOLD_BED_FLOOR] c.728 / [HOUSEHOLD_WASHER_FLOOR] c.729: "pasar"
 * suelto es demasiado genérico para posición libre) + keyword "aspiradora"
 * (lockstep keyword↔piso; c.730 ya tenía el verbo "aspirar", el objeto no).
 * Interop c.730: el piso VACUUM (`aspirar\s+(?!a\b)\w`) requiere el verbo
 * "aspirar", así "pasar la aspiradora" (nombre) no solapa ni viceversa.
 * Kind: HOUSEHOLD (quehacer doméstico canónico; TASK solo en envolvente
 * c.613).
 */
class ContextIntentEnginePasarAspiradoraFloorTest {

    @Test
    fun `captura pasar la aspiradora plus franja`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "pasar la aspiradora esta tarde", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Pasar la aspiradora", intent.title)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, pasar la aspiradora esta noche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Pasar la aspiradora", intent.title)
    }

    @Test
    fun `captura franja manana`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "pasar la aspiradora mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Pasar la aspiradora", intent.title)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "esta tarde pasar la aspiradora", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Pasar la aspiradora", intent.title)
    }

    @Test
    fun `no pasar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no pasar la aspiradora mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quizá pasar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá pasar la aspiradora mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado pase descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "pasé la aspiradora ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasar suelto descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "pasar", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `objeto no aspiradora no roba HOUSEHOLD`() {
        // Objeto inocuo y estable: "la tarde" no es quehacer doméstico; NI
        // está en la lista de formas CUARTA clase pendientes (evita fracaso
        // futuro cuando se implementen).
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "pasar la tarde en casa", 1000)
        )
        if (intent != null) {
            assertNotEquals(ContextIntentKind.HOUSEHOLD, intent.kind)
        }
    }

    @Test
    fun `envolvente c613 gobierna TASK`() {
        // "recuérdame pasar la aspiradora": el piso HOUSEHOLD se descarta
        // vía imperativeIsWrapped (WRAPPABLE_PATTERNS + HOUSEHOLD_FLOORS); el
        // piso TASK c.613 gobierna.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame pasar la aspiradora", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Pasar la aspiradora", intent.title)
    }
}
