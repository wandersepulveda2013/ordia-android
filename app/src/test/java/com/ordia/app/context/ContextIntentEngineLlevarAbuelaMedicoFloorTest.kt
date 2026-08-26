package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1184: lateral P1/P2 de MI propia sonda c.1178
 * (`tools/probe/MedicalRunMamaProbe.kt`, pines P1 «abuela»/P2 «abuelo»
 * medidos NULL) — abuelos del piso médico c.776. Cuidado de mayores:
 * la cita médica del abuelo/a es tan real como la del padre/madre
 * (c.1178) o la del hijo (c.1176); el cuidador que acompaña al abuelo
 * a su consulta es la tercera generación de la diligencia familiar.
 * PRE medido (sonda efímera sobre HEAD c218e5d8): 4/4 capturas NULL
 * (mi abuela, la abuela, mi abuelo, los abuelos), guards 3/3 NULL
 * (negación, pasado «llevé», subjuntivo «quizá lleve»), pines 3/3 NULL
 * (mujer/marido/suegra — laterales que siguen FUERA), regresiones 3/3
 * HIT (mamá c.1178, hijo c.1176, niña c.776), envolventes temporales
 * 2/2 NULL.
 * Fix lockstep 2 puntos (lección c.616; CERO keywords nuevas — el piso
 * da MINIMUM_CONFIDENCE por sí solo, gate c.751): objeto del piso `+
 * abuel[oa]s?` en `ERRAND_MEDICAL_RUN_FLOOR` + MISMO objeto en
 * `matchMedicalRun`. UNA forma por ciclo (anti-overreach):
 * mujer/marido/suegros quedan FUERA pineados (laterales ABIERTAS).
 * Re-pin legítimo de MI pin c.1178 («Abuelos/esposa FUERA pineados») —
 * doctrina c.1133/c.1141/c.1144/c.1172: el pin fue deliberado por
 * una-forma-por-ciclo, no por falsedad.
 */
class ContextIntentEngineLlevarAbuelaMedicoFloorTest {

    // ---- Capturas directas (piso) ----

    @Test
    fun `captura mi abuela medico manana`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a mi abuela al médico mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a mi abuela al médico", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura la abuela medico`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a la abuela al médico", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a la abuela al médico", intent.title)
    }

    @Test
    fun `captura presente primera persona abuelo lunes`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevo a mi abuelo al médico el lunes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevo a mi abuelo al médico", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura plural los abuelos doctor`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los abuelos al doctor mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los abuelos al doctor", intent.title)
        assertNotNull(intent.dueAt)
    }

    // ---- Envolventes temporales ----

    @Test
    fun `captura anclaje temporal prefijo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana llevar a mi abuela al médico", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a mi abuela al médico", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura destino consulta`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a mi abuela a la consulta el viernes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a mi abuela a la consulta", intent.title)
        assertNotNull(intent.dueAt)
    }

    // ---- Guards (NULL) ----

    @Test
    fun `guard negacion abuela`() {
        assertNull(ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no llevar a mi abuela al médico", 1000)))
    }

    @Test
    fun `guard pasado abuelo`() {
        assertNull(ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevé a mi abuelo al médico ayer", 1000)))
    }

    @Test
    fun `guard duda subjuntivo`() {
        assertNull(ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá la lleve al médico", 1000)))
    }

    // ---- Pines anti-overreach (NULL; laterales ABIERTAS) ----

    @Test
    fun `pin esposa fuera`() {
        assertNull(ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a mi mujer al médico mañana", 1000)))
    }

    @Test
    fun `pin marido fuera`() {
        assertNull(ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a mi marido al médico mañana", 1000)))
    }

    @Test
    fun `pin suegra fuera`() {
        assertNull(ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a la suegra al médico mañana", 1000)))
    }

    // ---- Regresiones hermanas (HIT) ----

    @Test
    fun `regresion mama c1178`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a mamá al médico mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `regresion hijo c1176`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a mi hijo al médico mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `regresion nina c776`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a la niña al dentista hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    // ---- DueAt/título con acuse ----

    @Test
    fun `titulo limpio con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "tengo que llevar a mi abuela al médico mañana", 1000)
        )
        assertNotNull(intent)
        // Envolvente acuse: el floor genérico («tengo que …») gana TASK
        // (canario heredado de c.1178: «recuérdame…» medía TASK 0.54).
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Llevar a mi abuela al médico", intent.title)
        assertNotNull(intent.dueAt)
    }
}
