package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// c.1198->c.1199: lateral (b) de la auditoría c.1197 (clase
// VIGESIMOTERCERA finanzas domésticas) — «recargar la tarjeta».
// PRE medido en sonda efímera (/tmp/FinanzasProbePRE.kt, HEAD d358e3c):
// 5/5 candidatas desnudas/con-temporal NULL (B1 «recargar la tarjeta
// el lunes», B2 «…mañana», B3 «…de crédito esta semana», W4 «ok,
// recargar la tarjeta»), guards 4/4 NULL (negación/pretérito/duda/
// declarativa «la recarga de la tarjeta tardó dos días»), bivalentes
// 2/2 NULL («recargar la página web», «recargar el arma»), envolventes
// intactas vía TASK/REMINDER (W1 avísame→REMINDER 0.47, W2/W3→TASK 0.45).
// Causa: la keyword «recarga» (0.12, dentro de «recargar») + bono
// temporal 0.1 = 0.22 < [MINIMUM_CONFIDENCE] 0.45 — la nota de saldo/
// prepago olvidada tiene coste real (sin saldo no hay llamada/datos).
// Gate c.751: la keyword «recarga» YA existe (0.12); el piso eleva SIN
// keyword nueva (mismo razonamiento c.613/c.1140/c.1149). Fix acotado:
// objeto EXIGIDO «tarjeta(s)» (anti-overreach: «recargar la página
// web/el arma» siguen NULL). Tres puntos lockstep (lección c.616/c.648):
//  piso RECARGA_TARJETA_FLOOR + registro en [WRAPPABLE_PATTERNS] de
//  PAYMENT (envolvente no roba) + plantilla en [extractTitle].
class ContextIntentEngineRecargaTarjetaFloorTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(
            source = ContextCaptureSource.NOTIFICATION,
            rawText = text,
            timestampMs = 1_700_000_000_000L,
        ),
    )

    // ---------- Capturas (RED -> GREEN): piso acotado PAYMENT ----------

    @Test
    fun `recargar la tarjeta el lunes captura PAYMENT`() {
        val intent = analyze("recargar la tarjeta el lunes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
        assertTrue(intent.confidence >= 0.45f)
        assertTrue(intent.dueAt != null)
        assertEquals("Recargar la tarjeta", intent.title)
    }

    @Test
    fun `recargar la tarjeta manana captura PAYMENT`() {
        val intent = analyze("recargar la tarjeta mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
        assertTrue(intent.confidence >= 0.45f)
        assertTrue(intent.dueAt != null)
        assertEquals("Recargar la tarjeta", intent.title)
    }

    @Test
    fun `recargar la tarjeta de credito captura PAYMENT con objeto completo`() {
        val intent = analyze("recargar la tarjeta de crédito esta semana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
        // «esta semana» queda en el título con dueAt=false — lateral familiar
        // de colas temporales YA documentada (c.845/c.852/c.1079/c.1102/c.1125);
        // fuera del alcance de este piso (UNA forma por ciclo, doctrina c.862).
        assertEquals("Recargar la tarjeta de crédito esta semana", intent.title)
        assertFalse(intent.dueAt != null)
    }

    @Test
    fun `ok recargar la tarjeta captura PAYMENT por acuse`() {
        val intent = analyze("ok, recargar la tarjeta")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
        assertEquals("Recargar la tarjeta", intent.title)
    }

    // ---------- Guards (deben quedar NULL) ----------

    @Test
    fun `no voy a recargar la tarjeta queda NULL`() {
        assertNull(analyze("no voy a recargar la tarjeta"))
    }

    @Test
    fun `preterito recargue la tarjeta queda NULL`() {
        assertNull(analyze("recargué la tarjeta ayer"))
    }

    @Test
    fun `duda no se si recargar la tarjeta queda NULL`() {
        assertNull(analyze("no sé si recargar la tarjeta mañana"))
    }

    @Test
    fun `declarativa la recarga de la tarjeta queda NULL`() {
        assertNull(analyze("la recarga de la tarjeta tardó dos días"))
    }

    @Test
    fun `bivalente recargar la pagina web queda NULL por objeto acotado`() {
        assertNull(analyze("recargar la página web"))
    }

    @Test
    fun `bivalente recargar el arma queda NULL por objeto acotado`() {
        assertNull(analyze("recargar el arma"))
    }

    // ---------- Envolvente no roba (registro WRAPPABLE_PATTERNS) ----------

    @Test
    fun `avisame manana recargar la tarjeta sigue REMINDER no PAYMENT`() {
        val intent = analyze("avísame mañana recargar la tarjeta")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.REMINDER, intent!!.kind)
    }

    @Test
    fun `recuerdame recargar la tarjeta sigue TASK no PAYMENT`() {
        val intent = analyze("recuérdame recargar la tarjeta mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `tengo que recargar la tarjeta sigue TASK no PAYMENT`() {
        val intent = analyze("tengo que recargar la tarjeta esta noche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    // ---------- Regresiones ----------

    @Test
    fun `pagar el alquiler sigue PAYMENT`() {
        val intent = analyze("pagar el alquiler el lunes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
    }

    @Test
    fun `pagar la luz sigue PAYMENT`() {
        val intent = analyze("pagar la luz mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
    }
}
