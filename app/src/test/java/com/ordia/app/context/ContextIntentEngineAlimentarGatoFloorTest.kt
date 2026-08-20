package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.744 (número provisional, confirmado al fetch pre-push final): forma
 * "alimentar al gato" (2/8 mascotas, sonda paralela
 * `FourthClassVerbDiscoveryProbe.kt` c.740, PRE NULL) — piso HOUSEHOLD
 * acotado al objeto mascota `gat[oa]s?` sobre el verbo bivalente
 * "alimentar" (familia de pisos acotados [HOUSEHOLD_PET_FLOOR] c.740:
 * "alimentar" suelto es demasiado genérico para posición libre — el
 * bebé/la planta/la relación) + keyword "gato"/"gata" (lockstep
 * keyword↔piso).
 * Interop c.740: verbos disjuntos (sacar vs alimentar), objetos
 * disjuntos (perro vs gato) — no hay solape.
 * Kind: HOUSEHOLD (cuidado de mascota = quehacer doméstico canónico,
 * misma deliberación c.740; TASK solo en envolvente c.613).
 */
class ContextIntentEngineAlimentarGatoFloorTest {

    @Test
    fun `captura alimentar al gato plus fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "alimentar al gato hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Alimentar al gato", intent.title)
    }

    @Test
    fun `captura gata con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, alimentar a la gata mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Alimentar a la gata", intent.title)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "esta noche alimentar al gato", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Alimentar al gato", intent.title)
    }

    @Test
    fun `captura posesivo y franja hora`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "alimentar a mi gato a las 6", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Alimentar a mi gato", intent.title)
    }

    @Test
    fun `captura plural gatos`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "alimentar a los gatos mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Alimentar a los gatos", intent.title)
    }

    @Test
    fun `no alimentar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no alimentar al gato hoy", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quizá alimentar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá alimentar al gato mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado alimente descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "alimenté al gato ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `alimentar suelto descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "alimentar", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `objeto no gato no roba HOUSEHOLD`() {
        // Objeto inocuo y estable: "al bebé" no es la forma sondeada; el
        // objeto mascota se acota a `gat[oa]s?` (familia control
        // kind-drift c.728/c.731).
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "alimentar al bebé hoy", 1000)
        )
        if (intent != null) {
            assertNotEquals(ContextIntentKind.HOUSEHOLD, intent.kind)
        }
    }

    @Test
    fun `envolvente c613 gobierna TASK`() {
        // "recuérdame alimentar al gato": el piso HOUSEHOLD se descarta
        // vía imperativeIsWrapped (WRAPPABLE_PATTERNS + HOUSEHOLD_FLOORS);
        // el piso TASK c.613 gobierna.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame alimentar al gato", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Alimentar al gato", intent.title)
    }
}
