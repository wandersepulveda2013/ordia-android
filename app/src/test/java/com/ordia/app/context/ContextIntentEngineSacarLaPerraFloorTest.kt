package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.756: forma "sacar la perra al parque" (CUARTA clase, mascotas — sonda
 * `FourthClassVerbDiscoveryProbe.kt`, PRE NULL) — extensión del piso
 * `HOUSEHOLD_PET_FLOOR` c.740: alternancia de ARTÍCULO DIRECTO
 * `(el|la|los|las|mi|tu|su)` añadida a las formas con "al"/"a+(el|la|...)"
 * ya cubiertas (variante conversacional "sacar la perra" vs "sacar al
 * perro"; cero keywords nuevas: "perro"/"perra" existen desde c.740;
 * el acotamiento al objeto mascota `perr[oa]s?` se conserva íntegro).
 * Determinista (regex), sin IA fingida, anti-overreach (negada/duda/
 * pasado/verbo suelto/objeto no mascota descartados).
 */
class ContextIntentEngineSacarLaPerraFloorTest {

    @Test
    fun `captura sacar la perra al parque`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "sacar la perra al parque mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Sacar la perra al parque", intent.title)
    }

    @Test
    fun `captura artículo directo masculino el perro`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "sacar el perro hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Sacar el perro", intent.title)
    }

    @Test
    fun `captura plural directo las perras`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "sacar las perras esta noche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Sacar las perras", intent.title)
    }

    @Test
    fun `captura posesivo directo mi perra`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "sacar mi perra mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Sacar mi perra", intent.title)
    }

    @Test
    fun `regresión c740 con al perro intacta`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "sacar al perro mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Sacar al perro", intent.title)
    }

    @Test
    fun `no sacar la perra descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no sacar la perra mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quizá sacar la perra descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá sacar la perra mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado saqué la perra descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "saqué la perra ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `objeto no mascota la cuenta descartado`() {
        // Anti kind-drift (familia c.728/c.731): el artículo directo NUEVO
        // NO debe robar objetos ajenos — "la cuenta" no es mascota.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "sacar la cuenta hoy", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `envolvente recuérdame gobierna TASK`() {
        // Guard centralizado vía WRAPPABLE_PATTERNS[HOUSEHOLD] =
        // HOUSEHOLD_FLOORS (lección lockstep c.648/c.652).
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame sacar la perra mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Sacar la perra", intent.title)
    }
}
