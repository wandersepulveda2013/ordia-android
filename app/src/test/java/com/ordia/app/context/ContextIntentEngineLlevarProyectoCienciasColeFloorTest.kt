package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1144: lateral (a-quinquies) de la candidata (a) clase DECIMODUARTA (cerradas
 * parciales c.1128 «merienda» / c.1129 «almuerzo» / c.1133 «dinero de la
 * excursión» / c.1141 «ropa de recambio»), ÚLTIMA de la familia «llevar X al
 * cole» — objeto de ACARREO ESCOLAR «el proyecto de ciencias» del piso
 * «llevar a los niños al colegio» c.773 (`ERRAND_SCHOOL_RUN_FLOOR`). NULL PRE
 * medido por la sonda persistida c.1127 `tools/probe/FourteenthClassSchoolProbe.kt`
 * (C17: «llevar el proyecto de ciencias al colegio el viernes» NULL), re-medido
 * en el PRE de este ciclo con sonda efímera (6/6 NULL: colegio+viernes/cole+
 * mañana/guardería+llevo/acuse+escuela/sin temporal/prefijo temporal) y pinneado
 * NULL en el test c.1141 (`objeto proyecto de ciencias sigue fuera`, re-pin
 * legítimo de ESTE ciclo, precedente c.1035/c.1041/c.1094). Olvido silencioso
 * P1: el proyecto de ciencias olvidado en casa el día de la entrega. UNA forma
 * por ciclo: con este objeto la familia (a) queda AGOTADA (5/5 laterales
 * cerrados: merienda c.1128, almuerzo c.1129, dinero c.1133, ropa c.1141,
 * proyecto c.1144).
 */
class ContextIntentEngineLlevarProyectoCienciasColeFloorTest {

    // ---- Capturas directas (piso) ----

    @Test
    fun `captura proyecto ciencias colegio viernes`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar el proyecto de ciencias al colegio el viernes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar el proyecto de ciencias al colegio", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura proyecto ciencias cole manana`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar el proyecto de ciencias al cole mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar el proyecto de ciencias al cole", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura presente llevo guarderia`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevo el proyecto de ciencias a la guardería mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevo el proyecto de ciencias a la guardería", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, llevar el proyecto de ciencias a la escuela mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar el proyecto de ciencias a la escuela", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana llevar el proyecto de ciencias al cole", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar el proyecto de ciencias al cole", intent.title)
        assertNotNull(intent.dueAt)
    }

    // ---- Envolvente: c.613 gobierna TASK ----

    @Test
    fun `envolvente c613 gobierna TASK`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame llevar el proyecto de ciencias al cole", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Llevar el proyecto de ciencias al cole", intent.title)
    }

    // ---- Guards ----

    @Test
    fun `no llevar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no llevar el proyecto de ciencias al cole mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quizá llevar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá llevar el proyecto de ciencias al cole mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado lleve descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevé el proyecto de ciencias al cole ayer", 1000)
        )
        assertNull(intent)
    }

    // ---- Pines (anti-solape / una forma por ciclo) ----

    @Test
    fun `hermana ropa c1141 intacta`() {
        // Regresión byte-idéntica de c.1141.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar la ropa de recambio a la guardería mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar la ropa de recambio a la guardería", intent.title)
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
    fun `creo que hay que sigue TASK`() {
        // Pin byte-idéntico PRE (sonda efímera): «hay que» gobierna TASK,
        // igual que las hermanas ya capturadas (ropa/merienda/dinero).
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "creo que hay que llevar el proyecto de ciencias al cole", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Llevar el proyecto de ciencias al cole", intent.title)
        assertNull(intent!!.dueAt)
    }

    @Test
    fun `proyecto sin destino sigue fuera`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar el proyecto de ciencias mañana", 1000)
        )
        assertNull(intent)
    }
}
