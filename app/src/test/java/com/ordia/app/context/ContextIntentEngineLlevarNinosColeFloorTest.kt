package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.850: diagonal COLOQUIAL «cole» del piso «llevar a los niños al colegio»
 * c.773 — candidata 2/6 de la sonda persistida c.845
 * `tools/probe/SeventhClassErrandProbe.kt` (NULL PRE verificado por la sonda:
 * «llevar a los niños al cole mañana» caía a NULL mientras «…al colegio
 * mañana» capturaba ERRAND — asimetría coloquial: «cole» es LA forma del
 * habla cotidiana, hermana de la asimetría de artículo c.848). Fix barato:
 * la lista de destinos educativos del piso `ERRAND_SCHOOL_RUN_FLOOR` admite
 * «cole» en los 2 puntos lockstep (piso + plantilla de título, lección
 * c.717); keyword-OBJETO «niños» ya existe (c.773) → lockstep coste-cero.
 * c.852: el acotamiento «al parque queda fuera» (destino de ocio NO
 * educativo, restricción deliberada c.773) se LEVANTA deliberadamente
 * (candidata 3/6 de la misma sonda, una forma por ciclo) — cobertura en
 * `ContextIntentEngineLlevarNinosParqueFloorTest.kt`.
 */
class ContextIntentEngineLlevarNinosColeFloorTest {

    // ---- Capturas directas (piso) ----

    @Test
    fun `captura cole manana`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los niños al cole mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños al cole", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura presente llevo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevo a los niños al cole mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevo a los niños al cole", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, llevar a los niños al cole mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños al cole", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana llevar a los niños al cole", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños al cole", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura mis ninos tarde`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a mis niños al cole esta tarde", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a mis niños al cole", intent.title)
        assertNotNull(intent.dueAt)
    }

    // ---- Envolvente: c.613 gobierna TASK (lockstep WRAPPABLE_PATTERNS) ----

    @Test
    fun `envolvente c613 gobierna TASK`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame llevar a los niños al cole", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Llevar a los niños al cole", intent.title)
    }

    // ---- Guards (contratos anti-overreach / anti-falsos-positivos) ----

    @Test
    fun `no llevar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no llevar a los niños al cole mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quizá llevar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá llevar a los niños al cole mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado lleve descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevé a los niños al cole ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `destino ocio parque captura desde c852`() {
        // Regresión cruzada c.852: la restricción deliberada c.773/c.850
        // («al parque» — ocio familiar — fuera) se levantó como candidata
        // 3/6 de la sonda c.845 (una forma por ciclo); la cobertura plena
        // vive en `ContextIntentEngineLlevarNinosParqueFloorTest.kt` y este
        // test queda como guard de no-regresión de la captura compartida.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los niños al parque mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños al parque", intent.title)
    }

    // ---- Regresiones del piso c.773 (destinos educativos intactos) ----

    @Test
    fun `regresión colegio intacta`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los niños al colegio mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños al colegio", intent.title)
    }

    @Test
    fun `regresión guardería intacta`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevo a mis niños a la guardería mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevo a mis niños a la guardería", intent.title)
    }
}
