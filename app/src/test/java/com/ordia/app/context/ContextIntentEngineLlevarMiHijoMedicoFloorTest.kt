package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1176: lateral espejo P2/P3 del piso médico c.776, registrada al
 * cerrar c.1172 — objeto hij[oa]s?. El piso `ERRAND_MEDICAL_RUN_FLOOR`
 * ya admite el posesivo singular `mi|tu|su` en el alternador (desde
 * c.776: «llevar a mi niña al médico») pero el objeto era SOLO
 * `niñ[oa]s?` — asimetría con el piso escolar (cerrada en c.1172:
 * `niñ[oa]s?|hij[oa]s?`) y con el aeropuerto c.1158 (parentesco
 * completo). «llevar a mi hijo al médico mañana» caía a NULL (medido
 * como pin P3 de la sonda SchoolRunMiHijoProbe POST c.1172 y como pin
 * `pin mi hijo al medico fuera` de la clase escolar hermana — re-pin
 * legítimo documentado, doctrina c.1133/c.1141/c.1144/c.1172).
 * PRE medido (sonda persistida `tools/probe/MedicalRunMiHijoProbe.kt`
 * sobre el motor real): 6/6 capturas NULL (C1-C6), guards 4/4 NULL
 * (negación ×2, pasado «llevé», duda subjuntivo «quizá lleve»), pines
 * 2/2 NULL («mamá» otro parentesco FUERA deliberado c.776, destino no
 * médico «al banco»), regresiones 6/6 HIT, envolvente «recuérdame…»
 * TASK 0.54 con título ya correcto (canario verde en PRE — camino
 * genérico c.613).
 * Fix lockstep 2 puntos (lección c.616; CERO keywords nuevas — «niños»
 * ya es keyword ERRAND c.773 y el piso da MINIMUM_CONFIDENCE por sí
 * solo, gate c.751 satisfecho): objeto del piso `niñ[oa]s?` →
 * `(?:niñ[oa]s?|hij[oa]s?)` + MISMO objeto en la plantilla
 * `matchMedicalRun` de [ContextIntentEngine.extractTitle]. UNA forma
 * por ciclo (anti-overreach): «llevar a mamá al médico» (otro
 * parentesco) queda FUERA pineado (deliberado c.776).
 */
class ContextIntentEngineLlevarMiHijoMedicoFloorTest {

    // ---- Capturas directas (piso) ----

    @Test
    fun `captura mi hijo medico manana`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a mi hijo al médico mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a mi hijo al médico", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura mi hija medico viernes`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a mi hija al médico el viernes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a mi hija al médico", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura presente primera persona pediatra`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevo a mi hijo al pediatra esta tarde", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevo a mi hijo al pediatra", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura tu hija dentista`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a tu hija al dentista mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a tu hija al dentista", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura su hijo hospital`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a su hijo al hospital el lunes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a su hijo al hospital", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura los hijos plural`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los hijos al médico mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los hijos al médico", intent.title)
        assertNotNull(intent.dueAt)
    }

    // ---- Guards (NULL siempre) ----

    @Test
    fun `negacion no llevar mi hijo descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no llevar a mi hijo al médico", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `negacion no llevo mi hija descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no llevo a mi hija al médico", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado lleve mi hijo descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevé a mi hijo al médico ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `duda subjuntivo lleve descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá lleve a mi hijo al médico", 1000)
        )
        assertNull(intent)
    }

    // ---- Pines anti-overreach (NULL deliberado) ----

    @Test
    fun `pin otro parentesco mama fuera`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a mamá al médico mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pin destino no medico fuera`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a mi hijo al banco mañana", 1000)
        )
        assertNull(intent)
    }

    // ---- Regresiones (misma región de regex) ----

    @Test
    fun `regresion la nina medico c776`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a la niña al médico mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a la niña al médico", intent.title)
    }

    @Test
    fun `regresion mi nina medico c776`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a mi niña al médico mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a mi niña al médico", intent.title)
    }

    @Test
    fun `regresion escolar c1172`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a mi hija a la fiesta del cole el viernes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a mi hija a la fiesta del cole", intent.title)
    }

    @Test
    fun `regresion aeropuerto c1158`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a mi hijo al aeropuerto mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
    }

    // ---- Envolvente ----

    @Test
    fun `envolvente recuerdame captura`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame llevar a mi hijo al médico mañana", 1000)
        )
        assertNotNull(intent)
    }
}
