package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1133: lateral (a-ter) de la candidata (a) clase DECIMODUARTA (cerradas
 * parciales c.1128 «merienda» / c.1129 «almuerzo») — objeto de ACARREO
 * ESCOLAR «el dinero de la excursión» del piso «llevar a los niños al
 * colegio» c.773 (`ERRAND_SCHOOL_RUN_FLOOR`). NULL PRE medido por la sonda
 * persistida c.1127 `tools/probe/FourteenthClassSchoolProbe.kt` (C19: «llevar
 * el dinero de la excursión al colegio mañana» NULL) y pinneado NULL en el
 * test c.1129 (`objeto dinero sigue fuera`, re-pin legítimo previsto,
 * precedente c.1035/c.1041/c.1094). UNA forma por ciclo: los objetos
 * hermanos («ropa de recambio», «proyecto de ciencias») quedan FUERA como
 * laterales (a-quater/a-quinquies) — ver pins.
 */
class ContextIntentEngineLlevarDineroExcursionColegioFloorTest {

    // ---- Capturas directas (piso) ----

    @Test
    fun `captura dinero excursion colegio manana`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar el dinero de la excursión al colegio mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar el dinero de la excursión al colegio", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura presente llevo cole`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevo el dinero de la excursión al cole mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevo el dinero de la excursión al cole", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, llevar el dinero de la excursión al colegio mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar el dinero de la excursión al colegio", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana llevar el dinero de la excursión al colegio", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar el dinero de la excursión al colegio", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura guarderia tarde`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar el dinero de la excursión a la guardería esta tarde", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar el dinero de la excursión a la guardería", intent.title)
        assertNotNull(intent.dueAt)
    }

    // ---- Envolvente: c.613 gobierna TASK ----

    @Test
    fun `envolvente c613 gobierna TASK`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame llevar el dinero de la excursión al colegio", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Llevar el dinero de la excursión al colegio", intent.title)
    }

    // ---- Guards ----

    @Test
    fun `no llevar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no llevar el dinero de la excursión al colegio mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quizá llevar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá llevar el dinero de la excursión al colegio mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado lleve descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevé el dinero de la excursión al colegio ayer", 1000)
        )
        assertNull(intent)
    }

    // ---- Pines (anti-solape / una forma por ciclo) ----

    @Test
    fun `hermana almuerzo c1129 intacta`() {
        // Regresión byte-idéntica de c.1129.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar el almuerzo al colegio mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar el almuerzo al colegio", intent.title)
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
    fun `objeto ropa de recambio sigue fuera`() {
        // Lateral (a-quater): UNA forma por ciclo.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar la ropa de recambio a la guardería mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `dinero sin destino sigue fuera`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar el dinero de la excursión mañana", 1000)
        )
        assertNull(intent)
    }
}
