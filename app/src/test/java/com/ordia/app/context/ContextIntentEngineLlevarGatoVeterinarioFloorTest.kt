package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.755 (número provisional, confirmado al fetch pre-push final): forma
 * "llevar al gato al veterinario" (sonda paralela
 * `FourthClassVerbDiscoveryProbe.kt` c.740, PRE NULL, 2/8→3/8 mascotas)
 * — extensión del piso [HOUSEHOLD_VET_FLOOR] c.747 con alternancia de
 * objeto `(?:perr[oa]s?|gat[oa]s?)` sobre destino `veterinari[oa]s?`.
 * El verbo "llevar" es bivalente (el coche/la cuenta/a los niños al
 * colegio), por lo que el piso sigue acotado a la forma completa
 * mascota+destino — solo se amplía el objeto a la segunda mascota
 * sonada. Lockstep keyword↔piso: "gato"/"gata" c.744 ya existen;
 * "veterinario"/"veterinaria" c.747 ya existen → cero keywords nuevas.
 */
class ContextIntentEngineLlevarGatoVeterinarioFloorTest {

    @Test
    fun `captura llevar al gato al veterinario plus fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar al gato al veterinario el viernes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Llevar al gato al veterinario", intent.title)
    }

    @Test
    fun `captura gata con veterinaria femenino`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a la gata a la veterinaria hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Llevar a la gata a la veterinaria", intent.title)
    }

    @Test
    fun `captura posesivo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a mi gato al veterinario a las 6", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Llevar a mi gato al veterinario", intent.title)
    }

    @Test
    fun `captura plural gatos`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los gatos al veterinario el sábado", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Llevar a los gatos al veterinario", intent.title)
    }

    @Test
    fun `objeto perro sigue capturando (regresión c-d747)`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar al perro al veterinario mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Llevar al perro al veterinario", intent.title)
    }

    @Test
    fun `no llevar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no llevar al gato al veterinario mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quizá llevar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá llevar al gato al veterinario mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado lleve descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevé al gato al veterinario ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `destino no veterinario no roba HOUSEHOLD`() {
        // Anti-overreach: "al parque" no sondea vacuna; el piso se acota
        // al destino `veterinari[oa]s?` — "llevar al gato al parque"
        // sigue sin piso.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar al gato al parque mañana", 1000)
        )
        if (intent != null) {
            assertNotEquals(ContextIntentKind.HOUSEHOLD, intent.kind)
        }
    }

    @Test
    fun `objeto no mascota no roba HOUSEHOLD`() {
        // "la planta" no es mascota: ni perro ni gato activan el piso.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar la planta al veterinario mañana", 1000)
        )
        if (intent != null) {
            assertNotEquals(ContextIntentKind.HOUSEHOLD, intent.kind)
        }
    }
}
