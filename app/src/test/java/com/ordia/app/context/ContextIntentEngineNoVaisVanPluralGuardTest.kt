package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guard c.1091 — lateral ABIERTA (1) de mi candidata S c.1007/c.1009
 * [RESUELTA en este ciclo, este lado; DISJUNTO de SU «no vas a…» c.1044]:
 * la 2ª/3ª persona PLURAL «no vais a…» / «no van a…» / «no piensan/
 * quieren + infinitivo» se descarta TODA (extensión de la 2ª persona
 * singular c.1044 SOBRE [planWrapperIsNegated] c.1009). Medida PRE con
 * sonda efímera `/tmp/probe1091/Probe.kt` (motor real vía
 * `tools/run_probe.sh`): 5/5 candidatas capturaban como falso
 * compromiso (HOUSEHOLD/CALL/APPOINTMENT/PAYMENT/SHOPPING) — en captura
 * pasiva persistía EXACTAMENTE lo opuesto de lo dicho (misma clase P1
 * que c.681/c.835/c.1009/c.1044). Re-pin legítimo MORE estricto de los
 * pines FUERA c.1044 («vais»/«van» pineados HIT): precedente
 * c.1019/c.1024/c.1041/c.1046.
 * Anti-overreach (alcance de esta clase):
 * (1) afirmativos plurales («vais/van a…») siguen capturando;
 * (2) la coma «no, van a …» (respuesta + plan afirmativo) no casa;
 * (3) inversión «sin»: «no van a irte SIN pagar la luz» — lo que sigue
 * a «sin» SÍ es compromiso, el guard NO dispara;
 * (4) canario: la duda «quizá» sigue descartando (NULL independiente
 * del guard).
 * Determinista (regex), cero random, cero IA fingida.
 */
class ContextIntentEngineNoVaisVanPluralGuardTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Capturas: plural negado → NULL (toda la frase) ----

    @Test
    fun `no vais a llamar no captura`() {
        assertNull(analyze("no vais a llamar a mamá esta noche"))
    }

    @Test
    fun `no vais a sacar al perro no captura`() {
        assertNull(analyze("no vais a sacar al perro esta tarde"))
    }

    @Test
    fun `no van a llamar no captura`() {
        assertNull(analyze("no van a llamar a mamá esta noche"))
    }

    @Test
    fun `no van a comprar no captura`() {
        assertNull(analyze("no van a comprar leche esta tarde"))
    }

    @Test
    fun `no van a pagar no captura`() {
        assertNull(analyze("no van a pagar la luz mañana"))
    }

    @Test
    fun `no piensan ir al medico no captura`() {
        assertNull(analyze("no piensan ir al médico el lunes"))
    }

    @Test
    fun `no quieren pagar no captura`() {
        assertNull(analyze("no quieren pagar la luz esta mañana"))
    }

    // ---- Guards ----

    @Test
    fun `afirmativo plural van a sigue capturando`() {
        val i = analyze("van a llamar a mamá esta noche")
        assertNotNull(i)
        assertEquals(ContextIntentKind.CALL, i!!.kind)
    }

    @Test
    fun `afirmativo plural vais a sigue capturando`() {
        val i = analyze("vais a sacar al perro esta tarde")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
    }

    @Test
    fun `coma no separado afirmativo captura`() {
        val i = analyze("no, van a llamar a mamá esta noche")
        assertNotNull(i)
        assertEquals(ContextIntentKind.CALL, i!!.kind)
    }

    @Test
    fun `inversion sin el guard no dispara`() {
        val i = analyze("no van a irlo sin pagar la luz mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.PAYMENT, i!!.kind)
    }

    @Test
    fun `canario duda sigue null`() {
        assertNull(analyze("quizá no van a llamar a mamá"))
    }

    // ---- Regresiones (singular cubierto c.1009/c.1044 → NULL) ----

    @Test
    fun `regresion primera persona no voy a sigue null`() {
        assertNull(analyze("no voy a llamar a mamá esta noche"))
    }

    @Test
    fun `regresion segunda persona no vas a sigue null`() {
        assertNull(analyze("no vas a pagar la luz esta mañana"))
    }
}
