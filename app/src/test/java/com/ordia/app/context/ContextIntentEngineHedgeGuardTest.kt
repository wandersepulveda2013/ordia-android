package com.ordia.app.context

import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Guard anti-overreach para duda/condicional (c.649).
 *
 * Los pisos [ContextIntentEngine.hasStrong*Imperative] (c.626/c.630/c.638/c.639/
 * c.643/c.647) elevan la confianza al mínimo para imperativos inequívocos, y los
 * bonos específicos (CALL, APPOINTMENT...) la suben por encima del umbral. Pero
 * NINGUNO distingue un compromiso firme de una mera especulación: "quizá ir al
 * gimnasio" activaba el piso EXERCISE (EXERCISE 0.59), "tal vez cita con el
 * dentista" el bono APPOINTMENT (0.69), "a lo mejor llamar a mamá" el bono CALL
 * (0.57)... y se persistían como tareas reales aunque el usuario expresamente NO
 * se comprometió. Eso es overreach (P1: dato falso/ruido en la bandeja), análogo
 * al de la negación (c.648) pero de clase distinta: la negación capta lo
 * OPUESTO, la duda capta lo NO-COMPROMETIDO.
 *
 * c.649 añade [ContextIntentEngine.hasHedgeMarker], que aplica una penalización
 * POST-pisos (los pisos usan maxOf, así que una penalización pre-piso la
 * sobreescribirían) a la confianza final cuando aparecen marcadores de duda
 * léxica ("quizá"/"quizás", "a lo mejor", "tal vez", "capaz", "puede que",
 * "a ver si"). A diferencia de la negación (que descarta el kind), la duda se
 * penaliza (no bloquea) porque no niega la intención, la debilita. No se
 * incluyen "debería"/"pensaba" (reconocen necesidad/intención, no pura duda) ni
 * casos con score base ya bajo (caen solos bajo el umbral).
 */
class ContextIntentEngineHedgeGuardTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(
                ContextCaptureSource.NOTIFICATION,
                raw,
                1723939200000L
            )
        )

    // --- Marcadores de duda con imperativos de piso: deben ser NULL (overreach) ---

    @Test
    fun hedgeExerciseGimnasioIsNotCaptured() {
        val intent = analyze("quizá ir al gimnasio")
        assertNull("'quizá ir al gimnasio' es una duda, no un compromiso", intent)
    }

    @Test
    fun hedgeExerciseCorrerIsNotCaptured() {
        val intent = analyze("quizá correr 5k")
        assertNull("'quizá correr 5k' es una duda, no un compromiso", intent)
    }

    @Test
    fun hedgeErrandBancoIsNotCaptured() {
        val intent = analyze("a lo mejor ir al banco")
        assertNull("'a lo mejor ir al banco' es una duda, no un compromiso", intent)
    }

    @Test
    fun hedgeErrandRecogerIsNotCaptured() {
        val intent = analyze("quizá recoger el paquete")
        assertNull("'quizá recoger el paquete' es una duda, no un compromiso", intent)
    }

    @Test
    fun hedgeStudyEstudiarIsNotCaptured() {
        val intent = analyze("quizá estudiar para el examen")
        assertNull("'quizá estudiar para el examen' es una duda, no un compromiso", intent)
    }

    @Test
    fun hedgeStudyRepasarIsNotCaptured() {
        val intent = analyze("quizá repasar la lección")
        assertNull("'quizá repasar la lección' es una duda, no un compromiso", intent)
    }

    @Test
    fun hedgeHouseholdLimpiarIsNotCaptured() {
        val intent = analyze("a lo mejor limpiar la cocina")
        assertNull("'a lo mejor limpiar la cocina' es una duda, no un compromiso", intent)
    }

    @Test
    fun hedgeMeetingReunionIsNotCaptured() {
        val intent = analyze("tal vez reunión con el equipo")
        assertNull("'tal vez reunión con el equipo' es una duda, no un compromiso", intent)
    }

    // --- Marcadores de duda con bonos fuertes (CALL, APPOINTMENT, TASK): NULL ---

    @Test
    fun hedgeCallLlamarIsNotCaptured() {
        val intent = analyze("quizá llamar a mamá")
        assertNull("'quizá llamar a mamá' es una duda, no un compromiso", intent)
    }

    @Test
    fun hedgeCallLlamarAloMejorIsNotCaptured() {
        val intent = analyze("a lo mejor llamar a maría")
        assertNull("'a lo mejor llamar a maría' es una duda, no un compromiso", intent)
    }

    @Test
    fun hedgeAppointmentCitaIsNotCaptured() {
        val intent = analyze("tal vez cita con el dentista")
        assertNull("'tal vez cita con el dentista' es una duda, no un compromiso", intent)
    }

    @Test
    fun hedgeTaskHayQueIsNotCaptured() {
        val intent = analyze("hay que comprar pan quizá")
        assertNull("'hay que comprar pan quizá' es una duda, no un compromiso", intent)
    }

    // --- Variantes ortográficas y condicionales ---

    @Test
    fun hedgeQuizasConSIsNotCaptured() {
        val intent = analyze("quizás compre pan")
        assertNull("'quizás compre pan' es una duda, no un compromiso", intent)
    }

    @Test
    fun hedgeTalVezIsNotCaptured() {
        val intent = analyze("tal vez comprar pan")
        assertNull("'tal vez comprar pan' es una duda, no un compromiso", intent)
    }

    @Test
    fun hedgeCapazIsNotCaptured() {
        val intent = analyze("capaz comprar pan")
        assertNull("'capaz comprar pan' es una duda, no un compromiso", intent)
    }

    @Test
    fun hedgePuedeQueIsNotCaptured() {
        val intent = analyze("puede que compre pan")
        assertNull("'puede que compre pan' es una duda, no un compromiso", intent)
    }

    @Test
    fun hedgeAverSiIsNotCaptured() {
        val intent = analyze("a ver si compro pan")
        assertNull("'a ver si compro pan' es una duda, no un compromiso", intent)
    }

    // --- Regresión: casos afirmativos SIN marcador de duda siguen capturándose ---

    @Test
    fun affirmativeGimnasioIsCaptured() {
        val intent = analyze("ir al gimnasio")
        assertNotNull("'ir al gimnasio' (sin duda) debe capturarse", intent)
    }

    @Test
    fun affirmativeCitaIsCaptured() {
        val intent = analyze("cita con el dentista")
        assertNotNull("'cita con el dentista' (sin duda) debe capturarse", intent)
    }

    @Test
    fun affirmativeLlamarIsCaptured() {
        val intent = analyze("llamar a maría")
        assertNotNull("'llamar a maría' (sin duda) debe capturarse", intent)
    }

    @Test
    fun affirmativeReunionIsCaptured() {
        val intent = analyze("reunión con el equipo")
        assertNotNull("'reunión con el equipo' (sin duda) debe capturarse", intent)
    }

    // --- No debe capturar falsos: substring que no es marcador de duda ---

    @Test
    fun nonHedgeSubstringDoesNotBlock() {
        // "capacidad" contiene "capaz" como substring pero los lookarounds
        // Unicode (\p{L}) impiden casarlo (exigen frontera de palabra): una tarea
        // legítima que mencione "capacidad" no se bloquea por el hedge guard.
        val intent = analyze("estudiar para el examen de capacidad")
        assertNotNull(
            "'estudiar para el examen de capacidad' (substring 'capaz' en 'capacidad') " +
                "no debe bloquearse por el hedge guard",
            intent
        )
    }
}
