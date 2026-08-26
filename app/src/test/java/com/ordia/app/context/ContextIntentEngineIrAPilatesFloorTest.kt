package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

// c.1232: «ir a pilates» (lateral (d) MEDIA de auditoría c.1227 cl.XXX deporte).
// Lockstep DOS puntos (lección c.616; gate c.751 — keyword-OBJETO
// monosemántica «pilates»; grafía preservada c.653). Guards pinadas en
// pretérito/negación (el piso es posición-libre como «natación»/«pesas»).
class ContextIntentEngineIrAPilatesFloorTest {

    private fun analyze(text: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1000)
        )

    // T1
    @Test
    fun `ir a pilates el lunes es exercise`() {
        val intent = analyze("ir a pilates el lunes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent?.kind)
        assertEquals("Ir a pilates", intent?.title)
    }

    // T2
    @Test
    fun `ir a pilates manana es exercise`() {
        val intent = analyze("ir a pilates mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent?.kind)
        assertEquals("Ir a pilates", intent?.title)
    }

    @Test
    fun `guardia preterito fui es null`() {
        assertNull(analyze("fui a pilates ayer"))
    }

    @Test
    fun `guardia negacion no voy es null`() {
        assertNull(analyze("no voy a pilates"))
    }

    // T3
    @Test
    fun `empezar pilates la semana que viene es exercise`() {
        val intent = analyze("empezar pilates la semana que viene")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent?.kind)
        assertNotNull(intent?.title)
    }

    // R1 regresión
    @Test
    fun `regresion ir al gimnasio sigue exercise`() {
        val intent = analyze("ir al gimnasio el lunes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent?.kind)
    }

    // R2 regresión
    @Test
    fun `regresion yoga sigue exercise`() {
        val intent = analyze("hacer yoga los martes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent?.kind)
    }

    // Envolvente
    @Test
    fun `envolvente recuerdo es task`() {
        val intent = analyze("recuérdame ir a pilates el lunes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent?.kind)
    }
}
