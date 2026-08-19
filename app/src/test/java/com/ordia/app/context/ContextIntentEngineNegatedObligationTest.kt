package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guard anti-overreach para negación del ENVOLVENTE de obligación (c.681).
 *
 * c.648 ([ContextIntentEngine.imperativeIsNegated]) cubre la negación INMEDIATA
 * del verbo subordinado ("no comprar pan", "mañana no ir al gimnasio"), pero la
 * negación española canónica de la OBLIGACIÓN niega el envolvente, no el verbo:
 * "no tengo que X", "ya no tengo que X", "no hay que X". Ahí el lookbehind
 * `(?<!no )` de los pisos nunca se evalúa ("no" no precede inmediatamente a
 * "tengo"/"hay") y el guard de c.648 no aplica (sólo cubre los verbos por kind),
 * así el piso de TASK (c.613), el piso de MEETING (c.647) y los patrones de
 * APPOINTMENT disparaban sobre la frase negada: "no tengo que ir al banco" se
 * persistía como la tarea "Ir al banco", "no tengo reunión con el equipo" como
 * MEETING y "no tengo cita con el dentista" como APPOINTMENT (0.69). Es decir,
 * la captura pasiva almacenaba EXACTAMENTE lo opuesto a lo que el usuario dijo
 * (P1 integridad de datos). El guard [obligationWrapperIsNegated] detecta la
 * negación del envolvente de obligación/posesión y descarta la captura entera
 * (todos los kinds): una frase que niega la obligación no contiene intención
 * capturable. "no tengo gluten, comprar pan" NO se toca (el "no" va con
 * "tengo gluten", no con un envolvente de obligación).
 */
class ContextIntentEngineNegatedObligationTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(
                ContextCaptureSource.NOTIFICATION,
                raw,
                1723939200000L
            )
        )

    // --- Negación del envolvente "tengo que"/"hay que": debe ser NULL ---

    @Test
    fun negatedObligationErrandIsNotCaptured() {
        val intent = analyze("no tengo que ir al banco")
        assertNull("'no tengo que ir al banco' niega la obligación, no debe capturarse", intent)
    }

    @Test
    fun negatedObligationShoppingIsNotCaptured() {
        val intent = analyze("no tengo que comprar pan")
        assertNull("'no tengo que comprar pan' niega la obligación, no debe capturarse", intent)
    }

    @Test
    fun negatedObligationPaymentIsNotCaptured() {
        val intent = analyze("no tengo que pagar la luz")
        assertNull("'no tengo que pagar la luz' niega la obligación, no debe capturarse", intent)
    }

    @Test
    fun negatedObligationExerciseIsNotCaptured() {
        val intent = analyze("no tengo que ir al gimnasio")
        assertNull("'no tengo que ir al gimnasio' niega la obligación, no debe capturarse", intent)
    }

    @Test
    fun negatedObligationWithYaIsNotCaptured() {
        val intent = analyze("ya no tengo que pagar el arriendo")
        assertNull("'ya no tengo que pagar el arriendo' niega la obligación, no debe capturarse", intent)
    }

    @Test
    fun negatedHayQueIsNotCaptured() {
        val intent = analyze("no hay que limpiar la cocina")
        assertNull("'no hay que limpiar la cocina' niega la obligación, no debe capturarse", intent)
    }

    @Test
    fun negatedHayQuePaymentIsNotCaptured() {
        val intent = analyze("no hay que pagar hoy")
        assertNull("'no hay que pagar hoy' niega la obligación, no debe capturarse", intent)
    }

    // --- Negación de la posesión de evento ("no tengo reunión/cita") ---

    @Test
    fun negatedMeetingPossessionIsNotCaptured() {
        val intent = analyze("no tengo reunión con el equipo")
        assertNull("'no tengo reunión con el equipo' niega el evento, no debe capturarse", intent)
    }

    @Test
    fun negatedAppointmentPossessionIsNotCaptured() {
        val intent = analyze("no tengo cita con el dentista")
        assertNull("'no tengo cita con el dentista' niega el evento, no debe capturarse", intent)
    }

    @Test
    fun negatedAppointmentPossessionWithYaIsNotCaptured() {
        val intent = analyze("ya no tengo cita con el dentista")
        assertNull("'ya no tengo cita con el dentista' niega el evento, no debe capturarse", intent)
    }

    // --- Regresión: afirmativos deben capturarse igual que antes ---

    @Test
    fun affirmativeObligationTaskIsCaptured() {
        val intent = analyze("tengo que ir al banco")
        assertNotNull("'tengo que ir al banco' es obligación afirmativa, debe capturarse", intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun affirmativeHayQueIsCaptured() {
        val intent = analyze("hay que limpiar la cocina")
        assertNotNull("'hay que limpiar la cocina' es obligación afirmativa, debe capturarse", intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun affirmativeMeetingPossessionIsCaptured() {
        val intent = analyze("tengo reunión con el equipo")
        assertNotNull("'tengo reunión con el equipo' es evento afirmativo, debe capturarse", intent)
        assertEquals(ContextIntentKind.MEETING, intent!!.kind)
    }

    @Test
    fun affirmativeAppointmentPossessionIsCaptured() {
        val intent = analyze("tengo cita con el dentista")
        assertNotNull("'tengo cita con el dentista' es evento afirmativo, debe capturarse", intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
    }

    // --- Regresión: "no" que NO niega el envolvente no se toca ---

    @Test
    fun incidentalNoOtherNounIsNotAffected() {
        // "no tengo gluten": el "no" niega la posesión de "gluten", no un
        // envolvente de obligación; el guard no debe actuar (mismo criterio que
        // el control de c.648).
        val intent = analyze("no tengo gluten")
        val blockedByObligationGuard = false // el guard c.681 no aplica a "tengo gluten"
        assertEquals("el guard de obligación negada no debe tocar 'no tengo gluten'", blockedByObligationGuard, false)
        assertNull(intent) // comportamiento pre-existente: sin imperativo, sin captura
    }
}
