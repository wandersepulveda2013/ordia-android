package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Piso c.922 — lateral «sacar dos millones de <divisa>» (registrada
 * FUERA de alcance en c.920/c.921 por plural en letra; medida NULL
 * 6/6 en sonda PRE efímera `/tmp/probe922/PreProbe.kt`, motor real vía
 * `tools/run_probe.sh`, 22 casos). Es la forma cotidiana del retiro de
 * efectivo de importe alto en LatAm («sacar dos millones de pesos del
 * cajero»): el usuario dicta «dos millones de» sin dígitos y la tarea
 * se perdía en silencio mientras «sacar 50 mil pesos» (c.915), «sacar
 * mil pesos» (c.919), «sacar un millón de pesos» (c.920), «sacar medio
 * millón de pesos» (c.921) y «pagar dos millones de pesos» (PAYMENT)
 * ya capturaban.
 *
 * NULL PRE medido: 6/6 candidatas NULL (pesos/euros/dólares/yenes/
 * libras, con acuse, prefijo temporal y origen explícito), 7/7 guards
 * NULL (negación, hedge, pasado, declarativo, sin divisa, «dos
 * millones de personas» declarativo, «tres millones» otro plural en
 * letra FUERA pineado), 9/9 regresiones HIT (50 mil pesos/mil euros/
 * un millón de pesos/medio millón de pesos/dinero ERRAND, «pagar dos
 * millones de pesos» PAYMENT, «cambiar dos millones de pesos» TASK,
 * basura/perro HOUSEHOLD).
 *
 * Causa raíz (hermana de c.921): la rama cantidad de
 * [ERRAND_CASH_FLOOR] exige `\d+` (opcionalmente + «mil»), «mil»
 * desnudo, «un millón de» o «medio millón de» antes de la divisa —
 * «dos millones de <divisa>» no ancla y la notificación se descarta
 * pese a llevar la keyword-DIVISA (c.909…c.917).
 *
 * Decisión de dominio deliberada: ERRAND, hermana de c.909…c.921
 * (retiro de efectivo = diligencia con desplazamiento, doctrina
 * c.842/c.862). La ancla «sacar» + «dos millones de» + divisa es
 * inequívoca: no colide con «dos millones de personas» (sin divisa),
 * ni con «sacar dos millones mañana» (sin divisa — NULL), ni con
 * «tres millones de pesos» (otro plural en letra — pineado NULL aquí,
 * lateral a medir en su ciclo). Solo el plural «dos millones» (el más
 * frecuente) se captura; NO se generaliza a `(?:dos|tres|…)` por
 * doctrina anti-overreach c.615 (UNA forma por ciclo).
 *
 * Lockstep TRES puntos (lección c.616/c.751; CERO cambios en
 * [ContextIntent.kt] — las keywords-DIVISA ya existen desde
 * c.909…c.917 y cubren por subcadena; «millones» NO se añade como
 * keyword — bivalente «dos millones de personas», y la keyword-DIVISA
 * basta para que la notificación llegue al análisis):
 * (1) piso — alternativa ADITIVA `dos\s+millones\s+de\s+(divisa)` en
 *     la rama cantidad;
 * (2) plantilla de título — la rama «sacar» de [extractTitle] admite
 *     la forma («Sacar dos millones de pesos del cajero», grafía
 *     preservada doctrina c.653; el acuse y el prefijo temporal se
 *     despojan porque el match arranca en el verbo, lección c.616);
 * (3) cinturón y tirantes — cláusula de negación de
 *     [imperativeIsNegated] extendida a «no sacar dos millones de
 *     <divisa>» (la keyword-DIVISA + el bono temporal elevarían el
 *     score sin pasar por el piso, cuyo lookbehind sí la bloquea —
 *     precedente c.909…c.921).
 *
 * Guards convertidos (precedente c.843/c.919/c.920/c.921): los
 * pineados NULL «sacar dos millones de pesos mañana» de
 * [ContextIntentEngineSacarUnMillonFloorTest] (acotación deliberada
 * c.920) y de [ContextIntentEngineSacarMedioMillonFloorTest]
 * (acotación deliberada c.921) se convierten en regresiones de
 * captura documentadas — ampliación de alcance, NO degradación de
 * los tests.
 *
 * Acotado deliberado (UNA forma por ciclo, doctrina anti-overreach
 * c.615): «sacar tres millones de pesos» (otro plural en letra,
 * medido NULL en la sonda) queda FUERA — pineado aquí, a medir en su
 * ciclo.
 */
class ContextIntentEngineSacarDosMillonesFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ─── Capturas (RED exacto: estos dos métodos) ───────────────────

    @Test
    fun `sacar dos millones de divisa captura ERRAND con titulo limpio`() {
        val r1 = analyze("sacar dos millones de pesos mañana")
        assertNotNull("«sacar dos millones de pesos mañana» debe capturar (NULL hasta c.922)", r1)
        assertEquals(ContextIntentKind.ERRAND, r1!!.kind)
        assertEquals("Sacar dos millones de pesos", r1.title)
        assertNotNull("«mañana» debe anclar dueAt", r1.dueAt)

        // Origen explícito «del cajero».
        val r2 = analyze("sacar dos millones de pesos del cajero")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.ERRAND, r2!!.kind)
        assertEquals("Sacar dos millones de pesos del cajero", r2.title)

        // Divisa fría «yenes» (c.917) en la forma «dos millones de».
        val r3 = analyze("sacar dos millones de yenes el viernes")
        assertNotNull(r3)
        assertEquals(ContextIntentKind.ERRAND, r3!!.kind)
        assertEquals("Sacar dos millones de yenes", r3.title)
        assertNotNull("«el viernes» debe anclar dueAt", r3.dueAt)
    }

    @Test
    fun `acuse y prefijo temporal se despojan del titulo dos millones`() {
        // Acuse «vale, …» (lección c.616).
        val r1 = analyze("vale, sacar dos millones de dólares")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.ERRAND, r1!!.kind)
        assertEquals("Sacar dos millones de dólares", r1.title)

        // Prefijo temporal «mañana …» + divisa bivalente «libras» (c.912).
        val r2 = analyze("mañana sacar dos millones de libras")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.ERRAND, r2!!.kind)
        assertEquals("Sacar dos millones de libras", r2.title)
        assertNotNull("«mañana» debe anclar dueAt", r2.dueAt)
    }

    // ─── Guards (NULL deseado) — verdes desde RED ───────────────────

    @Test
    fun `guards anti-overreach permanecen NULL dos millones`() {
        assertNull("negación inmediata (lookbehind)", analyze("no sacar dos millones de pesos mañana"))
        assertNull("duda (hedge c.649)", analyze("quizá saque dos millones de pesos mañana"))
        assertNull("narrativa pasado", analyze("saqué dos millones de pesos ayer"))
        assertNull("declarativo sin imperativo", analyze("el premio es dos millones de euros"))
        assertNull("sin divisa no hay ancla", analyze("sacar dos millones mañana"))
        assertNull("declarativo personas sin divisa", analyze("dos millones de personas asistieron"))
        // «tres millones»: otro plural en letra FUERA de alcance
        // (medido NULL en la sonda — a medir en su ciclo).
        assertNull("otro plural en letra fuera de alcance", analyze("sacar tres millones de pesos mañana"))
    }

    // ─── Regresiones (HIT esperado) — verdes desde RED ──────────────

    @Test
    fun `regresiones hermanas intactas dos millones`() {
        val mil = analyze("sacar 50 mil pesos mañana") // piso c.915
        assertNotNull(mil)
        assertEquals(ContextIntentKind.ERRAND, mil!!.kind)
        assertEquals("Sacar 50 mil pesos", mil.title)

        val milSinDigito = analyze("sacar mil euros mañana") // piso c.919
        assertNotNull(milSinDigito)
        assertEquals(ContextIntentKind.ERRAND, milSinDigito!!.kind)

        val unMillon = analyze("sacar un millón de pesos mañana") // piso c.920
        assertNotNull(unMillon)
        assertEquals(ContextIntentKind.ERRAND, unMillon!!.kind)

        val medioMillon = analyze("sacar medio millón de pesos mañana") // piso c.921
        assertNotNull(medioMillon)
        assertEquals(ContextIntentKind.ERRAND, medioMillon!!.kind)

        val dinero = analyze("sacar dinero mañana") // piso c.893
        assertNotNull(dinero)
        assertEquals(ContextIntentKind.ERRAND, dinero!!.kind)

        // «pagar dos millones de pesos» sigue PAYMENT (verbo distinto).
        val pago = analyze("pagar dos millones de pesos el viernes")
        assertNotNull(pago)
        assertEquals(ContextIntentKind.PAYMENT, pago!!.kind)

        // «cambiar dos millones de pesos por dólares» sigue TASK (piso c.710).
        val cambiar = analyze("cambiar dos millones de pesos por dólares")
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
