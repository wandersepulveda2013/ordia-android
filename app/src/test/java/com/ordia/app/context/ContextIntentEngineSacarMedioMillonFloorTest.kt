package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Piso c.921 — lateral «sacar medio millón de <divisa>» (registrada
 * FUERA de alcance en c.920 por cuantificador distinto; medida NULL
 * 6/6 en sonda PRE efímera `/tmp/probe921/PreProbe.kt`, motor real vía
 * `tools/run_probe.sh`, 21 casos). Es la forma cotidiana del retiro de
 * efectivo de importe medio-alto en LatAm («sacar medio millón de
 * pesos del cajero»): el usuario dicta «medio millón de» sin dígitos
 * y la tarea se perdía en silencio mientras «sacar 50 mil pesos»
 * (c.915), «sacar mil pesos» (c.919), «sacar un millón de pesos»
 * (c.920) y «pagar medio millón de pesos» (PAYMENT) ya capturaban.
 *
 * NULL PRE medido: 6/6 candidatas NULL (pesos/euros/dólares/yenes/
 * libras, con acuse, prefijo temporal y grafía sin tilde «millon»),
 * 7/7 guards NULL (negación, hedge, pasado, declarativo, sin divisa,
 * «medio millón de personas» declarativo, «dos millones» plural en
 * letra FUERA pineado), 8/8 regresiones HIT (50 mil pesos/mil euros/
 * un millón de pesos/dinero ERRAND, «pagar medio millón de pesos»
 * PAYMENT, «cambiar medio millón de pesos» TASK, basura/perro
 * HOUSEHOLD).
 *
 * Causa raíz (hermana de c.920): la rama cantidad de
 * [ERRAND_CASH_FLOOR] exige `\d+` (opcionalmente + «mil»), «mil»
 * desnudo o «un millón de» antes de la divisa — «medio millón de
 * <divisa>» no ancla y la notificación se descarta pese a llevar la
 * keyword-DIVISA (c.909…c.917).
 *
 * Decisión de dominio deliberada: ERRAND, hermana de c.909…c.920
 * (retiro de efectivo = diligencia con desplazamiento, doctrina
 * c.842/c.862). La ancla «sacar» + «medio millón de» + divisa es
 * inequívoca: no colide con «medio millón de personas» (sin divisa),
 * ni con «sacar medio millón mañana» (sin divisa — NULL), ni con
 * «dos millones de pesos» (plural en letra — pineado NULL aquí,
 * lateral a medir en su ciclo).
 *
 * Lockstep TRES puntos (lección c.616/c.751; CERO cambios en
 * [ContextIntent.kt] — las keywords-DIVISA ya existen desde
 * c.909…c.917 y cubren por subcadena; «millón» NO se añade como
 * keyword — bivalente «un millón de gracias», y la keyword-DIVISA
 * basta para que la notificación llegue al análisis):
 * (1) piso — alternativa ADITIVA `medio\s+mill[oó]n\s+de\s+(divisa)`
 *     en la rama cantidad (con y sin tilde, precedente «dólar»/«dolar»
 *     c.910 — el motor no normaliza tildes);
 * (2) plantilla de título — la rama «sacar» de [extractTitle] admite
 *     la forma («Sacar medio millón de pesos del cajero», grafía
 *     preservada doctrina c.653; el acuse y el prefijo temporal se
 *     despojan porque el match arranca en el verbo, lección c.616);
 * (3) cinturón y tirantes — cláusula de negación de
 *     [imperativeIsNegated] extendida a «no sacar medio millón de
 *     <divisa>» (la keyword-DIVISA + el bono temporal elevarían el
 *     score sin pasar por el piso, cuyo lookbehind sí la bloquea —
 *     precedente c.909…c.920).
 *
 * Guard convertido (precedente c.843/c.919/c.920): el pineado NULL
 * «sacar medio millón de pesos mañana» de
 * [ContextIntentEngineSacarUnMillonFloorTest] (acotación deliberada
 * c.920) se convierte en regresión de captura documentada —
 * ampliación de alcance, NO degradación del test.
 *
 * Acotado deliberado (UNA forma por ciclo, doctrina anti-overreach
 * c.615): «sacar dos millones de pesos» (plural en letra, medido NULL
 * en la sonda) queda FUERA — pineado aquí, a medir en su ciclo.
 */
class ContextIntentEngineSacarMedioMillonFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ─── Capturas (RED exacto: estos dos métodos) ───────────────────

    @Test
    fun `sacar medio millon de divisa captura ERRAND con titulo limpio`() {
        val r1 = analyze("sacar medio millón de pesos mañana")
        assertNotNull("«sacar medio millón de pesos mañana» debe capturar (NULL hasta c.921)", r1)
        assertEquals(ContextIntentKind.ERRAND, r1!!.kind)
        assertEquals("Sacar medio millón de pesos", r1.title)
        assertNotNull("«mañana» debe anclar dueAt", r1.dueAt)

        // Origen explícito + grafía sin tilde (precedente «dolar» c.910).
        val r2 = analyze("sacar medio millon de pesos del cajero")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.ERRAND, r2!!.kind)
        assertEquals("Sacar medio millon de pesos del cajero", r2.title)

        // Divisa fría «yenes» (c.917) en la forma «medio millón de».
        val r3 = analyze("sacar medio millón de yenes el viernes")
        assertNotNull(r3)
        assertEquals(ContextIntentKind.ERRAND, r3!!.kind)
        assertEquals("Sacar medio millón de yenes", r3.title)
        assertNotNull("«el viernes» debe anclar dueAt", r3.dueAt)
    }

    @Test
    fun `acuse y prefijo temporal se despojan del titulo medio millon`() {
        // Acuse «vale, …» (lección c.616).
        val r1 = analyze("vale, sacar medio millón de dólares")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.ERRAND, r1!!.kind)
        assertEquals("Sacar medio millón de dólares", r1.title)

        // Prefijo temporal «mañana …» + divisa bivalente «libras» (c.912).
        val r2 = analyze("mañana sacar medio millón de libras")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.ERRAND, r2!!.kind)
        assertEquals("Sacar medio millón de libras", r2.title)
        assertNotNull("«mañana» debe anclar dueAt", r2.dueAt)
    }

    // ─── Guards (NULL deseado) — verdes desde RED ───────────────────

    @Test
    fun `guards anti-overreach permanecen NULL medio millon`() {
        assertNull("negación inmediata (lookbehind)", analyze("no sacar medio millón de pesos mañana"))
        assertNull("duda (hedge c.649)", analyze("quizá saque medio millón de pesos mañana"))
        assertNull("narrativa pasado", analyze("saqué medio millón de pesos ayer"))
        assertNull("declarativo sin imperativo", analyze("el premio es medio millón de euros"))
        assertNull("sin divisa no hay ancla", analyze("sacar medio millón mañana"))
        assertNull("declarativo personas sin divisa", analyze("medio millón de personas asistió"))
        // «dos millones»: plural en letra FUERA de alcance (medido NULL
        // en la sonda — a medir en su ciclo).
        assertNull("plural en letra fuera de alcance", analyze("sacar dos millones de pesos mañana"))
    }

    // ─── Regresiones (HIT esperado) — verdes desde RED ──────────────

    @Test
    fun `regresiones hermanas intactas medio millon`() {
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
        assertEquals("Sacar un millón de pesos", unMillon.title)

        val dinero = analyze("sacar dinero mañana") // piso c.893
        assertNotNull(dinero)
        assertEquals(ContextIntentKind.ERRAND, dinero!!.kind)

        // «pagar medio millón de pesos» sigue PAYMENT (verbo distinto).
        val pago = analyze("pagar medio millón de pesos el viernes")
        assertNotNull(pago)
        assertEquals(ContextIntentKind.PAYMENT, pago!!.kind)

        // «cambiar medio millón de pesos por dólares» sigue TASK (piso c.710).
        val cambiar = analyze("cambiar medio millón de pesos por dólares")
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
