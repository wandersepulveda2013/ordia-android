package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.772: forma "medir la tensión" (sonda `tools/probe/FifthClassLifeProbe.kt`,
 * QUINTA clase — salud/autocuidado; elegida por dispersión determinista
 * epoch-day 20685 % 5 = 0 sobre el pool OPEN residual de 5 ítems). NULL PRE
 * verificado por la sonda sobre HEAD 0990f7b (también NULL todas las demás
 * variantes). El autocontrol de la tensión arterial es autocuidado de vida:
 * capturarlo evita el olvido (hermano de "tomar la medicina" c.765 y de
 * "ponerse la insulina" c.766). Piso TASK acotado al objeto `tensi[oó]n`
 * (el verbo "medir" es bivalente: la mesa/el espacio/el rendimiento quedan
 * FUERA — una forma por ciclo, doctrina de la sonda). SIN plural: "las
 * tensiones (del equipo/familiares)" son fricciones interpersonales, otra
 * semántica. Lockstep keyword-OBJETO "tensión" (lección c.713/c.751/c.765;
 * NO el verbo) + plantilla "(medir) la tensión". Kind: TASK (deliberación
 * contra APPOINTMENT/HABIT: no hay cita con profesional ni programa de
 * hábito — es un acto puntual de autocuidado). Negación sin cláusula
 * dedicada: keyword 0.12 + bono temporal 0.1 = 0.22 < umbral (hermana
 * c.765/c.766/c.768/c.771).
 */
class ContextIntentEngineMedirTensionFloorTest {

    @Test
    fun `captura base hoy`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "medir la tensión hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Medir la tensión", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con fecha manana`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "medir la tensión mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Medir la tensión", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse y dia`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, medir la tensión hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Medir la tensión", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana medir la tensión", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Medir la tensión", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura sin tilde`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "medir la tension hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        // El título preserva la grafía del usuario (doctrina c.653).
        assertEquals("Medir la tension", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `envolvente gobierna task`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame medir la tensión esta noche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `negada descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no medir la tensión mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `duda descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá medir la tensión mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "medí la tensión ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `objeto bivalente mesa descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "medir la mesa mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `suelto descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "medir", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `sustantivo suelto descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "la tensión está alta", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `regresion tomar la medicina intacto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "tomar la medicina mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `regresion reiniciar el router intacto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "reiniciar el router hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}
