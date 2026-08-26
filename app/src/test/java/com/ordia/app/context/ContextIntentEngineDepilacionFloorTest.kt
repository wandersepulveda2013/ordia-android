package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1258: «(la |mi )?depilaci[oó]n» — nominal de servicio
 * (lateral (c) MEDIA de MI auditoría c.1252 — clase TRIGÉSIMA SEXTA
 * belleza/cuidado personal). Gate c.751 (objeto monosemántico-servicio —
 * la depilación es sesión inequívoca de cuidado; CERO keyword nueva —
 * floor-only, precedente «partido» c.1231 / «peluquería» c.1256 /
 * «manicura» c.1257). Kind hermano de c.1256/c.1257: ERRAND
 * (desplazamiento a la sesión). «Cera» FUERA (polisémica: vela/coche/
 * oído — gate c.751 la excluye, sonda G2; «depilación con cera» queda
 * cubierta por el nominal). Sonda persistida
 * `tools/probe/DepilacionProbe.kt` (PRE 6/6 NULL targets — olvido
 * silencioso). Lockstep: extensión del piso ERRAND_BEAUTY_RUN_FLOOR +
 * plantilla matchBeautyRun en extractTitle + guard
 * pastErrandCopulaGoverns (cubre el nominal nuevo por la misma
 * constante). Kind: ERRAND (TASK sólo en envolvente
 * «recuérdame»/«tengo que», lección de archivo del wrapper).
 */
class ContextIntentEngineDepilacionFloorTest {

    @Test
    fun `captura la depilacion articulo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "la depilación el viernes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("La depilación", intent.title)
    }

    @Test
    fun `captura depilacion desnuda`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "depilación el sábado", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Depilación", intent.title)
    }

    @Test
    fun `captura mi depilacion posesivo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mi depilación mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Mi depilación", intent.title)
    }

    @Test
    fun `captura cita para la depilacion`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cita para la depilación el jueves", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("La depilación", intent.title)
    }

    @Test
    fun `captura la depilacion sin tilde`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "la depilacion el lunes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("La depilacion", intent.title)
    }

    @Test
    fun `captura depilacion con cera`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "depilación con cera mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Depilación con cera", intent.title)
    }

    @Test
    fun `guard negacion no voy a la depilacion`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no voy a la depilación mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `guard cera polisemica velas`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "la cera de las velas", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `guard copulativa pasada depilacion fue ayer`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "la depilación fue ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `regresion hermana manicura`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "la manicura el viernes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("La manicura", intent.title)
    }

    @Test
    fun `regresion hermana peluqueria`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "peluquería el martes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Peluquería", intent.title)
    }

    @Test
    fun `regresion envolvente recuerdame`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}
