package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1141: lateral (a-quater) de la candidata (a) clase DECIMODUARTA (cerradas
 * parciales c.1128 «merienda» / c.1129 «almuerzo» / c.1133 «dinero de la
 * excursión») — objeto de ACARREO ESCOLAR «la ropa de recambio» del piso
 * «llevar a los niños al colegio» c.773 (`ERRAND_SCHOOL_RUN_FLOOR`). NULL PRE
 * medido por la sonda persistida c.1127 `tools/probe/FourteenthClassSchoolProbe.kt`
 * (C16: «llevar la ropa de recambio al colegio mañana» NULL), re-medido en el
 * PRE de este ciclo con sonda efímera (5/5 NULL: guardería/colegio/cole/llevo/
 * acuse+prefijo temporal) y pinneado NULL en el test c.1133 (`objeto ropa de
 * recambio sigue fuera`, re-pin legítimo de ESTE ciclo, precedente
 * c.1035/c.1041/c.1094). Olvido silencioso P1: la ropa de recambio olvidada en
 * casa. UNA forma por ciclo: el objeto hermano («proyecto de ciencias») queda
 * FUERA como lateral (a-quinquies) — ver pin.
 */
class ContextIntentEngineLlevarRopaRecambioGuarderiaFloorTest {

    // ---- Capturas directas (piso) ----

    @Test
    fun `captura ropa recambio guarderia manana`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar la ropa de recambio a la guardería mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar la ropa de recambio a la guardería", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura ropa recambio colegio manana`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar la ropa de recambio al colegio mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar la ropa de recambio al colegio", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura presente llevo cole`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevo la ropa de recambio al cole mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevo la ropa de recambio al cole", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, llevar la ropa de recambio a la guardería esta tarde", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar la ropa de recambio a la guardería", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana llevar la ropa de recambio a la escuela", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar la ropa de recambio a la escuela", intent.title)
        assertNotNull(intent.dueAt)
    }

    // ---- Envolvente: c.613 gobierna TASK ----

    @Test
    fun `envolvente c613 gobierna TASK`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame llevar la ropa de recambio a la guardería", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Llevar la ropa de recambio a la guardería", intent.title)
    }

    // ---- Guards ----

    @Test
    fun `no llevar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no llevar la ropa de recambio a la guardería mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quizá llevar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá llevar la ropa de recambio al colegio mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado lleve descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevé la ropa de recambio a la guardería ayer", 1000)
        )
        assertNull(intent)
    }

    // ---- Pines (anti-solape / una forma por ciclo) ----

    @Test
    fun `hermana dinero c1133 intacta`() {
        // Regresión byte-idéntica de c.1133.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar el dinero de la excursión al colegio mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar el dinero de la excursión al colegio", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `forma original niños intacta`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los niños al colegio mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños al colegio", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `objeto proyecto de ciencias sigue fuera`() {
        // Lateral (a-quinquies): UNA forma por ciclo.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar el proyecto de ciencias al cole mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `ropa sin destino sigue fuera`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar la ropa de recambio mañana", 1000)
        )
        assertNull(intent)
    }
}
