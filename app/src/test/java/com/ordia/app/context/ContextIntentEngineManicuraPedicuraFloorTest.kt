package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1257: «(la |mi )?(manicura|pedicura)» — nominal de servicio
 * (lateral (b) MEDIA de MI auditoría c.1252 — clase TRIGÉSIMA SEXTA
 * belleza/cuidado personal). Gate c.751 (objeto monosemántico-servicio —
 * la manicura/pedicura es sesión inequívoca de cuidado; CERO keyword
 * nueva — floor-only, precedente «partido» c.1231 / «peluquería»
 * c.1256). Kind hermano de c.1256: ERRAND (desplazamiento a la sesión).
 * «Uñas»/«cejas» FUERA (partes corporales polisémicas — gate c.751 las
 * excluye, sonda G2). Sonda persistida
 * `tools/probe/ManicuraPedicuraProbe.kt` (PRE 6/6 NULL targets —
 * olvido silencioso). Lockstep: extensión del piso
 * ERRAND_BEAUTY_RUN_FLOOR + plantilla matchBeautyRun en extractTitle
 * + guard pastErrandCopulaGoverns (cubre los nominales nuevos por la
 * misma constante). Kind: ERRAND (TASK sólo en envolvente
 * «recuérdame»/«tengo que», lección de archivo del wrapper).
 */
class ContextIntentEngineManicuraPedicuraFloorTest {

    @Test
    fun `captura la manicura articulo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "la manicura el viernes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("La manicura", intent.title)
    }

    @Test
    fun `captura manicura desnuda`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "manicura el sábado", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Manicura", intent.title)
    }

    @Test
    fun `captura mi manicura posesivo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mi manicura mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Mi manicura", intent.title)
    }

    @Test
    fun `captura la pedicura articulo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "la pedicura el lunes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("La pedicura", intent.title)
    }

    @Test
    fun `captura pedicura desnuda`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "pedicura mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Pedicura", intent.title)
    }

    @Test
    fun `captura cita en la pedicura`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cita en la pedicura el jueves", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("La pedicura", intent.title)
    }

    // Guards (NULL esperado — anti-overreach)
    @Test
    fun `guard negacion plan no voy`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no voy a la manicura mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `guard unas corporales fuera`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "las uñas pintadas", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `guard copulativa pasada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "la manicura fue ayer", 1000)
        )
        assertNull(intent)
    }

    // Regresiones (HIT por fórmulas heredadas)
    @Test
    fun `regresion peluqueria hermana c1256`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "peluquería el martes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Peluquería", intent.title)
    }

    @Test
    fun `regresion recuedame task`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `regresion cortarme el pelo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cortarme el pelo el sábado", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
    }
}
