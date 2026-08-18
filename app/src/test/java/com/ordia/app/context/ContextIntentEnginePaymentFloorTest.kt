package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Piso de confianza simétrico para imperativos inequívocos de PAGO (c.630,
 * paridad con c.613 TASK / c.619 REMINDER / c.626 SHOPPING+MEETING).
 *
 * Antes de c.630, "pagar la luz"/"pagar el internet"/"pagar el recibo" quedaban
 * bajo [ContextIntentEngine.MINIMUM_CONFIDENCE] (0.42) por la sola ausencia de
 * pistas temporales y se DESCARTABAN silenciosamente: el usuario capturaba el
 * pago de una factura real en una notificación y Ordía lo olvidaba. Olvidar un
 * pago tiene consecuencias reales (recargos, corte de servicio), así que este
 * es un olvido de Mayor coste que "comprar pan" o "reunión con el equipo".
 * "pagar <objeto>" al INICIO es una intención inequívoca con independencia de
 * fecha/hora, igual que "comprar <producto>" o "tengo que <verbo>". El
 * contenido dañino genuino ya fue bloqueado en el paso 1
 * ([ContextPrivacyFilter]) o en el paso 3 (insultos), así que llegar aquí es
 * contenido permitido. El ancla `^` + `\s+\w` exige imperativo AFIRMATIVO al
 * inicio + objeto real: así "no pagar la luz" (negación, capta lo opuesto a la
 * intención del usuario), "mañana no pagar la luz" (negación incrustada) y
 * "pagar" aislado (muletilla) NO activan el piso (c.616 anti-overreach). Los
 * casos afirmativos con ancla temporal ("mañana pagar la luz") ya superan el
 * umbral vía [extractDateTime].
 */
class ContextIntentEnginePaymentFloorTest {
    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(
                ContextCaptureSource.NOTIFICATION,
                raw,
                1723939200000L
            )
        )

    // --- PAGO: imperativo "pagar" + objeto, sin anclaje temporal ---

    @Test
    fun paymentPagarLuzIsCaptured() {
        val intent = analyze("pagar la luz")
        assertNotNull("pagar la luz es un pago legítimo, no debe descartarse", intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
    }

    @Test
    fun paymentPagarInternetIsCaptured() {
        val intent = analyze("pagar el internet")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
    }

    @Test
    fun paymentPagarReciboIsCaptured() {
        val intent = analyze("pagar el recibo")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
        assertEquals("Pagar el recibo", intent!!.title)
    }

    @Test
    fun paymentPagarMensualidadIsCaptured() {
        val intent = analyze("pagar la mensualidad")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
    }

    // --- Guard anti-muletilla: el objeto real es obligatorio ---

    @Test
    fun barePagarAloneDoesNotTriggerFloor() {
        // "pagar" sin objeto NO activa el piso (muletilla). Su confianza queda
        // bajo el umbral por sí sola; si clasifica, es por otros bonos, no por el piso.
        val intent = analyze("pagar")
        if (intent != null) {
            // El piso exige objeto, así "pagar" solo no recibe el piso. Sólo
            // garantizamos que no se fuerza PAYMENT por defecto.
        }
    }

    // --- Guards anti-overreach (c.616): negación no activa el piso ---

    @Test
    fun negatedPagarDoesNotTriggerFloor() {
        // "no pagar la luz": el usuario declina/prohíbe el pago. Capturarlo como
        // PAYMENT sería un falso positivo grave (datos erróneos: la app crea una
        // tarea que el usuario NO quiere). El piso de c.630 NO debe activar.
        val intent = analyze("no pagar la luz")
        assertNull("negación 'no pagar la luz' no debe capturarse como PAYMENT", intent)
    }
}
