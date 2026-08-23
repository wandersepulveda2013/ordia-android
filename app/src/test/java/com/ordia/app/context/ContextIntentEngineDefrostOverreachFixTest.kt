package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.899 (número provisional, confirmado al fetch pre-push final): el piso
 * libre `descongelar\s+\w` (c.898 hermano) era overreach bivalente —
 * robaba «el banco»/«la cuenta»/«el congelador» como HOUSEHOLD (PRE
 * medido 3/3 HIT indebido por sonda persistida
 * `tools/probe/DefrostOverreachProbe.kt`). Fix: piso acotado al
 * objeto-comida (`carnes?|pollos?|pescados?`) y plantilla acotada igual
 * (alineación piso↔título, lección c.616); guard de negación heredado
 * `(?<!no )`. Regresiones comida intactas («descongelar la
 * carne»/«pollo»/«pescado»).
 */
class ContextIntentEngineDefrostOverreachFixTest {

    // --- Guards bivaleantes (objetivo: NULL post-fix) ------------------------

    @Test
    fun `no roba el banco`() {
        assertNull(ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "descongelar el banco mañana", 1000)
        ))
    }

    @Test
    fun `no roba la cuenta`() {
        assertNull(ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "descongelar la cuenta mañana", 1000)
        ))
    }

    @Test
    fun `no roba el congelador`() {
        assertNull(ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "descongelar el congelador mañana", 1000)
        ))
    }

    // --- Captura comida (objetivo: HIT inalterado) ---------------------------

    @Test
    fun `captura la carne`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "descongelar la carne por la noche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Descongelar la carne", intent.title)
    }

    @Test
    fun `captura el pollo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "descongelar el pollo mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Descongelar el pollo", intent.title)
    }

    @Test
    fun `captura el pescado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "descongelar el pescado esta tarde", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Descongelar el pescado", intent.title)
    }

    // --- Guard negación heredado ---------------------------------------------

    @Test
    fun `negacion inmediata descartada`() {
        assertNull(ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no descongelar la carne mañana", 1000)
        ))
    }
}
