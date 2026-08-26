package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1201 (este lado, UNA por ciclo) — unidad (c) ABIERTA de MI auditoría
 * c.1197 (clase VIGESIMOTERCERA finanzas domésticas): «adelantar la
 * mensualidad (el lunes/del viernes)» estaba NULL en el motor (olvido
 * silencioso P1: vía envolvente caía a TASK, directo caía sin piso).
 * Gate c.751 satisfecho: «mensualidad» ya es keyword de
 * [ContextIntentKind.PAYMENT]; aquí no se añade keyword nueva, se enseña
 * el infinitivo «adelantar» en el piso [PAYMENT_FLOOR], exactamente en
 * lockstep DOS puntos (lección c.616/c.652) igual que «recargar»
 * (c.1198 del hermano): (1) [PAYMENT_VERBS]; (2) plantilla [extractTitle]
 * rama PAYMENT conserva el verbo (doctrina c.653, grafía preservada).
 */
class ContextIntentEngineAdelantarMensualidadTest {

    private fun analyze(
        text: String,
        now: Long = 1_700_000_000_000L,
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    @Test
    fun `adelantar la mensualidad captura PAYMENT`() {
        val intent = analyze("adelantar la mensualidad")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
        assertEquals(0.45f, intent.confidence, 1e-6f)
        assertEquals("Adelantar la mensualidad", intent.title)
    }

    @Test
    fun `adelantar la mensualidad con temporal marca dueAt`() {
        val intent = analyze("adelantar la mensualidad el lunes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
        assertEquals(0.45f, intent.confidence, 1e-6f)
        assertEquals("Adelantar la mensualidad", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `envolvente recuerdamas casos hermanos TASK idéntico`() {
        // Medido c.1201: la envolvente «recuérdame X» resuelve TASK con
        // los tres verbos PAYMENT (pagar / recargar / adelantar) de forma
        // byte-idéntica. Pin de consistencia, no de captura PAYMENT.
        val intent = analyze("recuérdame adelantar la mensualidad")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Adelantar la mensualidad", intent.title)
    }

    @Test
    fun `vecino pagar la mensualidad sigue PAYMENT byte identico`() {
        val intent = analyze("pagar la mensualidad")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
        assertEquals(0.45f, intent.confidence, 1e-6f)
        assertEquals("Pagar la mensualidad", intent.title)
    }

    @Test
    fun `negacion protectora no adelantar sigue NULL`() {
        assertNull(analyze("no adelantar la mensualidad"))
    }

    @Test
    fun `subjuntivo quizá adelante sigue NULL`() {
        assertNull(analyze("quizá adelante la mensualidad"))
    }
}
