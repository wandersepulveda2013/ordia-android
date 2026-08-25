package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1128: objeto de ACARREO ESCOLAR «merienda» del piso «llevar a los niños
 * al colegio» c.773 (`ERRAND_SCHOOL_RUN_FLOOR`; diagonal «cole» c.850,
 * destino «parque» c.852) — candidata (a) de la clase DECIMODUARTA (vida
 * escolar de los hijos), descubierta por la sonda persistida c.1127
 * `tools/probe/FourteenthClassSchoolProbe.kt` (NULL PRE medido por la sonda
 * sobre el motor real: «llevar la merienda al colegio mañana» caía a NULL
 * mientras «llevar a los niños al colegio» capturaba ERRAND — el deber
 * escolar cotidiano de preparar/llevar la merienda se perdía en silencio;
 * «llevar» es bivalente y está deliberadamente FUERA de `ERRAND_VERBS`).
 * El piso sólo admitía el objeto `niñ[oa]s?`; la alternancia de objeto
 * admite ahora «merienda» en los 2 puntos lockstep (piso + plantilla de
 * título `matchSchoolRun`, lección c.616). CERO keywords nuevas: el piso
 * da 0.45 = MINIMUM_CONFIDENCE por sí solo vía [hasStrongErrandImperative]
 * (medido en la forma original: ERRAND 0.45). UNA forma por ciclo
 * (doctrina anti-overreach): los objetos hermanos medidos NULL en la sonda
 * («almuerzo», «dinero de la excursión», «ropa de recambio», «proyecto de
 * ciencias») quedan FUERA como laterales (a-bis … a-quinquies) — ver pins.
 */
class ContextIntentEngineLlevarMeriendaColegioFloorTest {

    // ---- Capturas directas (piso) ----

    @Test
    fun `captura merienda colegio manana`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar la merienda al colegio mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar la merienda al colegio", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura presente llevo cole`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevo la merienda al cole mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevo la merienda al cole", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, llevar la merienda al colegio mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar la merienda al colegio", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana llevar la merienda al colegio", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar la merienda al colegio", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura escuela tarde`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar la merienda a la escuela esta tarde", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar la merienda a la escuela", intent.title)
        assertNotNull(intent.dueAt)
    }

    // ---- Envolvente: c.613 gobierna TASK (WRAPPABLE_PATTERNS ← ERRAND_FLOORS, fuente única) ----

    @Test
    fun `envolvente c613 gobierna TASK`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame llevar la merienda al colegio", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Llevar la merienda al colegio", intent.title)
    }

    // ---- Guards (contratos anti-overreach / anti-falsos-positivos) ----

    @Test
    fun `no llevar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no llevar la merienda al colegio mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quizá llevar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá llevar la merienda al colegio mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado lleve descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevé la merienda al colegio ayer", 1000)
        )
        assertNull(intent)
    }

    // ---- Pines (anti-solape / una forma por ciclo) ----

    @Test
    fun `forma original niños intacta`() {
        // Regresión byte-idéntica del piso c.773: la alternancia de objeto
        // nueva no roba ni altera la forma original.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los niños al colegio mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños al colegio", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `objeto almuerzo sigue fuera`() {
        // Lateral (a-bis) medida NULL en la sonda c.1127: UNA forma por
        // ciclo — sólo «merienda» entra en ESTE ciclo.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar el almuerzo al colegio mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `destino no listado sigue fuera`() {
        // El destino del piso es lista cerrada (colegio/cole/escuela/
        // guardería/parque): «al trabajo» no es acarreo escolar.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar la merienda al trabajo mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `merienda sin destino sigue fuera`() {
        // «llevar» es bivalente (deliberadamente fuera de ERRAND_VERBS):
        // sin el destino escolar cerrado no hay señal suficiente.
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar la merienda mañana", 1000)
        )
        assertNull(intent)
    }
}
