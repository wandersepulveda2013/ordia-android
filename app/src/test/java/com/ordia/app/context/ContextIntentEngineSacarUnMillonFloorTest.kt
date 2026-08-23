package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Piso c.920 — lateral «sacar un millón de <divisa>» (registrada FUERA
 * de alcance en c.919 por ancla distinta; medida NULL 6/6 en sonda PRE
 * efímera `/tmp/probe920/PreProbe.kt`, motor real vía
 * `tools/run_probe.sh`, 21 casos). Es la forma cotidiana del retiro de
 * efectivo de importe alto en LatAm («sacar un millón de pesos del
 * cajero»): el usuario dicta «un millón de» sin dígitos y la tarea se
 * perdía en silencio mientras «sacar 50 mil pesos» (c.915), «sacar mil
 * pesos» (c.919) y «pagar un millón de pesos» (PAYMENT) ya capturaban.
 *
 * NULL PRE medido: 6/6 candidatas NULL (pesos/euros/dólares/yenes/
 * libras, con acuse, prefijo temporal y grafía sin tilde «millon»),
 * 7/7 guards NULL (negación, hedge, pasado, declarativo, sin divisa,
 * «un millón de gracias» bivalente, «medio millón» FUERA pineado —
 * capturado en c.921, guard convertido precedente c.843),
 * 8/8 regresiones HIT (50 mil pesos/mil euros/50 euros/dinero ERRAND,
 * «pagar un millón de pesos» PAYMENT, «cambiar un millón de pesos»
 * TASK, basura/perro HOUSEHOLD).
 *
 * Causa raíz (hermana de c.919): la rama cantidad de
 * [ERRAND_CASH_FLOOR] exige `\d+` (opcionalmente + «mil») o «mil»
 * desnudo antes de la divisa — «un millón de <divisa>» no ancla y la
 * notificación se descarta pese a llevar la keyword-DIVISA
 * (c.909…c.917).
 *
 * Decisión de dominio deliberada: ERRAND, hermana de c.909…c.919
 * (retiro de efectivo = diligencia con desplazamiento, doctrina
 * c.842/c.862). La ancla «sacar» + «un millón de» + divisa es
 * inequívoca: no colide con «un millón de gracias» (sin divisa), ni
 * con «sacar un millón mañana» (sin divisa — NULL), ni con «medio
 * millón» (cuantificador distinto — pineado NULL aquí, capturado en
 * c.921 con su propia rama «medio millón de <divisa>»).
 *
 * Lockstep TRES puntos (lección c.616/c.751; CERO cambios en
 * [ContextIntent.kt] — las keywords-DIVISA ya existen desde
 * c.909…c.917 y cubren por subcadena; «millón» NO se añade como
 * keyword — bivalente «un millón de gracias», y la keyword-DIVISA
 * basta para que la notificación llegue al análisis):
 * (1) piso — alternativa ADITIVA `un\s+mill[oó]n\s+de\s+(divisa)` en
 *     la rama cantidad (con y sin tilde, precedente «dólar»/«dolar»
 *     c.910 — el motor no normaliza tildes);
 * (2) plantilla de título — la rama «sacar» de [extractTitle] admite
 *     la forma («Sacar un millón de pesos del cajero», grafía
 *     preservada doctrina c.653; el acuse y el prefijo temporal se
 *     despojan porque el match arranca en el verbo, lección c.616);
 * (3) cinturón y tirantes — cláusula de negación de
 *     [imperativeIsNegated] extendida a «no sacar un millón de
 *     <divisa>» (la keyword-DIVISA + el bono temporal elevarían el
 *     score sin pasar por el piso, cuyo lookbehind sí la bloquea —
 *     precedente c.909…c.919).
 *
 * Guard convertido (precedente c.843/c.919): el pineado NULL «sacar
 * un millón de pesos mañana» de [ContextIntentEngineSacarMilSinDigitoFloorTest]
 * (acotación deliberada c.919) se convierte en regresión de captura
 * documentada — ampliación de alcance, NO degradación del test.
 *
 * Acotado deliberado (UNA forma por ciclo, doctrina anti-overreach
 * c.615): «sacar medio millón de pesos» (cuantificador distinto,
 * medido NULL en la sonda) quedaba FUERA — pineado aquí, capturado
 * en c.921 con su propia rama; «dos millones de pesos» (plural en
 * letra) idem — pineado aquí, capturado en c.922; «tres millones»
 * sigue NULL.
 */
class ContextIntentEngineSacarUnMillonFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ─── Capturas (RED exacto: estos dos métodos) ───────────────────

    @Test
    fun `sacar un millon de divisa captura ERRAND con titulo limpio`() {
        val r1 = analyze("sacar un millón de pesos mañana")
        assertNotNull("«sacar un millón de pesos mañana» debe capturar (NULL hasta c.920)", r1)
        assertEquals(ContextIntentKind.ERRAND, r1!!.kind)
        assertEquals("Sacar un millón de pesos", r1.title)
        assertNotNull("«mañana» debe anclar dueAt", r1.dueAt)

        // Origen explícito + grafía sin tilde (precedente «dolar» c.910).
        val r2 = analyze("sacar un millon de pesos del cajero")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.ERRAND, r2!!.kind)
        assertEquals("Sacar un millon de pesos del cajero", r2.title)

        // Divisa fría «yenes» (c.917) en la forma «un millón de».
        val r3 = analyze("sacar un millón de yenes el viernes")
        assertNotNull(r3)
        assertEquals(ContextIntentKind.ERRAND, r3!!.kind)
        assertEquals("Sacar un millón de yenes", r3.title)
        assertNotNull("«el viernes» debe anclar dueAt", r3.dueAt)
    }

    @Test
    fun `acuse y prefijo temporal se despojan del titulo un millon`() {
        // Acuse «vale, …» (lección c.616).
        val r1 = analyze("vale, sacar un millón de dólares")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.ERRAND, r1!!.kind)
        assertEquals("Sacar un millón de dólares", r1.title)

        // Prefijo temporal «mañana …» + divisa bivalente «libras» (c.912).
        val r2 = analyze("mañana sacar un millón de libras")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.ERRAND, r2!!.kind)
        assertEquals("Sacar un millón de libras", r2.title)
        assertNotNull("«mañana» debe anclar dueAt", r2.dueAt)
    }

    // ─── Guards (NULL deseado) — verdes desde RED ───────────────────

    @Test
    fun `guards anti-overreach permanecen NULL un millon`() {
        assertNull("negación inmediata (lookbehind)", analyze("no sacar un millón de pesos mañana"))
        assertNull("duda (hedge c.649)", analyze("quizá saque un millón de pesos mañana"))
        assertNull("narrativa pasado", analyze("saqué un millón de pesos ayer"))
        assertNull("declarativo sin imperativo", analyze("el premio es un millón de euros"))
        assertNull("sin divisa no hay ancla", analyze("sacar un millón mañana"))
        assertNull("«un millón de gracias» agradecimiento bivalente", analyze("un millón de gracias por todo"))
        // «medio millón»: el pineado NULL de c.920 se convirtió en
        // captura en c.921 (rama «medio millón de <divisa>» en
        // [ERRAND_CASH_FLOOR]) — precedente c.843: ampliación de
        // alcance documentada, NO degradación del test.
        val medio = analyze("sacar medio millón de pesos mañana")
        assertNotNull("«medio millón de pesos» captura desde c.921", medio)
        assertEquals(ContextIntentKind.ERRAND, medio!!.kind)
        // Plural en letra: el pineado NULL de c.920 se convirtió en
        // captura en c.922 (rama «dos millones de <divisa>» en
        // [ERRAND_CASH_FLOOR]) — precedente c.843: ampliación de
        // alcance documentada, NO degradación del test.
        val dos = analyze("sacar dos millones de pesos mañana")
        assertNotNull("«dos millones de pesos» captura desde c.922", dos)
        assertEquals(ContextIntentKind.ERRAND, dos!!.kind)
    }

    // ─── Regresiones (HIT esperado) — verdes desde RED ──────────────

    @Test
    fun `regresiones hermanas intactas un millon`() {
        val mil = analyze("sacar 50 mil pesos mañana") // piso c.915
        assertNotNull(mil)
        assertEquals(ContextIntentKind.ERRAND, mil!!.kind)
        assertEquals("Sacar 50 mil pesos", mil.title)

        val milSinDigito = analyze("sacar mil euros mañana") // piso c.919
        assertNotNull(milSinDigito)
        assertEquals(ContextIntentKind.ERRAND, milSinDigito!!.kind)

        val euros = analyze("sacar 50 euros mañana") // piso c.909
        assertNotNull(euros)
        assertEquals(ContextIntentKind.ERRAND, euros!!.kind)

        val dinero = analyze("sacar dinero mañana") // piso c.893
        assertNotNull(dinero)
        assertEquals(ContextIntentKind.ERRAND, dinero!!.kind)

        // «pagar un millón de pesos» sigue PAYMENT (verbo distinto).
        val pago = analyze("pagar un millón de pesos el viernes")
        assertNotNull(pago)
        assertEquals(ContextIntentKind.PAYMENT, pago!!.kind)

        // «cambiar un millón de pesos por dólares» sigue TASK (piso c.710).
        val cambiar = analyze("cambiar un millón de pesos por dólares")
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
