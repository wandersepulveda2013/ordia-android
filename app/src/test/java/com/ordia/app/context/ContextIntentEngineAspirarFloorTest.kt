package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.730: forma "aspirar <objeto>" (17/19 TERCERA clase, hogar 4/6) — piso
 * HOUSEHOLD "aspirar" (verbo de aspiradora, inequívoco como "barrer"/"fregar";
 * va a [HOUSEHOLD_FLOORS] propia con lookahead `(?!a\b)` para NO capturar la
 * acepción figurada "aspirar a un cargo") + keyword "aspirar" (lockstep
 * c.727) + plantilla "(aspirar) X"→"Aspirar X" en la lista genérica de
 * extractTitle (lockstep c.727).
 * Kind: HOUSEHOLD (deliberación genérico-vs-acotado: "aspirar" en imperativo
 * es siempre aspiradora; la única acepción ajena es la figurada con "a",
 * guardia explícita — no se acota a un solo objeto como "lavadora").
 */
class ContextIntentEngineAspirarFloorTest {

    @Test
    fun `captura aspirar plus fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "aspirar la alfombra mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Aspirar la alfombra", intent.title)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, aspirar la alfombra esta tarde", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Aspirar la alfombra", intent.title)
    }

    @Test
    fun `captura objeto sofa`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "aspirar el sofá esta noche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Aspirar el sofá", intent.title)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana aspirar la alfombra", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Aspirar la alfombra", intent.title)
    }

    @Test
    fun `no aspirar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no aspirar la alfombra mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quizá aspirar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá aspirar la alfombra mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado aspiré descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "aspiré la alfombra ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `aspirar suelto descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "aspirar", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `aspirar a un cargo descartado`() {
        // Figura de ambición ("aspirar a un cargo"): piso con lookahead
        // `(?!a\b)` lo excluye; keyword+temporal no alcanzan umbral.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "aspirar a un cargo mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `envolvente c613 gobierna TASK`() {
        // "recuérdame aspirar la alfombra": el piso HOUSEHOLD se descarta vía
        // imperativeIsWrapped (WRAPPABLE_PATTERNS + HOUSEHOLD_FLOORS); el piso
        // TASK c.613 gobierna.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame aspirar la alfombra", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Aspirar la alfombra", intent.title)
    }
}
