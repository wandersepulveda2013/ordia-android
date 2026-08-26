package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1157b: complemento lateral del piso c.1157 «llevar el portátil/ordenador
 * al trabajo» (canónico del hermano — primer-push-gana, doctrina c.1111;
 * colisión convergente: mi implementación paralela se retiró, precedente
 * c.1138/c.1149). UNA lateral por ciclo: destino «a la oficina» (declarada
 * ABIERTA en el docblock c.1157). PRE medido con sonda efímera
 * /tmp/probe1157oficina/Probe.kt sobre HEAD 62e34ba0 (motor real): 4/4
 * capturas NULL (oficina mañana/lunes/acuse/«llevo»), 10/10 guards NULL,
 * 3/3 regresiones HIT. Fix lockstep 2 puntos (lección c.616): extensión del
 * destino en `ERRAND_WORK_DEVICE_FLOOR` y en la plantilla
 * `matchWorkDeviceRun` (`al trabajo | a la oficina`); CERO keywords nuevas
 * (gate c.751 satisfecho por «llevar», keyword TASK histórica). Laterales
 * que SIGUEN ABIERTAS (pineadas NULL): «al curro», plural «los portátiles»,
 * objeto «tablet», «llevar el móvil a la oficina».
 */
class ContextIntentEngineLlevarPortatilOficinaFloorTest {

    // ---- Capturas directas (complemento de destino) ----

    @Test
    fun `captura portatil oficina manana`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar el portátil a la oficina mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar el portátil a la oficina", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura ordenador oficina el lunes`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar el ordenador a la oficina el lunes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar el ordenador a la oficina", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, llevar el portátil a la oficina", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar el portátil a la oficina", intent.title)
        assertNull(intent!!.dueAt)
    }

    @Test
    fun `captura presente llevo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevo el portátil a la oficina mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevo el portátil a la oficina", intent.title)
        assertNotNull(intent.dueAt)
    }

    // ---- Guards (siguen descartadas) ----

    @Test
    fun `guard negacion no llevar`() {
        assertNull(ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no llevar el portátil a la oficina", 1000)
        ))
    }

    @Test
    fun `guard negacion no lleves`() {
        assertNull(ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no lleves el portátil a la oficina", 1000)
        ))
    }

    @Test
    fun `guard pasado lleve`() {
        assertNull(ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevé el portátil a la oficina ayer", 1000)
        ))
    }

    @Test
    fun `guard subjuntivo duda`() {
        assertNull(ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá lleve el portátil a la oficina", 1000)
        ))
    }

    @Test
    fun `guard destino no laboral playa`() {
        assertNull(ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar el portátil a la playa", 1000)
        ))
    }

    @Test
    fun `guard lateral curro abierta`() {
        assertNull(ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar el portátil al curro mañana", 1000)
        ))
    }

    @Test
    fun `guard lateral plural abierta`() {
        assertNull(ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar los portátiles a la oficina", 1000)
        ))
    }

    @Test
    fun `guard sin destino`() {
        assertNull(ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar el portátil mañana", 1000)
        ))
    }

    @Test
    fun `guard objeto persona`() {
        assertNull(ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a María a la oficina", 1000)
        ))
    }

    @Test
    fun `guard objeto perro`() {
        assertNull(ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar el perro a la oficina", 1000)
        ))
    }

    // ---- Regresiones (canónico c.1157 y pisos hermanos, byte-idénticas) ----

    @Test
    fun `regresion trabajo canonica c1157`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar el portátil al trabajo mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar el portátil al trabajo", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `regresion coche taller c684`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar el coche al taller", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar el coche al taller", intent.title)
    }

    @Test
    fun `regresion ninos cole c773`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los niños al cole", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños al cole", intent.title)
    }
}
