package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1008: candidata S (TOP sistémica) de la fila DÉCIMA (MASCOTAS) del
 * BACKLOG — guard de envolvente de PLAN/VOLICIÓN negado. La negación
 * española compuesta de plan/volición en 1ª persona («no voy a + inf.»,
 * «no vamos a …», «no pienso/pensamos …», «no quiero/queremos …»,
 * «no planeo/planeamos …», «no cuento/contamos con …») NO estaba
 * cubierta: [obligationWrapperIsNegated] (c.681/c.835) sólo cubre
 * obligación/condicional, y los lookbehind `(?<!no )` + cláusulas de
 * [imperativeIsNegated] exigen «no» INMEDIATO al verbo del kind. La
 * captura pasiva persistía EXACTAMENTE lo opuesto de lo dicho (misma
 * clase P1 que c.681/c.835). Medida PRE con sonda efímera
 * `/tmp/probe1008/Probe.kt` (motor real vía `tools/run_probe.sh`, HEAD
 * `199e251`): 13/16 formas de la batería HIT (falso positivo —
 * HOUSEHOLD/CALL/SHOPPING/PAYMENT/ERRAND/APPOINTMENT), 3/16 ya NULL
 * por otras vías («no voy a tomar la medicación», «no quiero comprar
 * leche», «no cuento con ir a la reunión»); 7/7 afirmativas HIT
 * (regresión); 2ª persona «no vas a llamar a mamá» HIT CALL 0.57
 * (FUERA de alcance documentado — lateral registrada; RESUELTA en
 * c.1044: medida PRE 7/7 falsos compromisos, guard extendido a 2ª
 * persona singular — ver ContextIntentEngineNoVasSegundaPersonaGuardTest).
 * Fix mínimo
 * (hermano de c.681/c.835): nuevo guard [planWrapperIsNegated] en
 * [scoreKind] que descarta TODA la clasificación (la frase entera
 * niega el plan, no un kind concreto). Determinista (regex), sin IA
 * fingida. Anti-overreach: 2ª persona SINGULAR («no vas a…») resuelta
 * en c.1044 (era la contradicción medida); 2ª persona PLURAL («no vais/
 * van a…») y presente simple («no voy al super») NO se tocan —
 * comportamiento pineado.
 */
class ContextIntentEnginePlanWrapperNegatedTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Negación compuesta de plan/volición (NULL tras el fix) ----

    @Test
    fun `no voy a sacar al perro no captura`() {
        assertNull(analyze("no voy a sacar al perro hoy"))
    }

    @Test
    fun `no voy a llamar no captura`() {
        assertNull(analyze("no voy a llamar a mamá"))
    }

    @Test
    fun `no voy a comprar no captura`() {
        assertNull(analyze("no voy a comprar leche"))
    }

    @Test
    fun `no voy a pagar no captura`() {
        assertNull(analyze("no voy a pagar la luz"))
    }

    @Test
    fun `no voy a cortarme el pelo no captura`() {
        assertNull(analyze("no voy a cortarme el pelo"))
    }

    @Test
    fun `no voy a poner la lavadora no captura`() {
        assertNull(analyze("no voy a poner la lavadora"))
    }

    @Test
    fun `no voy a llevar al taller no captura`() {
        assertNull(analyze("no voy a llevar el coche al taller"))
    }

    @Test
    fun `no voy a vacunar no captura`() {
        assertNull(analyze("no voy a vacunar al perro"))
    }

    @Test
    fun `no pienso ir al medico no captura`() {
        assertNull(analyze("no pienso ir al médico mañana"))
    }

    @Test
    fun `ya no voy a llamar no captura`() {
        assertNull(analyze("ya no voy a llamar a mamá"))
    }

    @Test
    fun `no vamos a limpiar no captura`() {
        assertNull(analyze("no vamos a limpiar la cocina"))
    }

    @Test
    fun `ya no pienso llamar no captura`() {
        assertNull(analyze("ya no pienso llamar al banco"))
    }

    @Test
    fun `no planeo ir al medico no captura`() {
        assertNull(analyze("no planeo ir al médico"))
    }

    // ---- Pins de la familia ya NULL PRE (documentan alcance) ----

    @Test
    fun `no voy a tomar la medicacion sigue null`() {
        assertNull(analyze("no voy a tomar la medicación"))
    }

    @Test
    fun `no quiero comprar sigue null`() {
        assertNull(analyze("no quiero comprar leche"))
    }

    @Test
    fun `no cuento con ir sigue null`() {
        assertNull(analyze("no cuento con ir a la reunión"))
    }

    // ---- Regresiones afirmativas (intactas) ----

    @Test
    fun `afirmativa voy a sacar al perro intacta`() {
        val i = analyze("voy a sacar al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
    }

    @Test
    fun `afirmativa voy a comprar intacta`() {
        val i = analyze("voy a comprar leche mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.SHOPPING, i!!.kind)
    }

    @Test
    fun `afirmativa sacar al perro intacta`() {
        val i = analyze("sacar al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
    }

    @Test
    fun `afirmativa comprar leche intacta`() {
        val i = analyze("comprar leche")
        assertNotNull(i)
        assertEquals(ContextIntentKind.SHOPPING, i!!.kind)
    }

    @Test
    fun `afirmativa llamar intacta`() {
        val i = analyze("llamar a mamá")
        assertNotNull(i)
        assertEquals(ContextIntentKind.CALL, i!!.kind)
    }

    @Test
    fun `afirmativa pagar intacta`() {
        val i = analyze("pagar la luz")
        assertNotNull(i)
        assertEquals(ContextIntentKind.PAYMENT, i!!.kind)
    }

    @Test
    fun `afirmativa ir al medico intacta`() {
        val i = analyze("ir al médico mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.APPOINTMENT, i!!.kind)
    }

    // ---- FUERA de alcance (pin de comportamiento actual) ----

    @Test
    fun `segunda persona no vas a llamar resuelta c1041 ahora descarta`() {
        // Pin original (HIT CALL 0.57) era la contradicción medida —
        // lateral c.1007 resuelta en c.1044: falso compromiso igual que
        // la 1ª persona. Cobertura completa en
        // ContextIntentEngineNoVasSegundaPersonaGuardTest.
        assertNull(analyze("no vas a llamar a mamá"))
    }

    @Test
    fun `presente simple no voy al super sigue null`() {
        assertNull(analyze("no voy al super"))
    }

    @Test
    fun `segunda persona no vas a comprar sigue null`() {
        assertNull(analyze("no vas a comprar leche"))
    }
}
