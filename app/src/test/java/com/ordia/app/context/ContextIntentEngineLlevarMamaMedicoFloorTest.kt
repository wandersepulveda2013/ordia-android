package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1178: lateral de c.1176 — parentesco adulto directo del piso médico
 * c.776: «llevar a mamá/papá/madre/padre al médico». El pin «llevar a
 * mamá al médico» de c.776 fue deliberado SOLO por doctrina
 * una-forma-por-ciclo (no por falsedad); P1 evitar olvidos: la cita
 * médica del padre/madre es la diligencia del cuidador adulto (llevar
 * al padre mayor a su consulta es tan real como llevar al hijo al
 * pediatra — hermano EXACTO del objeto cerrado en c.1176).
 * PRE medido (sonda persistida `tools/probe/MedicalRunMamaProbe.kt`
 * sobre el motor real): 6/6 capturas NULL (C1-C6), guards 3/3 NULL
 * (negación, pasado «llevé», duda subjuntivo «quizá lleve»), pines 2/2
 * NULL (abuela/esposa FUERA este ciclo), regresiones 4/4 HIT (incl. R3
 * «mi mamá al aeropuerto» c.1158), envolvente «recuérdame…» TASK 0.54
 * con título ya correcto (canario verde en PRE).
 * Fix lockstep 2 puntos (lección c.616; CERO keywords nuevas — el piso
 * da MINIMUM_CONFIDENCE por sí solo, gate c.751): objeto del piso
 * `(?:niñ[oa]s?|hij[oa]s?)` → `+ mam[áa]|pap[áa]|madre|padre` en
 * `ERRAND_MEDICAL_RUN_FLOOR` + MISMO objeto en `matchMedicalRun`.
 * UNA forma por ciclo (anti-overreach): abuelos/suegros/esposa quedan
 * FUERA pineados. Re-pin legítimo del pin P1 de MI sonda c.1176
 * («llevar a mamá al médico», doctrina c.1133/c.1141/c.1144/c.1172) y
 * del test `pin otro parentesco mama fuera` de la clase c.1176.
 */
class ContextIntentEngineLlevarMamaMedicoFloorTest {

    // ---- Capturas directas (piso) ----

    @Test
    fun `captura mama medico manana`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a mamá al médico mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a mamá al médico", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura papa medico viernes`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a papá al médico el viernes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a papá al médico", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura presente primera persona madre`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevo a mi madre al médico esta tarde", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevo a mi madre al médico", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura mi padre hospital`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a mi padre al hospital el lunes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a mi padre al hospital", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura tu madre dentista`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a tu madre al dentista mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a tu madre al dentista", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura la madre sin posesivo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a la madre al médico mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a la madre al médico", intent.title)
        assertNotNull(intent.dueAt)
    }

    // ---- Guards (NULL siempre) ----

    @Test
    fun `negacion no llevar mama descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no llevar a mamá al médico", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado lleve papa descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevé a papá al médico ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `duda subjuntivo lleve madre descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá lleve a mi madre al médico", 1000)
        )
        assertNull(intent)
    }

    // ---- Pines anti-overreach (NULL deliberado) ----

    @Test
    fun `pin abuela fuera`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a mi abuela al médico mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pin esposa fuera`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a mi mujer al médico mañana", 1000)
        )
        assertNull(intent)
    }

    // ---- Regresiones (misma región de regex) ----

    @Test
    fun `regresion mi hijo medico c1176`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a mi hijo al médico mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a mi hijo al médico", intent.title)
    }

    @Test
    fun `regresion la nina medico c776`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a la niña al médico mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
    }

    @Test
    fun `regresion mama aeropuerto c1158`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a mi mamá al aeropuerto mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
    }

    @Test
    fun `regresion ninos colegio c773`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los niños al colegio mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
    }

    // ---- Envolvente ----

    @Test
    fun `envolvente recuerdame captura`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame llevar a mamá al médico mañana", 1000)
        )
        assertNotNull(intent)
    }
}
