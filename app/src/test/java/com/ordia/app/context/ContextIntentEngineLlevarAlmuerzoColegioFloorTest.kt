package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1129: lateral (a-bis) de la candidata (a) clase DECIMODUARTA (cerrada
 * parcial c.1128 «merienda») — objeto de ACARREO ESCOLAR «el almuerzo» del
 * piso «llevar a los niños al colegio» c.773 (`ERRAND_SCHOOL_RUN_FLOOR`).
 * NULL PRE medido por la sonda persistida c.1127
 * `tools/probe/FourteenthClassSchoolProbe.kt` (C18: «llevar el almuerzo al
 * colegio mañana» NULL) y pinneado NULL en el test c.1128 (re-pin legítimo,
 * precedente c.1035/c.1041/c.1094). UNA forma por ciclo: los objetos
 * hermanos («dinero de la excursión», «ropa de recambio», «proyecto de
 * ciencias») quedan FUERA como laterales (a-ter … a-quinquies) — ver pins.
 */
class ContextIntentEngineLlevarAlmuerzoColegioFloorTest {

    // ---- Capturas directas (piso) ----

    @Test
    fun `captura almuerzo colegio manana`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar el almuerzo al colegio mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar el almuerzo al colegio", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura presente llevo cole`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevo el almuerzo al cole mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevo el almuerzo al cole", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, llevar el almuerzo al colegio mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar el almuerzo al colegio", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana llevar el almuerzo al colegio", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar el almuerzo al colegio", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura guarderia tarde`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar el almuerzo a la guardería esta tarde", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar el almuerzo a la guardería", intent.title)
        assertNotNull(intent.dueAt)
    }

    // ---- Envolvente: c.613 gobierna TASK ----

    @Test
    fun `envolvente c613 gobierna TASK`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame llevar el almuerzo al colegio", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Llevar el almuerzo al colegio", intent.title)
    }

    // ---- Guards ----

    @Test
    fun `no llevar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no llevar el almuerzo al colegio mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quizá llevar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá llevar el almuerzo al colegio mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado lleve descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevé el almuerzo al colegio ayer", 1000)
        )
        assertNull(intent)
    }

    // ---- Pines (anti-solape / una forma por ciclo) ----

    @Test
    fun `hermana merienda c1128 intacta`() {
        // Regresión byte-idéntica de c.1128.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar la merienda al colegio mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar la merienda al colegio", intent.title)
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
    fun `objeto dinero sigue fuera`() {
        // Lateral (a-ter): UNA forma por ciclo.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar el dinero de la excursión al colegio mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `almuerzo sin destino sigue fuera`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar el almuerzo mañana", 1000)
        )
        assertNull(intent)
    }
}
