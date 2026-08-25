package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1067 (P1 olvido silencioso en captura pasiva — envolventes hermanas
 * «avisame»/«notificame» SIN TILDE, escritura real en notificaciones de chat
 * donde el teclado omite la tilde). Lateral registrada en c.1065 (medida
 * CALL 0.57 en la sonda EnvelopeSinTildeProbe c.1055) y re-medida con sonda
 * efímera PRE: la envolvente sin tilde era INVISIBLE en todos los puntos —
 * piso c.619 ([hasStrongReminderImperative]), guard de envolvente c.652
 * ([WRAPPER_PATTERN]), bono REMINDER 0.25 y plantilla de título de
 * [extractTitle] — así el verbo subordinado enrutaba como acción autónoma
 * («avisame llamar a mamá» → CALL 0.57, «avisame ir al médico» →
 * APPOINTMENT 0.67 con título corrupto «Avisame ir al médico») o caía a
 * NULL («avisame comprar pan», «avisame revisar el correo», «avisame
 * mañana de la reunión»): el recordatorio explícito se olvidaba. Misma
 * clase de defecto que «recuerdame» (c.1065). Fix: alternancia de tilde
 * `av[ií]same|notif[ií]came` en lockstep en los 5 puntos funcionales (piso
 * + guard + bono + plantilla de título + [WRAPPER_NEGATION_SPAN] de la
 * UNIÓN SU c.1064 — sin él «avisame no se que» capturaría como basura
 * REMINDER, misma lección del 6º punto de c.1065). CERO keywords nuevas:
 * el piso basta (paridad de confianza exacta: con tilde 0.12 keyword +
 * 0.25 bono → piso 0.45; sin tilde 0.25 bono → piso 0.45). Determinista
 * (regex), sin random, sin IA fingida.
 */
class ContextIntentEngineAvisameSinTildeTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: «avisame X» sin tilde gobierna REMINDER (paridad c.619) ---

    @Test
    fun avisameLlamarAMama_wrapperWinsReminder() {
        val intent = analyze("avisame llamar a mamá")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.REMINDER, intent!!.kind)
        assertEquals("Llamar a mamá", intent.title)
    }

    @Test
    fun avisameComprarPan_capturesReminder() {
        val intent = analyze("avisame comprar pan")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.REMINDER, intent!!.kind)
        assertEquals("Comprar pan", intent.title)
    }

    @Test
    fun avisameRevisarElCorreo_capturesReminder() {
        val intent = analyze("avisame revisar el correo")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.REMINDER, intent!!.kind)
        assertEquals("Revisar el correo", intent.title)
    }

    @Test
    fun avisameIrAlMedico_wrapperGuardWinsReminder() {
        val intent = analyze("avisame ir al médico")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.REMINDER, intent!!.kind)
        assertEquals("Ir al médico", intent.title)
    }

    @Test
    fun avisameMananaDeLaReunion_capturesReminderWithDueAt() {
        val intent = analyze("avisame mañana de la reunión")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.REMINDER, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    // --- Hermana «notificame» sin tilde (misma clase, misma alternancia) ---

    @Test
    fun notificameLlamarAMama_wrapperWinsReminder() {
        val intent = analyze("notificame llamar a mamá")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.REMINDER, intent!!.kind)
        assertEquals("Llamar a mamá", intent.title)
    }

    @Test
    fun notificameComprarPan_capturesReminder() {
        val intent = analyze("notificame comprar pan")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.REMINDER, intent!!.kind)
        assertEquals("Comprar pan", intent.title)
    }

    @Test
    fun avisameMayusculaInicial_wrapperWinsReminder() {
        val intent = analyze("Avisame llamar a mamá")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.REMINDER, intent!!.kind)
        assertEquals("Llamar a mamá", intent.title)
    }

    // --- Paridad de confianza: sin tilde == con tilde (piso c.619) ---

    @Test
    fun avisameSinTilde_mismaConfianzaQueConTilde() {
        val sinTilde = analyze("avisame llamar a mamá")
        val conTilde = analyze("avísame llamar a mamá")
        assertNotNull(sinTilde)
        assertNotNull(conTilde)
        assertEquals(conTilde!!.confidence, sinTilde!!.confidence, 0.0001f)
    }

    // --- Regresión: la forma con tilde sigue pinada (lockstep) ---

    @Test
    fun avisameConTilde_sigueGobernandoReminder() {
        val intent = analyze("avísame llamar a mamá")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.REMINDER, intent!!.kind)
        assertEquals("Llamar a mamá", intent.title)
    }

    // --- Anti-overreach: muletilla aislada sin verbo no activa el piso ---

    @Test
    fun avisameAislado_notificameAislado_sinVerboNoCaptura() {
        assertNull(analyze("avisame"))
        assertNull(analyze("notificame"))
    }

    // --- Regresión: pasado no captura («me avisaste ayer») ---

    @Test
    fun meAvisasteAyer_pasadoNoCaptura() {
        assertNull(analyze("me avisaste ayer"))
    }

    // --- Pin byte-idéntico: «avisame pagar la luz mañana» ya enrutaba
    // PAYMENT igual que con tilde (el piso de pago gobierna en ambas) ---

    @Test
    fun avisamePagarLaLuzManana_paymentPinByteIdentical() {
        val sinTilde = analyze("avisame pagar la luz mañana")
        val conTilde = analyze("avísame pagar la luz mañana")
        assertNotNull(sinTilde)
        assertNotNull(conTilde)
        assertEquals(ContextIntentKind.PAYMENT, sinTilde!!.kind)
        assertEquals(conTilde!!.kind, sinTilde.kind)
        assertEquals(conTilde.title, sinTilde.title)
    }

    // --- Coherencia con la guard wrapper+no de la UNIÓN (SU c.1064): la
    // alternancia de tilde se extendió también a WRAPPER_NEGATION_SPAN
    // (5º punto lockstep, misma lección del 6º punto de c.1065) ---

    @Test
    fun avisameSinTilde_noSeQue_quedaNullRuido() {
        assertNull(analyze("avisame no se que"))
        assertNull(analyze("avisame no sé qué"))
        assertNull(analyze("notificame no se que"))
    }

    @Test
    fun avisameSinTilde_noInfinitivo_capturaFielProhibicion() {
        val intent = analyze("avisame no llamar a mamá")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.REMINDER, intent!!.kind)
        assertEquals("No llamar a mamá", intent.title)
    }
}
