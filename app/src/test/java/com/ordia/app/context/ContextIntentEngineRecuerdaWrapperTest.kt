package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Wrapper "recuerda X" (2ª persona, imperativo hacia el asistente, sin "me")
 * de la captura contextual (c.682).
 *
 * La sonda de descubrimiento de c.681 constató olvido silencioso P1 en
 * [ContextIntentEngine.analyze]: "recuerda comprar leche" (y otras 6 formas
 * cotidianas) se descartaban (NULL) porque el piso de TASK (c.613) sólo
 * reconocía "recuérdame"/"no olvides"/"tengo que"/"hay que". "recuerda X" es
 * la forma imperativa directa equivalente a "recuérdame X" y debe capturarse
 * como TASK (una forma por ciclo, doctrina anti-overreach de c.681).
 *
 * Anti-overreach: el verbo que sigue a "recuerda" debe ser INFINITIVO
 * ("recuerda comprar…", "recuerda pagar…", "recuerda ir…"). Así la nostalgia
 * conversacional ("recuerda cuando íbamos al parque", "recuerda que mañana es
 * el cumple de ana") NO se captura, y "recuerdas" (indicativo interrogativo)
 * tampoco. Las formas con sustantivo ("recuerda la cita") no necesitan el
 * wrapper: ya las captura el patrón propio de su kind (APPOINTMENT, c.682
 * RED lo fijó como regresión).
 */
class ContextIntentEngineRecuerdaWrapperTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(
                ContextCaptureSource.NOTIFICATION,
                raw,
                1723939200000L
            )
        )

    // --- Captura: "recuerda <infinitivo>" debe ser TASK ---

    @Test
    fun recuerdaComprarLecheIsCapturedAsTask() {
        val intent = analyze("recuerda comprar leche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Comprar leche", intent.title)
    }

    @Test
    fun recuerdaPagarLaRentaMananaIsCapturedWithDueAt() {
        val intent = analyze("recuerda pagar la renta mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Pagar la renta", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun recuerdaLlamarAMamaIsCaptured() {
        val intent = analyze("recuerda llamar a mamá")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Llamar a mamá", intent.title)
    }

    @Test
    fun recuerdaIrAlGimnasioIsTaskNotExercise() {
        val intent = analyze("recuerda ir al gimnasio")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun recuerdaTenerLaReunionIsTaskNotMeeting() {
        val intent = analyze("recuerda tener la reunión con el equipo")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Tener la reunión con el equipo", intent.title)
    }

    @Test
    fun recuerdaTomarLaMedicacionIsCaptured() {
        val intent = analyze("recuerda tomar la medicación")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Tomar la medicación", intent.title)
    }

    // --- Anti-overreach: nostalgia/indicativo NO se capturan ---

    @Test
    fun recuerdaCuandoIbaAlParqueIsNotCaptured() {
        assertNull(analyze("recuerda cuando íbamos al parque"))
    }

    @Test
    fun recuerdaQueMananaEsElCumpleIsNotCaptured() {
        assertNull(analyze("recuerda que mañana es el cumple de ana"))
    }

    @Test
    fun recuerdasComprarLecheIsNotCaptured() {
        assertNull(analyze("¿recuerdas comprar leche?"))
    }

    @Test
    fun recuerdaLaCitaKeepsAppointmentCapture() {
        // La forma con sustantivo ya la cubre el patrón de APPOINTMENT ("Cita del
        // dentista", 0.57): el wrapper con lookahead de infinitivo NO debe
        // desactivarla. Regresión fijada en RED: era APPOINTMENT antes de c.682.
        val intent = analyze("recuerda la cita del dentista")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
        assertEquals("Cita del dentista", intent.title)
    }

    // --- Regresión: los wrappers existentes no cambian ---

    @Test
    fun recuerdameComprarPanStillCapturedAsTask() {
        val intent = analyze("recuérdame comprar pan")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Comprar pan", intent.title)
    }
}
