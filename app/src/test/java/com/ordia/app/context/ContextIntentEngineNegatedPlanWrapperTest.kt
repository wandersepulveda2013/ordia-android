package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1009: candidata S (TOP) de la sonda persistida c.1007
 * `tools/probe/TenthClassPetProbe.kt` — la negación COMPUESTA de
 * plan/volición («no voy a + infinitivo», «ya no voy a …», «no
 * pienso …», «no quiero …», «no planeo …», «no cuento con …»)
 * NO estaba cubierta por [obligationWrapperIsNegated] (c.681/c.835,
 * sólo envolventes de OBLIGACIÓN/condicional: «no tengo que», «no
 * hay que», «no habría/tendría que», «no debería») ni por los
 * lookbehind `(?<!no )` de los pisos ni por las cláusulas de
 * [imperativeIsNegated] (ambos exigen «no» INMEDIATO al verbo).
 * Medida PRE (motor real vía `tools/run_probe.sh`, HEAD `cf462b9`):
 * micro-sonda efímera `/tmp/probe1007/Probe.kt` (19 casos) — 10/12
 * pisos representativos capturaban «no voy a …» (perro, llamar,
 * luz, leche, médico, pelo, lavadora, taller, medicación, vacunar)
 * + «no pienso ir al médico» HIT APPOINTMENT + «ya no voy a llamar
 * a mamá» HIT CALL; `/tmp/probe1007/Probe2.kt` (10 casos) — «no
 * vamos a ir al médico»/«no quiero llamar»/«no planeo llamar»/«no
 * cuento con llamar» HIT. La captura pasiva persistía EXACTAMENTE
 * lo opuesto de lo dicho (misma clase P1 que c.681/c.835: falso
 * compromiso — recordatorios de cosas que el usuario dijo que NO
 * haría).
 * Fix mínimo (hermano de c.681): NUEVO guard [planWrapperIsNegated]
 * que descarta TODA la clasificación cuando un envolvente de
 * PLAN/VOLICIÓN de 1ª persona está negado — «(ya )no voy/vamos a»,
 * «(ya )no pienso/pensamos/planeo/planeamos/quiero/queremos +
 * infinitivo», «(ya )no cuento/contamos con + infinitivo».
 * Anti-overreach (alcance fijado por los guards de esta clase):
 * (1) 2ª persona SINGULAR «no vas a …» — lateral documentada c.1007
 * RESUELTA en c.1044 (medida PRE: 7/7 candidatas capturaban como
 * falso compromiso; el contenido de la notificación niega el plan
 * sea cual sea el sujeto — ver
 * [ContextIntentEngineNoVasSegundaPersonaGuardTest]); 2ª persona
 * PLURAL «no vais/van a …» sigue FUERA (pineada HIT, no medida);
 * (2) la coma «no, voy a …»
 * (respuesta + plan afirmativo) NO casa (`no\s+` exige espacio);
 * (3) inversión «sin»: «no quiero irme SIN pagar la luz» — lo que
 * sigue a «sin» SÍ es compromiso real («pagar la luz»), así el
 * guard NO dispara si hay infinitivo tras «sin» posterior al
 * envolvente; (4) los afirmativos («voy a …», «pienso …», «tengo
 * que …») siguen capturando intactos.
 */
class ContextIntentEngineNegatedPlanWrapperTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Negación compuesta de plan: TODA la frase se descarta ----

    @Test
    fun `no voy a sacar al perro no captura`() {
        assertNull(analyze("no voy a sacar al perro hoy"))
    }

    @Test
    fun `no voy a llamar no captura`() {
        assertNull(analyze("no voy a llamar a mamá esta noche"))
    }

    @Test
    fun `no voy a pagar no captura`() {
        assertNull(analyze("no voy a pagar la luz mañana"))
    }

    @Test
    fun `no voy a comprar no captura`() {
        assertNull(analyze("no voy a comprar leche esta tarde"))
    }

    @Test
    fun `no voy a ir al medico no captura`() {
        assertNull(analyze("no voy a ir al médico el lunes"))
    }

    @Test
    fun `no voy a cortarme el pelo no captura`() {
        assertNull(analyze("no voy a cortarme el pelo mañana"))
    }

    @Test
    fun `no voy a poner la lavadora no captura`() {
        assertNull(analyze("no voy a poner la lavadora hoy"))
    }

    @Test
    fun `no voy a llevar el coche no captura`() {
        assertNull(analyze("no voy a llevar el coche al taller mañana"))
    }

    @Test
    fun `no voy a tomar la medicacion no captura`() {
        assertNull(analyze("no voy a tomar la medicación a las 8"))
    }

    @Test
    fun `no voy a vacunar no captura`() {
        assertNull(analyze("no voy a vacunar al gato el lunes"))
    }

    @Test
    fun `no vamos a ir no captura`() {
        assertNull(analyze("no vamos a ir al médico el lunes"))
    }

    @Test
    fun `ya no voy a llamar no captura`() {
        assertNull(analyze("ya no voy a llamar a mamá esta noche"))
    }

    @Test
    fun `no pienso ir no captura`() {
        assertNull(analyze("no pienso ir al médico el lunes"))
    }

    @Test
    fun `no quiero llamar no captura`() {
        assertNull(analyze("no quiero llamar a mamá esta noche"))
    }

    @Test
    fun `no planeo llamar no captura`() {
        assertNull(analyze("no planeo llamar a mamá esta noche"))
    }

    @Test
    fun `no cuento con llamar no captura`() {
        assertNull(analyze("no cuento con llamar a mamá esta noche"))
    }

    // ---- Guards de alcance (siguen capturando / comportamiento fijado) ----

    @Test
    fun `afirmativo voy a sigue capturando`() {
        val i = analyze("voy a sacar al perro hoy")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
    }

    @Test
    fun `afirmativo pienso sigue capturando`() {
        val i = analyze("pienso llamar a mamá esta noche")
        assertNotNull(i)
        assertEquals(ContextIntentKind.CALL, i!!.kind)
    }

    @Test
    fun `afirmativo tengo que sigue capturando`() {
        val i = analyze("tengo que pagar la luz mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.PAYMENT, i!!.kind)
    }

    @Test
    fun `coma no respuesta sigue capturando`() {
        val i = analyze("no, voy a llamar a mamá esta noche")
        assertNotNull(i)
        assertEquals(ContextIntentKind.CALL, i!!.kind)
    }

    @Test
    fun `segunda persona no vas a resuelta c1041 ahora descarta`() {
        // Lateral c.1007 resuelta en c.1044: el pin original (HIT CALL)
        // era la contradicción medida — «no vas a …» es falso compromiso
        // igual que la 1ª persona. Cobertura completa en
        // ContextIntentEngineNoVasSegundaPersonaGuardTest.
        assertNull(analyze("no vas a llamar a mamá esta noche"))
    }

    @Test
    fun `inversion sin lo que sigue a sin si es compromiso`() {
        val i = analyze("no quiero irme sin pagar la luz mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.PAYMENT, i!!.kind)
    }

    // ---- Regresiones ----

    @Test
    fun `regresion llamar a mama`() {
        val i = analyze("llamar a mamá esta noche")
        assertNotNull(i)
        assertEquals(ContextIntentKind.CALL, i!!.kind)
    }

    @Test
    fun `regresion sacar al perro`() {
        val i = analyze("sacar al perro a las 8")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
    }
}
