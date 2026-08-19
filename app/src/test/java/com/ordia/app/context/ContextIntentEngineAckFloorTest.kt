package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guard de piso para prefijos de acuse en SHOPPING/PAYMENT (c.651).
 *
 * Defecto descubierto por probe JVM fuente real (hallazgo secundario c.650):
 * los pisos [ContextIntentEngine.hasStrongShoppingImperative] y
 * [ContextIntentEngine.hasStrongPaymentImperative] conservaban el ancla `^`
 * original (c.626/c.630), que exigía el verbo al INICIO de la notificación.
 * Así TODO imperativo de compra/pago con prefijo de acuse ("sí, comprar pan",
 * "vale, comprar pan", "ok, pagar el recibo") se DESCARTABA — olvido
 * silencioso P1: sin pista temporal, el bono de [ContextIntentEngine.extractDateTime]
 * no compensa la base baja (SHOPPING 0.37 / PAYMENT 0.42 < MINIMUM_CONFIDENCE).
 * El reanclaje era seguro tras c.648 ([ContextIntentEngine.imperativeIsNegated]
 * descarta la negación a nivel kind en cualquier posición) y se aplicó igual
 * que c.643 (HOUSEHOLD) y c.647 (MEETING/EXERCISE/ERRAND/STUDY):
 * `^` → `\b(?<!no )`.
 *
 * Cobertura:
 * - 6 prefijos de acuse capturados (RED pre-fix → GREEN post-fix).
 * - 4 afirmativos ya cubiertos (regresión: comportamiento intacto).
 * - 5 guards anti-overreach intactos (negación c.648, duda c.649,
 *   condición c.650, "no" incidental).
 * - 2 verbos aislados (muletilla) siguen sin activar el piso.
 */
class ContextIntentEngineAckFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(
                ContextCaptureSource.NOTIFICATION,
                raw,
                1723939200000L
            )
        )

    // --- Prefijos de acuse: ahora capturados (olvido silencioso cerrado) ---

    @Test
    fun ackSiShoppingIsCaptured() {
        val intent = analyze("sí, comprar pan")
        assertNotNull("'sí, comprar pan' es compra confirmada", intent)
        assertEquals(ContextIntentKind.SHOPPING, intent!!.kind)
    }

    @Test
    fun ackValeShoppingIsCaptured() {
        val intent = analyze("vale, comprar pan")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.SHOPPING, intent!!.kind)
    }

    @Test
    fun ackOkShoppingIsCaptured() {
        val intent = analyze("ok, comprar leche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.SHOPPING, intent!!.kind)
    }

    @Test
    fun ackOkPaymentIsCaptured() {
        val intent = analyze("ok, pagar el recibo")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
    }

    @Test
    fun ackSiPaymentIsCaptured() {
        val intent = analyze("sí, pagar la luz")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
    }

    @Test
    fun ackValePaymentIsCaptured() {
        val intent = analyze("vale, pagar el internet")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
    }

    @Test
    fun ackTitleDropsPrefix() {
        // El prefijo de acuse no debe contaminar el título persistido. c.653b:
        // el objeto preserva su caso original (sin title-case espurio).
        val intent = analyze("sí, comprar pan")
        assertNotNull(intent)
        assertEquals("Comprar pan", intent!!.title)
    }

    // --- Afirmativos preexistentes: regresión (sin acuse siguen capturados) ---

    @Test
    fun plainShoppingStillCaptured() {
        assertEquals(ContextIntentKind.SHOPPING, analyze("comprar pan")!!.kind)
    }

    @Test
    fun plainPaymentStillCaptured() {
        assertEquals(ContextIntentKind.PAYMENT, analyze("pagar el recibo")!!.kind)
    }

    @Test
    fun temporalShoppingStillCaptured() {
        assertEquals(ContextIntentKind.SHOPPING, analyze("mañana comprar pan")!!.kind)
    }

    @Test
    fun temporalPaymentStillCaptured() {
        assertEquals(ContextIntentKind.PAYMENT, analyze("mañana pagar la luz")!!.kind)
    }

    // --- Guards anti-overreach intactos (c.616/c.648/c.649/c.650) ---

    @Test
    fun negatedShoppingStillDiscarded() {
        assertNull(analyze("no comprar pan"))
    }

    @Test
    fun embeddedNegationPaymentStillDiscarded() {
        assertNull(analyze("mañana no pagar la luz"))
    }

    @Test
    fun hedgedShoppingStillDiscarded() {
        assertNull(analyze("quizá comprar pan"))
    }

    @Test
    fun conditionalShoppingStillDiscarded() {
        assertNull(analyze("si puedo comprar pan"))
    }

    @Test
    fun conditionalPaymentStillDiscarded() {
        assertNull(analyze("si puedo pagar el recibo"))
    }

    @Test
    fun incidentalNoReminderStillTask() {
        // "no" niega "olvides", "comprar" va libre: recordatorio legítimo.
        assertEquals(ContextIntentKind.TASK, analyze("no olvides comprar pan")!!.kind)
    }

    // --- Los imperativos envolventes NO son acuse: el verbo subordinado no
    // --- roba el kind al recordatorio/tarea que lo gobierna (regresión c.651) ---

    @Test
    fun reminderWrapperKeepsReminderKind() {
        // "avísame pagar la luz": el pago es CONTENIDO del recordatorio.
        assertEquals(ContextIntentKind.REMINDER, analyze("avísame pagar la luz")!!.kind)
    }

    @Test
    fun taskWrapperKeepsTaskKind() {
        // "recuérdame comprar pan": la compra es CONTENIDO de la tarea.
        assertEquals(ContextIntentKind.TASK, analyze("recuérdame comprar pan")!!.kind)
    }

    @Test
    fun statementPrefixDoesNotFireFloor() {
        // "no tengo gluten, comprar pan": el prefijo es una declaración, no un
        // acuse — el piso no debe activarse (documentado en c.648).
        val intent = analyze("no tengo gluten, comprar pan")
        val isShopping = intent != null && intent.kind == ContextIntentKind.SHOPPING
        assertEquals(false, isShopping)
    }

    // --- Verbo aislado (muletilla): el guard `\s+\w` sigue exigiendo objeto ---

    @Test
    fun bareComprarIsNotCaptured() {
        assertNull(analyze("comprar"))
    }

    @Test
    fun barePagarIsNotCaptured() {
        assertNull(analyze("pagar"))
    }
}
