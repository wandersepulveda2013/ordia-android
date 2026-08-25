package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guard c.1044 — lateral ABIERTA documentada en c.1007/c.1009 RESUELTA:
 * 2ª persona SINGULAR «no vas a …», «no piensas/quieres/planeas +
 * infinitivo», «no cuentas con + infinitivo» se descarta TODA (igual
 * que la 1ª persona del guard [planWrapperIsNegated] c.1009). Medido
 * PRE con sonda efímera `/tmp/probe1041/Probe.kt` (motor real vía
 * `tools/run_probe.sh`): 7/7 candidatas capturaban como falso
 * compromiso (CALL/HOUSEHOLD/PAYMENT/APPOINTMENT/SHOPPING/ERRAND) —
 * en captura pasiva una notificación que dice «no vas a <x>» persiste
 * EXACTAMENTE lo opuesto de lo dicho (misma clase P1 que c.681/c.835/
 * c.1009; el sujeto de la negación da igual: el contenido de la
 * notificación niega el plan, sea del usuario o dirigida a él).
 * Anti-overreach (alcance fijado por los guards/pines de esta clase):
 * (1) 2ª persona PLURAL «no vais a…» / «no van a…» FUERA — lateral
 * documentada, pineada HIT; resuelta c.1091 (este lado) tras medirla
 * como falso compromiso — ver `ContextIntentEngineNoVaisVanPluralGuardTest`
 * (2) afirmativos de 2ª persona («vas a…») siguen capturando;
 * (3) la coma «no, vas a …» (respuesta + plan afirmativo) no casa;
 * (4) inversión «sin»: «no quieres irte SIN pagar la luz» — lo que
 * sigue a «sin» SÍ es compromiso, el guard NO dispara;
 * (5) control «ya no vas a tomar la medicación» era ya NULL por otra
 * vía y sigue NULL (canario, no del guard).
 * Determinista (regex), cero random, cero IA fingida.
 */
class ContextIntentEngineNoVasSegundaPersonaGuardTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Capturas: 2ª persona singular negada → NULL (toda la frase) ----

    @Test
    fun `no vas a llamar no captura`() {
        assertNull(analyze("no vas a llamar a mamá esta noche"))
    }

    @Test
    fun `no vas a sacar al perro no captura`() {
        assertNull(analyze("no vas a sacar al perro hoy"))
    }

    @Test
    fun `no vas a pagar no captura`() {
        assertNull(analyze("no vas a pagar la luz mañana"))
    }

    @Test
    fun `no piensas ir no captura`() {
        assertNull(analyze("no piensas ir al médico"))
    }

    @Test
    fun `no quieres comprar no captura`() {
        assertNull(analyze("no quieres comprar leche esta tarde"))
    }

    @Test
    fun `no planeas cortarte el pelo no captura`() {
        assertNull(analyze("no planeas cortarte el pelo el sábado"))
    }

    @Test
    fun `no cuentas con llamar no captura`() {
        assertNull(analyze("no cuentas con llamar a mamá"))
    }

    @Test
    fun `control ya no vas a tomar la medicacion sigue null`() {
        assertNull(analyze("ya no vas a tomar la medicación a las 8"))
    }

    // ---- Guards: conducta correcta que NO cambia ----

    @Test
    fun `afirmativo segunda persona sigue capturando`() {
        val i = analyze("vas a llamar a mamá esta noche")
        assertNotNull(i)
        assertEquals(ContextIntentKind.CALL, i!!.kind)
    }

    @Test
    fun `coma no respuesta segunda persona sigue capturando`() {
        val i = analyze("no, vas a llamar a mamá esta noche")
        assertNotNull(i)
        assertEquals(ContextIntentKind.CALL, i!!.kind)
    }

    @Test
    fun `inversion sin segunda persona lo que sigue a sin si es compromiso`() {
        val i = analyze("no quieres irte sin pagar la luz mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.PAYMENT, i!!.kind)
    }

    @Test
    fun `control irse sin medicacion era null y sigue null`() {
        assertNull(analyze("no vas a irte sin tomar la medicación a las 8"))
    }

    @Test
    fun `pin segunda persona plural vais queda fuera`() {
        // Re-pin legítimo c.1091: se descarta TODA (ver
        // `ContextIntentEngineNoVaisVanPluralGuardTest`)
        assertNull(analyze("no vais a llamar a mamá esta noche"))
    }

    @Test
    fun `pin segunda persona plural van queda fuera`() {
        // Re-pin legítimo c.1091: se descarta TODA (ver
        // `ContextIntentEngineNoVaisVanPluralGuardTest`)
        assertNull(analyze("no van a llamar a mamá esta noche"))
    }

    // ---- Regresiones ----

    @Test
    fun `regresion primera persona no voy a sigue null`() {
        assertNull(analyze("no voy a llamar a mamá esta noche"))
    }

    @Test
    fun `regresion primera persona no pienso sigue null`() {
        assertNull(analyze("no pienso ir al médico"))
    }

    @Test
    fun `regresion llamar a mama`() {
        val i = analyze("llamar a mamá esta noche")
        assertNotNull(i)
        assertEquals(ContextIntentKind.CALL, i!!.kind)
    }
}
