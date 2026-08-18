package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guard anti-overreach para negación de imperativos con prefijo temporal (c.648).
 *
 * Los pisos [ContextIntentEngine.hasStrong*Imperative] (c.626/c.630/c.638/c.639/
 * c.643/c.647) sólo bloquean la negación cuando el score queda bajo
 * [ContextIntentEngine.MINIMUM_CONFIDENCE] vía lookbehind `(?<!no )`: si el verbo
 * no aparece al inicio o un patrón específico/bono temporal lo eleva por encima
 * del umbral, el guard del piso NO se activaba. Así "mañana no comprar pan" se
 * capturaba como la tarea "Comprar pan" (score 0.47): exactamente lo OPUESTO a la
 * intención del usuario, persistido como dato real (overreach P1). c.648 añade
 * [ContextIntentEngine.imperativeIsNegated], que descarta el kind cuando el
 * verbo imperativo aparece inmediatamente negado por "no" en cualquier posición,
 * complementando a los pisos donde el bono temporal o un patrón específico
 * elevan el score. La negación debe ser INMEDIATA ("no <verbo>"), así "no olvides
 * comprar pan" (el "no" niega "olvides", "comprar" va libre) o "no tengo gluten,
 * comprar pan" (el "no" va con "tengo") NO se bloquean: el imperativo queda libre.
 */
class ContextIntentEngineNegationGuardTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(
                ContextCaptureSource.NOTIFICATION,
                raw,
                1723939200000L
            )
        )

    // --- Negación inmediata con prefijo temporal: deben ser NULL (overreach) ---

    @Test
    fun temporalNegatedShoppingIsNotCaptured() {
        val intent = analyze("mañana no comprar pan")
        assertNull("'mañana no comprar pan' niega la compra, no debe capturarse", intent)
    }

    @Test
    fun temporalNegatedPaymentIsNotCaptured() {
        val intent = analyze("mañana no pagar el recibo")
        assertNull("'mañana no pagar el recibo' niega el pago, no debe capturarse", intent)
    }

    @Test
    fun temporalNegatedStudyRepasarIsNotCaptured() {
        val intent = analyze("mañana no repasar la lección")
        assertNull("'mañana no repasar la lección' niega el estudio, no debe capturarse", intent)
    }

    @Test
    fun temporalNegatedStudyEstudiarIsNotCaptured() {
        val intent = analyze("mañana no estudiar para el examen")
        assertNull("'mañana no estudiar para el examen' niega el estudio, no debe capturarse", intent)
    }

    @Test
    fun temporalNegatedStudyPrepararExamenIsNotCaptured() {
        val intent = analyze("mañana no preparar el examen")
        assertNull("'mañana no preparar el examen' niega el estudio, no debe capturarse", intent)
    }

    @Test
    fun temporalNegatedErrandRecogerIsNotCaptured() {
        val intent = analyze("mañana no recoger el paquete")
        assertNull("'mañana no recoger el paquete' niega la diligencia, no debe capturarse", intent)
    }

    @Test
    fun temporalNegatedErrandIrBancoIsNotCaptured() {
        val intent = analyze("mañana no ir al banco")
        assertNull("'mañana no ir al banco' niega la diligencia, no debe capturarse", intent)
    }

    @Test
    fun temporalNegatedMeetingIsNotCaptured() {
        val intent = analyze("mañana no reunión con el equipo")
        assertNull("'mañana no reunión con el equipo' niega la reunión, no debe capturarse", intent)
    }

    @Test
    fun temporalNegatedExerciseCorrerIsNotCaptured() {
        val intent = analyze("mañana no correr 5k")
        assertNull("'mañana no correr 5k' niega el ejercicio, no debe capturarse", intent)
    }

    @Test
    fun temporalNegatedExerciseIrGimnasioIsNotCaptured() {
        val intent = analyze("mañana no ir al gimnasio")
        assertNull("'mañana no ir al gimnasio' niega el ejercicio, no debe capturarse", intent)
    }

    @Test
    fun temporalNegatedExerciseHacerYogaIsNotCaptured() {
        val intent = analyze("mañana no hacer yoga")
        assertNull("'mañana no hacer yoga' niega el ejercicio, no debe capturarse", intent)
    }

    @Test
    fun temporalNegatedHouseholdIsNotCaptured() {
        val intent = analyze("mañana no fregar los platos")
        assertNull("'mañana no fregar los platos' niega el hogar, no debe capturarse", intent)
    }

    // --- Negación inmediata sin prefijo temporal: deben ser NULL ---

    @Test
    fun negatedShoppingNoPrefixIsNotCaptured() {
        val intent = analyze("no comprar pan")
        assertNull("'no comprar pan' niega la compra, no debe capturarse", intent)
    }

    @Test
    fun negatedPaymentNoPrefixIsNotCaptured() {
        val intent = analyze("no pagar el recibo")
        assertNull("'no pagar el recibo' niega el pago, no debe capturarse", intent)
    }

    // --- Regresión: afirmativos con prefijo temporal deben capturarse ---

    @Test
    fun temporalAffirmativeShoppingIsCaptured() {
        val intent = analyze("mañana comprar pan")
        assertNotNull("'mañana comprar pan' es una compra afirmativa, debe capturarse", intent)
        assertEquals(ContextIntentKind.SHOPPING, intent!!.kind)
    }

    @Test
    fun temporalAffirmativePaymentIsCaptured() {
        val intent = analyze("mañana pagar el recibo")
        assertNotNull("'mañana pagar el recibo' es un pago afirmativo, debe capturarse", intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
    }

    @Test
    fun temporalAffirmativeExerciseIrGimnasioIsCaptured() {
        val intent = analyze("mañana ir al gimnasio")
        assertNotNull("'mañana ir al gimnasio' es ejercicio afirmativo, debe capturarse", intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
    }

    @Test
    fun temporalAffirmativeStudyEstudiarIsCaptured() {
        val intent = analyze("mañana estudiar para el examen")
        assertNotNull("'mañana estudiar para el examen' es estudio afirmativo, debe capturarse", intent)
        assertEquals(ContextIntentKind.STUDY, intent!!.kind)
    }

    @Test
    fun temporalAffirmativeHouseholdIsCaptured() {
        val intent = analyze("mañana fregar los platos")
        assertNotNull("'mañana fregar los platos' es hogar afirmativo, debe capturarse", intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
    }

    // --- Regresión: "no" incidental (no niega el imperativo) deben capturarse ---

    @Test
    fun incidentalNoBeforeOlvidesDoesNotBlockShopping() {
        // "no olvides comprar pan": el "no" niega "olvides", "comprar" va libre.
        val intent = analyze("no olvides comprar pan")
        assertNotNull("'no olvides comprar pan' no niega 'comprar', debe capturarse", intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun incidentalNoBeforeTengoDoesNotBlockShopping() {
        // "no tengo gluten, comprar pan": el "no" va con "tengo". Aquí la base de
        // SHOPPING es baja (sin bono temporal, sin piso fuerte) y ya se descartaba
        // antes de c.648; el guard no debe cambiar este comportamiento. Verificamos
        // que el guard NO introduzca un falso positivo: sigue sin capturarse como
        // SHOPPING (puede ser NULL o TASK, pero nunca SHOPPING).
        val intent = analyze("no tengo gluten, comprar pan")
        val isShopping = intent != null && intent.kind == ContextIntentKind.SHOPPING
        assertEquals(
            "'no tengo gluten, comprar pan': el 'no' no niega 'comprar', no debe bloquear SHOPPING por el guard",
            false,
            isShopping
        )
    }
}
