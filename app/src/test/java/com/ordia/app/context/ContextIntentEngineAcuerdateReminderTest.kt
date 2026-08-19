package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * c.689: envolvente imperativa reflexiva de recordatorio "acuérdate de
 * <infinitivo>" (2ª persona, enclítico -te) → REMINDER. Hermano cercano del
 * piso c.619 ("acordarme", 1ª persona) y de la forma c.687 ("te acuerdas de",
 * interrogativa). Es la forma más cotidiana de auto-recordatorio en voz alta.
 * Anti-overreach: acepta sólo infinitivo (la evocación del pasado y los
 * sustantivos no capturan); la negación bloqueada por `(?<!no )`.
 */
class ContextIntentEngineAcuerdateReminderTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1723939200000L)
    )

    @Test
    fun acuerdateDeSacarAlPerroIsCapturedAsReminder() {
        val intent = analyze("acuérdate de sacar al perro")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.REMINDER, intent!!.kind)
        assertEquals("Sacar al perro", intent.title)
    }

    @Test
    fun acuerdateSinTildeIsCapturedAsReminder() {
        val intent = analyze("acuerdate de comprar leche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.REMINDER, intent!!.kind)
        assertEquals("Comprar leche", intent.title)
    }

    @Test
    fun acuerdateConFechaResuelveDueAt() {
        val intent = analyze("acuérdate de llamar al banco mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.REMINDER, intent!!.kind)
        assertTrue(intent.dueAt != null)
        assertEquals("Llamar al banco", intent.title)
    }

    @Test
    fun acuerdateConFranjaLimpiaTitulo() {
        val intent = analyze("acuérdate de hacer ejercicio por la mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.REMINDER, intent!!.kind)
        assertEquals("Hacer ejercicio", intent.title)
    }

    @Test
    fun evocacionDelPasadoNoCaptura() {
        assertNull(analyze("acuérdate de cuando íbamos al parque"))
    }

    @Test
    fun sustantivoTrasDeNoCaptura() {
        assertNull(analyze("acuérdate de las llaves"))
    }

    @Test
    fun negacionConservadoraNoCaptura() {
        assertNull(analyze("no acuérdate de pagar"))
    }

    @Test
    fun regressionRecuerdameStillCaptured() {
        // c.613: "recuérdame" es piso de TASK, no de REMINDER.
        val intent = analyze("recuérdame llamar")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun regressionTeAcuerdasStillReminder() {
        val intent = analyze("te acuerdas de pagar la renta?")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.REMINDER, intent!!.kind)
    }
}
