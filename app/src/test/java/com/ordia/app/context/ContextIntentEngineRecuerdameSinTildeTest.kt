package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1065 (P1 olvido silencioso en captura pasiva — envolvente «recuerdame»
 * SIN TILDE, escritura real en notificaciones de chat donde el teclado omite
 * la tilde). Lateral medida NULL en la sonda EnvelopeSinTildeProbe (c.1055) y
 * re-medida con `tools/probe/RecuerdameSinTildeProbe.kt`: la envolvente sin
 * tilde era INVISIBLE en todos los puntos — piso c.613
 * ([hasStrongTaskImperative]), guard de envolvente c.652 ([WRAPPER_PATTERN]),
 * bono «recuérdame \w+» y plantillas de título de [extractTitle] — así el
 * verbo subordinado enrutaba como acción autónoma («recuerdame llamar a
 * mamá» → CALL 0.57, «recuerdame ir al médico» → APPOINTMENT 0.67 con título
 * corrupto «Recuerdame ir al médico») o caía a NULL («recuerdame comprar
 * pan», «recuerdame revisar el correo», «recuerdame pagar el arriendo
 * mañana»): el recordatorio explícito por antonomasia se olvidaba.
 * Fix: alternancia de tilde `recu[ée]rdame` en lockstep en los 5 puntos
 * funcionales (piso + guard + bono + 2 plantillas de título), mismo patrón
 * que «acu[ée]rdate» (c.689) — más un 6º punto descubierto en la integración
 * de la UNIÓN: [WRAPPER_NEGATION_SPAN] (guard wrapper+no de SU c.1064, sin
 * él «recuerdame no se que» capturaba como basura TASK). CERO keywords
 * nuevas: el piso basta (paridad de confianza exacta: con tilde 0.12
 * keyword + 0.2 bono → piso 0.45; sin tilde 0.2 bono → piso 0.45). Las
 * hermanas «avisame»/«notificame» sin tilde quedan registradas como
 * laterales (medidas en la sonda, UNA por ciclo, doctrina anti-overreach).
 * Determinista (regex), sin random, sin IA fingida.
 */
class ContextIntentEngineRecuerdameSinTildeTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: «recuerdame X» sin tilde gobierna TASK (paridad c.613) ---

    @Test
    fun recuerdameLlamarAMama_wrapperWinsTask() {
        val intent = analyze("recuerdame llamar a mamá")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Llamar a mamá", intent.title)
    }

    @Test
    fun recuerdameComprarPan_capturesTask() {
        val intent = analyze("recuerdame comprar pan")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Comprar pan", intent.title)
    }

    @Test
    fun recuerdameRevisarElCorreo_capturesTask() {
        val intent = analyze("recuerdame revisar el correo")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Revisar el correo", intent.title)
    }

    @Test
    fun recuerdameCortarmeLosPelosManana_wrapperGovernsFloor() {
        val intent = analyze("recuerdame cortarme los pelos mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cortarme los pelos", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun recuerdamePagarElArriendoManana_capturesTaskWithDueAt() {
        val intent = analyze("recuerdame pagar el arriendo mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Pagar el arriendo", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun recuerdameIrAlMedico_wrapperGuardWinsTask() {
        val intent = analyze("recuerdame ir al médico")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Ir al médico", intent.title)
    }

    @Test
    fun recuerdameReunionConElEquipo_wrapperGuardWinsTask() {
        val intent = analyze("recuerdame reunión con el equipo")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun recuerdameMayusculaInicial_wrapperWinsTask() {
        val intent = analyze("Recuerdame llamar a mamá")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Llamar a mamá", intent.title)
    }

    // --- Paridad de confianza: sin tilde == con tilde (piso c.613) ---

    @Test
    fun recuerdameSinTilde_mismaConfianzaQueConTilde() {
        val sinTilde = analyze("recuerdame llamar a mamá")
        val conTilde = analyze("recuérdame llamar a mamá")
        assertNotNull(sinTilde)
        assertNotNull(conTilde)
        assertEquals(conTilde!!.confidence, sinTilde!!.confidence, 0.0001f)
    }

    // --- Regresión: la forma con tilde sigue pinada (lockstep) ---

    @Test
    fun recuerdameConTilde_sigueGobernandoTask() {
        val intent = analyze("recuérdame llamar a mamá")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Llamar a mamá", intent.title)
    }

    // --- Anti-overreach: muletilla aislada sin verbo no activa el piso ---

    @Test
    fun recuerdameAislado_sinVerboNoCaptura() {
        assertNull(analyze("recuerdame"))
    }

    // --- Coherencia con la guard wrapper+no de la UNIÓN (SU c.1064): la
    // alternancia de tilde se extendió también a WRAPPER_NEGATION_SPAN (6º
    // punto lockstep descubierto en la integración) ---

    @Test
    fun recuerdameSinTilde_noSeQue_quedaNullRuido() {
        assertNull(analyze("recuerdame no se que"))
        assertNull(analyze("recuerdame no sé qué"))
    }

    @Test
    fun recuerdameSinTilde_noInfinitivo_capturaFielProhibicion() {
        val intent = analyze("recuerdame no llamar a mamá")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("No llamar a mamá", intent.title)
    }
}
