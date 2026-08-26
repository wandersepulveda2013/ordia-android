package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1256: «(la |mi )?(peluquer[ií]a|barber[ií]a)» — nominal de lugar
 * FUERTE (lateral (a) de MI auditoría c.1252 — clase TRIGÉSIMA SEXTA
 * belleza/cuidado personal). Gate c.751 (objeto monosemántico-lugar —
 * la peluquería/barbería es destino inequívoco de recado; CERO keyword
 * nueva — floor-only, precedente «partido» c.1231 / «clase-fitness»
 * c.1250). Kind hermano de «cortar(me) el pelo» c.842: ERRAND
 * (desplazamiento). Sonda persistida
 * `tools/probe/PeluqueriaBarberiaProbe.kt` (PRE 6/6 NULL targets —
 * olvido silencioso). Lockstep DOS puntos (lección c.616): piso acotado
 * ERRAND_BARBERSHOP_RUN_FLOOR + plantilla matchBarbershopRun en
 * extractTitle. Kind: ERRAND (TASK sólo en envolvente
 * «recuérdame»/«tengo que», lección de archivo del wrapper).
 */
class ContextIntentEnginePeluqueriaBarberiaFloorTest {

    @Test
    fun `captura peluqueria desnuda`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "peluquería el martes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Peluquería", intent.title)
    }

    @Test
    fun `captura cita en la peluqueria`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cita en la peluquería el viernes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("La peluquería", intent.title)
    }

    @Test
    fun `captura la peluqueria articulo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "la peluquería mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("La peluquería", intent.title)
    }

    @Test
    fun `captura mi peluqueria posesivo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mi peluquería el viernes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Mi peluquería", intent.title)
    }

    @Test
    fun `captura barberia desnuda`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "barbería el sábado", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Barbería", intent.title)
    }

    @Test
    fun `captura la barberia articulo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "la barbería el lunes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("La barbería", intent.title)
    }

    // Guards (NULL esperado — anti-overreach)
    @Test
    fun `guard negacion plan no voy`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no voy a la peluquería mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `guard salon de belleza polisemico`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "el salón de belleza está cerrado", 1000)
        )
        assertNull(intent)
    }

    // Regresiones (HIT por fórmulas heredadas)
    @Test
    fun `regresion recuedame task`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `regresion ir al banco`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "ir al banco mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
    }

    @Test
    fun `regresion comprar leche`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "comprar leche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.SHOPPING, intent!!.kind)
    }

    @Test
    fun `regresion cita con el medico`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cita con el médico mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
    }
}
