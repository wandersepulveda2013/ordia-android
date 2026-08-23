package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Piso c.919 — lateral OBS-P6 (registrada FUERA de alcance en c.915 por
 * ancla distinta; medida NULL en la sonda hermana): «sacar mil <divisa>»
 * SIN dígito. Es la forma cotidiana hablada del retiro de efectivo en
 * LatAm («sacar mil pesos del cajero») y España («sacar mil euros»):
 * el usuario dicta «mil» sin el número y la tarea se perdía en silencio
 * mientras «sacar 1000 pesos» (c.911) y «sacar 50 mil pesos» (c.915)
 * ya capturaban.
 *
 * NULL PRE medido con sonda efímera (`/tmp/probe919/PreProbe.kt`, motor
 * real vía `tools/run_probe.sh`, 21 casos): 5/5 candidatas NULL
 * (euros/pesos/dólares/yenes/libras, con acuse y prefijo temporal),
 * 8/8 guards NULL (negación, hedge, pasado, declarativo, sin divisa,
 * «mil gracias», número en letra «dos mil pesos», «un millón» FUERA),
 * 8/8 regresiones HIT (50 mil pesos/50 euros/2000 pesos/dinero ERRAND,
 * «pagar 50 mil pesos» PAYMENT, «cambiar 50 mil pesos» TASK,
 * basura/perro HOUSEHOLD).
 *
 * Causa raíz (hermana de c.915): la rama cantidad de
 * [ERRAND_CASH_FLOOR] exige `\d+` antes del cuantificador «mil» —
 * «mil» desnudo no ancla y la notificación se descarta pese a llevar
 * la keyword-DIVISA (c.909…c.917).
 *
 * Decisión de dominio deliberada: ERRAND, hermana de c.909…c.917
 * (retiro de efectivo = diligencia con desplazamiento, doctrina
 * c.842/c.862). La ancla «sacar» + «mil» + divisa es inequívoca: no
 * colide con «mil gracias» (sin divisa), ni con «sacar dos mil pesos»
 * (número en letra — la rama exige «mil» inmediatamente tras «sacar»;
 * pineado NULL c.916, sigue NULL), ni con «sacar un millón de pesos»
 * («millón» ≠ «mil\s», medido NULL — lateral a medir en su ciclo).
 *
 * Lockstep TRES puntos (lección c.616/c.751; CERO cambios en
 * [ContextIntent.kt] — las keywords-DIVISA «euro»/«dólar»/«dolar»/
 * «peso»/«libra»/«yen» ya existen desde c.909…c.917 y cubren por
 * subcadena; «mil» NO se añade como keyword — bivalente
 * «mil gracias», y la keyword-DIVISA basta para que la notificación
 * llegue al análisis):
 * (1) piso — extensión ADITIVA de la rama cantidad de
 *     [ERRAND_CASH_FLOOR]: alternativa `mil\s+(divisa)` (las ramas
 *     `dinero|efectivo` y `\d+…mil?…divisa` casan exactamente igual,
 *     cero reescritura);
 * (2) plantilla de título — la rama «sacar» de [extractTitle] admite
 *     la forma («Sacar mil euros», grafía preservada doctrina c.653;
 *     el acuse y el prefijo temporal se despojan porque el match
 *     arranca en el verbo, lección c.616);
 * (3) cinturón y tirantes — cláusula de negación de
 *     [imperativeIsNegated] extendida a «no sacar mil <divisa>» (la
 *     keyword-DIVISA + el bono temporal elevarían el score sin pasar
 *     por el piso, cuyo lookbehind sí la bloquea — precedente
 *     c.909…c.915).
 *
 * Acotado deliberado (UNA forma por ciclo, doctrina anti-overreach
 * c.615): «sacar un millón de pesos»/«medio millón» quedan FUERA
 * (medidos NULL en la sonda — ancla distinta, a medir en su ciclo);
 * el número en letra «dos mil pesos» sigue NULL pineado (c.916).
 */
class ContextIntentEngineSacarMilSinDigitoFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ─── Capturas (RED exacto: estos dos métodos) ───────────────────

    @Test
    fun `sacar mil divisa sin digito captura ERRAND con titulo limpio`() {
        val r1 = analyze("sacar mil euros mañana")
        assertNotNull("«sacar mil euros mañana» debe capturar (NULL hasta c.919)", r1)
        assertEquals(ContextIntentKind.ERRAND, r1!!.kind)
        assertEquals("Sacar mil euros", r1.title)
        assertNotNull("«mañana» debe anclar dueAt", r1.dueAt)

        // Origen explícito: se preserva en el título (doctrina c.653).
        val r2 = analyze("sacar mil pesos del cajero")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.ERRAND, r2!!.kind)
        assertEquals("Sacar mil pesos del cajero", r2.title)

        // Divisa bivalente «libras» (c.912) en la forma sin dígito.
        val r3 = analyze("sacar mil libras el viernes")
        assertNotNull(r3)
        assertEquals(ContextIntentKind.ERRAND, r3!!.kind)
        assertEquals("Sacar mil libras", r3.title)
        assertNotNull("«el viernes» debe anclar dueAt", r3.dueAt)
    }

    @Test
    fun `acuse y prefijo temporal se despojan del titulo mil sin digito`() {
        // Acuse «vale, …» (lección c.616).
        val r1 = analyze("vale, sacar mil dólares")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.ERRAND, r1!!.kind)
        assertEquals("Sacar mil dólares", r1.title)

        // Prefijo temporal «mañana …» + divisa fría «yenes» (c.917).
        val r2 = analyze("mañana sacar mil yenes")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.ERRAND, r2!!.kind)
        assertEquals("Sacar mil yenes", r2.title)
        assertNotNull("«mañana» debe anclar dueAt", r2.dueAt)
    }

    // ─── Guards (NULL deseado) — verdes desde RED ───────────────────

    @Test
    fun `guards anti-overreach permanecen NULL mil sin digito`() {
        assertNull("negación inmediata (lookbehind)", analyze("no sacar mil euros mañana"))
        assertNull("duda (hedge c.649)", analyze("quizá saque mil euros mañana"))
        assertNull("narrativa pasado", analyze("saqué mil euros ayer"))
        assertNull("declarativo sin imperativo", analyze("la entrada cuesta mil euros"))
        assertNull("sin divisa no hay ancla", analyze("sacar mil mañana"))
        assertNull("«mil gracias» agradecimiento bivalente", analyze("mil gracias por todo"))
        // Número en letra: la rama exige «mil» inmediatamente tras
        // «sacar»; «dos mil pesos» sigue NULL (pineado c.916).
        assertNull("número en letra sin dígito", analyze("sacar dos mil pesos mañana"))
        // «un millón de pesos»: lateral FUERA de alcance (ancla
        // distinta, medida NULL en la sonda — a medir en su ciclo).
        assertNull("«un millón» fuera de alcance", analyze("sacar un millón de pesos mañana"))
    }

    // ─── Regresiones (HIT esperado) — verdes desde RED ──────────────

    @Test
    fun `regresiones hermanas intactas mil sin digito`() {
        val mil = analyze("sacar 50 mil pesos mañana") // piso c.915
        assertNotNull(mil)
        assertEquals(ContextIntentKind.ERRAND, mil!!.kind)
        assertEquals("Sacar 50 mil pesos", mil.title)

        val euros = analyze("sacar 50 euros mañana") // piso c.909
        assertNotNull(euros)
        assertEquals(ContextIntentKind.ERRAND, euros!!.kind)

        val pesos = analyze("sacar 2000 pesos mañana") // piso c.911
        assertNotNull(pesos)
        assertEquals(ContextIntentKind.ERRAND, pesos!!.kind)

        val dinero = analyze("sacar dinero mañana") // piso c.893
        assertNotNull(dinero)
        assertEquals(ContextIntentKind.ERRAND, dinero!!.kind)

        // «pagar N mil pesos» sigue PAYMENT (verbo distinto).
        val pago = analyze("pagar 50 mil pesos el viernes")
        assertNotNull(pago)
        assertEquals(ContextIntentKind.PAYMENT, pago!!.kind)

        // «cambiar N mil pesos por dólares» sigue TASK (piso c.710).
        val cambiar = analyze("cambiar 50 mil pesos por dólares")
        assertNotNull(cambiar)
        assertEquals(ContextIntentKind.TASK, cambiar!!.kind)

        // Quehaceres hermanos con «sacar» intactos (c.717/c.740).
        val basura = analyze("sacar la basura mañana")
        assertNotNull(basura)
        assertEquals(ContextIntentKind.HOUSEHOLD, basura!!.kind)
        val perro = analyze("sacar al perro esta tarde")
        assertNotNull(perro)
        assertEquals(ContextIntentKind.HOUSEHOLD, perro!!.kind)
    }
}
