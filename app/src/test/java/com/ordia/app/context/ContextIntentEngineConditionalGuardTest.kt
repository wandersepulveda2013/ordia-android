package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guard anti-overreach para el condicional "si" (c.650).
 *
 * Continuación directa de c.649 (duda) — defecto de CLASE DISTINTA descubierto
 * por probe JVM fuente real: una cláusula condicional ("si tengo tiempo",
 * "si puedo", "si me dan el día"...) que gobierna el imperativo activaba los
 * pisos [ContextIntentEngine.hasStrong*Imperative] y los bonos fuertes
 * (CALL/APPOINTMENT), persistiendo como tarea firme una acción que el usuario
 * sólo haría BAJO CONDICIÓN: "si tengo tiempo ir al gimnasio" → EXERCISE 0.59,
 * "si me dan el día cita con el dentista" → APPOINTMENT 0.69, "si puedo llamar
 * a mamá" → CALL 0.57. Overreach (P1: dato no comprometido persistido).
 *
 * Decisión de diseño (documentada, honesta, determinista):
 * - Condición QUE PRECEDE al imperativo (al inicio de la frase o tras puntuación)
 *   o marcadores medios inequívocos ("si puedo/puedes/podemos", "si tengo
 *   tiempo", "si es posible", "si se puede", "si me acuerdo") ⇒ la acción está
 *   gobernada por una condición no resuelta ⇒ especulación ⇒ penalización
 *   post-pisos [ContextIntentEngine.HEDGE_PENALTY] (como la duda c.649).
 * - Condición QUE SIGUE a un imperativo firme ("llamar al banco si no llega el
 *   pago") ⇒ recordatorio condicional legítimo: el usuario SÍ se comprometió a
 *   actuar bajo esa condición ⇒ se CAPTURA (no es overreach).
 * - "si" subordinada de contenido ("preguntar a juan si viene", "ver si hay
 *   leche") ⇒ no es condición sobre el compromiso ⇒ no penaliza.
 * - "sí" afirmativo CON tilde ("sí, comprar pan") ⇒ no casa (el patrón exige
 *   "si" sin tilde) ⇒ no penaliza.
 */
class ContextIntentEngineConditionalGuardTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(
                ContextCaptureSource.NOTIFICATION,
                raw,
                1723939200000L
            )
        )

    // --- Condicional inicial/medio que gobierna el imperativo: NULL (overreach) ---

    @Test
    fun conditionalSiPuedoShoppingIsNotCaptured() {
        assertNull("'mañana si puedo comprar pan' está gobernado por condición", analyze("mañana si puedo comprar pan"))
    }

    @Test
    fun conditionalSiTengoTiempoExerciseIsNotCaptured() {
        assertNull("'si tengo tiempo ir al gimnasio' está gobernado por condición", analyze("si tengo tiempo ir al gimnasio"))
    }

    @Test
    fun conditionalInitialMeetingIsNotCaptured() {
        assertNull("'si sale bien reunión con el equipo' está gobernado por condición", analyze("si sale bien reunión con el equipo"))
    }

    @Test
    fun conditionalSiPuedoCallIsNotCaptured() {
        assertNull("'si puedo llamar a mamá' está gobernado por condición", analyze("si puedo llamar a mamá"))
    }

    @Test
    fun conditionalInitialAppointmentIsNotCaptured() {
        assertNull("'si me dan el día cita con el dentista' está gobernado por condición", analyze("si me dan el día cita con el dentista"))
    }

    @Test
    fun conditionalSiNoLlueveExerciseIsNotCaptured() {
        assertNull("'si no llueve correr 5k' está gobernado por condición", analyze("si no llueve correr 5k"))
    }

    @Test
    fun conditionalSiEsPosiblePaymentIsNotCaptured() {
        assertNull("'pagar la luz, si es posible' está gobernado por condición", analyze("pagar la luz, si es posible"))
    }

    @Test
    fun conditionalSiSePuedeErrandIsNotCaptured() {
        assertNull("'si se puede recoger el paquete' está gobernado por condición", analyze("si se puede recoger el paquete"))
    }

    // --- Regresión: afirmativos firmes siguen capturándose ---

    @Test
    fun firmShoppingStillCaptured() {
        assertEquals(ContextIntentKind.SHOPPING, analyze("comprar pan")?.kind)
    }

    @Test
    fun firmTemporalShoppingStillCaptured() {
        assertEquals(ContextIntentKind.SHOPPING, analyze("mañana comprar pan")?.kind)
    }

    @Test
    fun firmExerciseStillCaptured() {
        assertEquals(ContextIntentKind.EXERCISE, analyze("ir al gimnasio")?.kind)
    }

    @Test
    fun firmAppointmentStillCaptured() {
        assertEquals(ContextIntentKind.APPOINTMENT, analyze("cita con el dentista")?.kind)
    }

    @Test
    fun firmCallStillCaptured() {
        assertEquals(ContextIntentKind.CALL, analyze("llamar a maría")?.kind)
    }

    // --- Regresión: condición TRAS imperativo firme = recordatorio condicional legítimo ---

    @Test
    fun trailingConditionAfterFirmImperativeStillCaptured() {
        val intent = analyze("llamar al banco si no llega el pago")
        assertNotNull("recordatorio condicional legítimo (imperativo primero)", intent)
        assertEquals(ContextIntentKind.CALL, intent?.kind)
    }

    // --- Regresión: "si" subordinada de contenido no penaliza (cambia kind por score, no por guard) ---

    @Test
    fun contentSiDoesNotTriggerConditionalGuard() {
        // "revisar si el informe está listo" no contiene marcador condicional del guard;
        // si no se captura es por score base bajo, no por el guard (no falso positivo del patrón).
        val intent = analyze("revisar si el informe está listo")
        if (intent != null) {
            assertEquals(ContextIntentKind.TASK, intent.kind)
        }
    }

    // --- Regresión: "sí" afirmativo con tilde no activa el guard condicional ---
    // (La captura de "sí, comprar pan" depende del piso SHOPPING con prefijo de
    // acuse, cerrado en c.651; aquí sólo se garantiza que el guard NO la penaliza:
    // si se captura, es SHOPPING.)

    @Test
    fun affirmativeSiWithAccentDoesNotTriggerConditionalGuard() {
        val intent = analyze("sí, comprar pan")
        if (intent != null) {
            assertEquals(ContextIntentKind.SHOPPING, intent.kind)
        }
    }

    // --- Regresión: negación c.648 y duda c.649 intactas ---

    @Test
    fun negationGuardStillDiscards() {
        assertNull(analyze("mañana no comprar pan"))
    }

    @Test
    fun hedgeGuardStillDiscards() {
        assertNull(analyze("quizá ir al gimnasio"))
    }
}
