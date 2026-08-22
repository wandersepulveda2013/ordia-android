package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.848: diagonal «una» del piso «poner la lavadora» c.729 — candidata 3/6
 * de la sonda persistida c.845 `tools/probe/SeventhClassErrandProbe.kt`
 * (asimetría de artículo: «poner LA lavadora» capturaba HOUSEHOLD mientras
 * «poner UNA lavadora» caía a NULL — olvido silencioso del mismo quehacer
 * con el artículo indeterminado, el más natural cuando la lavadora no es
 * «la» de siempre). Fix barato: el grupo de artículo del piso c.729 admite
 * «una» (lockstep piso↔plantilla de título, lección c.717). Keyword
 * «lavadora» ya existía (c.729) → lockstep coste-cero. Acotado deliberado
 * (una forma por ciclo): «un lavavajillas» (piso hermano c.738) y el
 * plural «unas lavadoras» quedan FUERA.
 */
class ContextIntentEnginePonerUnaLavadoraFloorTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Capturas directas (piso) ----

    @Test
    fun `captura poner una lavadora plus franja`() {
        val intent = analyze("poner una lavadora esta tarde")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Poner una lavadora", intent.title)
    }

    @Test
    fun `captura con acuse`() {
        val intent = analyze("vale, poner una lavadora esta noche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Poner una lavadora", intent.title)
    }

    @Test
    fun `captura franja noche`() {
        val intent = analyze("poner una lavadora por la noche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Poner una lavadora", intent.title)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = analyze("esta tarde poner una lavadora")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Poner una lavadora", intent.title)
    }

    // ---- Envolvente: c.613 gobierna TASK (lockstep WRAPPABLE_PATTERNS) ----

    @Test
    fun `envolvente c613 gobierna TASK`() {
        val intent = analyze("recuérdame poner una lavadora")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Poner una lavadora", intent.title)
    }

    // ---- Guards (contratos anti-overreach / anti-falsos-positivos) ----

    @Test
    fun `no poner descartado`() {
        assertNull(analyze("no poner una lavadora esta tarde"))
    }

    @Test
    fun `quizá poner descartado`() {
        assertNull(analyze("quizá poner una lavadora esta tarde"))
    }

    @Test
    fun `pasado puse descartado`() {
        assertNull(analyze("puse una lavadora ayer"))
    }

    @Test
    fun `poner un lavavajillas queda fuera`() {
        // Acotado deliberado c.848: sólo la diagonal «una» del piso
        // lavadora; «un lavavajillas» (piso hermano c.738) es candidata
        // propia, una forma por ciclo.
        assertNull(analyze("poner un lavavajillas esta noche"))
    }

    // ---- Regresiones de la familia (pisos hermanos intactos) ----

    @Test
    fun `regresión poner la lavadora intacta`() {
        val intent = analyze("poner la lavadora esta tarde")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Poner la lavadora", intent.title)
    }

    @Test
    fun `regresión poner el lavavajillas intacta`() {
        val intent = analyze("poner el lavavajillas esta noche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Poner el lavavajillas", intent.title)
    }

    @Test
    fun `regresión envolvente c729 intacta`() {
        val intent = analyze("recuérdame poner la lavadora")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Poner la lavadora", intent.title)
    }
}
