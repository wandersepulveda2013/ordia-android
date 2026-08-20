package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.728: forma "hacer la cama" (15/19 TERCERA clase, hogar 2/6) — piso
 * HOUSEHOLD acotado al objeto `cama` (misma familia que [HOUSEHOLD_TRASH_FLOOR]
 * "sacar la basura" c.717, porque "hacer" suelto es demasiado genérico para
 * posición libre; además "hacer" ya es keyword de TASK) + keyword "cama"
 * (lockstep keyword↔piso, lección c.639) + plantilla "(hacer) (la) cama(s)…"
 * →"Hacer la cama …" (lockstep lección c.713/c.717).
 * Kind: HOUSEHOLD (deliberación contra TASK/EXERCISE — quehacer doméstico
 * canónico; "hacer yoga" ya es EXERCISE de su propia vía; TASK queda solo
 * para envolvente c.613).
 */
class ContextIntentEngineHacerCamaFloorTest {

    @Test
    fun `captura hacer la cama plus fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "hacer la cama mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Hacer la cama", intent.title)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, hacer la cama hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Hacer la cama", intent.title)
    }

    @Test
    fun `captura plural camas`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "hacer las camas mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Hacer las camas", intent.title)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana hacer la cama", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Hacer la cama", intent.title)
    }

    @Test
    fun `no hacer descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no hacer la cama mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quizá hacer descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá hacer la cama mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado hice descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "hice la cama ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `hacer suelto descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "hacer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `hacer objeto no domestico no roba HOUSEHOLD`() {
        // "hacer el informe" es TASK (keyword "hacer" vía ContextIntent.kt) con
        // el verbo genérico: el piso HOUSEHOLD queda acotado a `cama(s)` y NO
        // lo captura el hogar (kind drift anti-overreach).
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "hacer el informe mañana", 1000)
        )
        if (intent != null) {
            assertNotEquals(ContextIntentKind.HOUSEHOLD, intent.kind)
        }
    }

    @Test
    fun `envolvente c613 gobierna TASK`() {
        // "recuérdame hacer la cama": el piso HOUSEHOLD se descarta vía
        // imperativeIsWrapped (WRAPPABLE_PATTERNS + HOUSEHOLD_FLOORS); el piso
        // TASK c.613 gobierna con template "recuérdame X"→"X".
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame hacer la cama", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer la cama", intent.title)
    }
}
