package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Lateral (a) FUERTE c.1237 de MI auditoría c.1236 (clase XXXI
 * tecnología/informática): «escanear + objeto documental profesional
 * (informe/documentos)» era NULL medido porque el piso c.864 acota su
 * alternancia de objeto a (dni|contratos?|notas?|código qr). Keyword
 * «escanear» ya existía (c.864) — gate c.751 satisfecho SIN keyword
 * nueva: el fix solo amplía la alternancia del piso (anti-overreach:
 * extensión ACOTADA, el resto de objetos desnudos sigue FUERA).
 * TDD RED→GREEN; DISJUNTO del marcador del hermano (c.1235
 * «entrenamiento de <deporte>»).
 */
class ContextIntentEngineEscanearInformeFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    @Test
    fun `escanear documentos profesionales captura TASK con titulo limpio`() {
        val r1 = analyze("escanear el informe mañana")
        assertNotNull("«escanear el informe mañana» debe capturar (era NULL en c.1236)", r1)
        assertEquals(ContextIntentKind.TASK, r1!!.kind)
        assertEquals("Escanear el informe", r1.title)
        assertNotNull("«mañana» debe anclar dueAt", r1.dueAt)

        val r2 = analyze("escanear el documento hoy")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.TASK, r2!!.kind)
        assertEquals("Escanear el documento", r2.title)
        assertNotNull(r2.dueAt)

        val r3 = analyze("escanear los documentos esta semana")
        assertNotNull(r3)
        assertEquals(ContextIntentKind.TASK, r3!!.kind)
        // «esta semana» se conserva en el título (no ancla dueAt en
        // posición posterior; lección c.616: el match arranca en el verbo)
        assertEquals("Escanear los documentos esta semana", r3.title)

        val r4 = analyze("escanear los informes mañana")
        assertNotNull(r4)
        assertEquals(ContextIntentKind.TASK, r4!!.kind)
        assertEquals("Escanear los informes", r4.title)
    }

    @Test
    fun `prefijos de acuse y temporal se despojan del titulo`() {
        val r1 = analyze("vale, escanear el documento")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.TASK, r1!!.kind)
        assertEquals("Escanear el documento", r1.title)

        val r2 = analyze("mañana escanear el informe")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.TASK, r2!!.kind)
        assertEquals("Escanear el informe", r2.title)

        val r3 = analyze("recuérdame escanear el documento")
        assertNotNull(r3)
        assertEquals(ContextIntentKind.TASK, r3!!.kind)
        assertEquals("Escanear el documento", r3.title)
    }

    @Test
    fun `guards anti-overreach permanecen NULL`() {
        assertNull("negación", analyze("no escanear el informe mañana"))
        assertNull("duda (hedge c.649)", analyze("quizá escanear el documento mañana"))
        assertNull("narrativa pasado", analyze("escaneé el informe ayer"))
        assertNull("sustantivo suelto", analyze("el informe está completo"))
    }

    @Test
    fun `regresiones hermanas del piso c864 permanecen estables`() {
        val r1 = analyze("escanear el DNI mañana")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.TASK, r1!!.kind)

        val r2 = analyze("escanear el contrato mañana")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.TASK, r2!!.kind)
    }
}
