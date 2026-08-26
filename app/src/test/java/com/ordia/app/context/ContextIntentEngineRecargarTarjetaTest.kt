package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * c.1198 (este lado, primer-marcador-gana) — unidad (b) ABIERTA de MI
 * auditoría c.1197 (clase VIGESIMOTERCERA finanzas domésticas):
 * «recargar la tarjeta (el lunes)» estaba NULL en el motor. La keyword
 * PAYMENT «recarga» (sustantivo) existía pero el infinitivo «recargar»
 * no casaba con ella (olvido silencioso P1: la recarga ordenada caía
 * sin piso; < umbral [MINIMUM_CONFIDENCE]).
 *
 * Fix lockstep DOS puntos (lección c.616/c.652; CERO keywords nuevas — el
 * sustantivo «recarga» ya existe en [ContextIntentKind.PAYMENT], gate
 * c.751): (1) [PAYMENT_VERBS] = «pagar|recargar» — alimenta piso
 * [PAYMENT_FLOOR], guard de negación [imperativeIsNegated] y rama de
 * bonus [scoreSpecificPatterns]; (2) plantilla [extractTitle] rama
 * PAYMENT reconstruye verbo+objeto conservando el verbo (doctrina
 * c.653, grafía preservada en el título) — antes estaba «pagar»
 * hardcoded.
 *
 * Pin de privacidad por diseño (hermana c.1029 contraseña): la unidad
 * (a) «hacer la transferencia al casero el lunes» sigue NULL — la
 * palabra «transferencia» está ALISTADA en la blocklist de
 * [ContextPrivacyFilter] (gesto 1 de [analyze]) por decisión de
 * privacidad FINANCIERA (cierre documentado, NO gap). La unidad (c)
 * permanece ABIERTA para futuro ciclo (UNA por ciclo).
 *
 * PRE medido (sonda efímera `/tmp/probe1198/Sonda.kt` vía
 * `tools/run_probe.sh`, HEAD `59c492f3`): (a) NULL por diseño, (b)
 * NULL medido, regresiones «pagar la luz»/«ir al banco» HIT.
 */
class ContextIntentEngineRecargarTarjetaTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ─── Captura (unidad (b)) ──────────────────────────────────────

    @Test
    fun `recargar la tarjeta captura PAYMENT con titulo limpio`() {
        val intent = analyze("recargar la tarjeta")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
        assertEquals("Recargar la tarjeta", intent.title)
    }

    @Test
    fun `recargar la tarjeta el lunes se resuelve a dia siguiente`() {
        val intent = analyze("recargar la tarjeta el lunes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
        assertTrue(intent!!.dueAt != null)
        // El sanitizer c.606 corta la cola temporal (lección c.617) —
        // dueAt ya la resolvió [extractDateTime].
        assertEquals("Recargar la tarjeta", intent.title)
    }

    @Test
    fun `prefix temporal mañana recargar la tarjeta captura`() {
        val intent = analyze("mañana recargar la tarjeta")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
    }

    @Test
    fun `recargar el saldo lateral ABIERTO por gate de keyword`() {
        // ABIERTO lateral documentado: «saldo» no está en las keywords-OBJETO
        // de PAYMENT (gate c.751) y la doctrina CERO-keywords-nuevas
        // prohíbe abrirla aquí. El verbo cerró el gancho; este guard
        // documenta el NULL medido por la sonda post-fix.
        assertNull(analyze("recargar el saldo del móvil"))
    }

    // ─── Pin de privacidad por diseño (unidad (a)) ────────────────

    @Test
    fun `hacer la transferencia NULL por disenio de privacidad financiera`() {
        assertNull(analyze("hacer la transferencia al casero el lunes"))
    }

    // ─── Guards de negación ────────────────────────────────────────

    @Test
    fun `no recargar la tarjeta queda fuera por guard negacion`() {
        assertNull(analyze("no recargar la tarjeta mañana"))
    }

    @Test
    fun `duda subjuntivo no recargue la tarjeta queda fuera`() {
        assertNull(analyze("no recargue la tarjeta"))
    }

    // ─── Regresiones PAYMENT ───────────────────────────────────────

    @Test
    fun `pagar la luz sigue PAYMENT intacto`() {
        val intent = analyze("pagar la luz")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
        assertEquals("Pagar la luz", intent.title)
    }

    @Test
    fun `pagar el gas sigue PAYMENT intacto`() {
        val intent = analyze("pagar el gas")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
        assertEquals("Pagar el gas", intent.title)
    }

    @Test
    fun `hacer la recarga sustantiva lateral ABIERTO documentado`() {
        // ABIERTO lateral documentado: la frase nombrante «hacer la
        // recarga…» no tiene keyword-OBJETO anclada por su propio verbo
        // (gate c.751, CERO-keywords-nuevas). NULL medido post-fix.
        assertNull(analyze("hacer la recarga de la tarjeta"))
    }

    // ─── Unidad (c) permanece ABIERTA lateral documentada ─────────

    @Test
    fun `adelantar la mensualidad sigue ABIERTA en null lateral`() {
        assertNull(analyze("adelantar la mensualidad del coche"))
    }
}
