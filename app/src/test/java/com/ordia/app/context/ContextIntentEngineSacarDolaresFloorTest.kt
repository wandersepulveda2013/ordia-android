package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Piso c.910 — lateral de la familia (1) efectivo/cajero de la clase
 * NOVENA (registrada c.893 en el BACKLOG), hermana directa de c.909:
 * «sacar <cantidad> dólares». Medida NULL con sonda efímera PRE
 * (`/tmp/probe910/PreProbe.kt` + `MeasureProbe.kt`, motor real vía
 * `tools/run_probe.sh`, HEAD `c15c4ee`): 4/4 candidatas NULL
 * (declarativa, del cajero, acuse «vale,», prefijo temporal; con y sin
 * tilde), guards NULL, regresiones HIT. La sonda también midió las
 * laterales hermanas pendientes de c.909 y cerró dos cuestiones:
 *  - «coger dinero/efectivo» YA captura como TASK 0.45 vía el piso
 *    abierto «coger <objeto>» (c.716, decisión deliberada documentada:
 *    TASK contra ERRAND/SHOPPING) — NO es NULL, no es bug, no se toca;
 *  - «coger <N> dólares» idem (HIT TASK, piso c.716).
 * Causa raíz (idéntica a c.909, lección c.751): la rama cantidad de
 * [ERRAND_CASH_FLOOR] exige la palabra `euros?` y «dólares» no era
 * keyword de ningún kind — «sacar 100 dólares mañana» ni llegaba al
 * análisis mientras «pagar 50 dólares» (PAYMENT, keyword «pagar») y
 * «cambiar dólares por euros» (TASK, piso abierto c.710) ya capturaban.
 *
 * Decisión de dominio deliberada: ERRAND, hermana de c.909 — «sacar N
 * dólares» es la forma cotidiana del retiro de efectivo (LatAm/Colombia;
 * doctrina c.842/c.862 «la diligencia gobierna»). La ancla
 * cantidad+divisa es inequívoca en posición de compromiso: no colide
 * con «pagar N dólares» (PAYMENT, verbo distinto), ni con declarativos
 * («la entrada cuesta 50 dólares» — sin imperativo «sacar» el piso no
 * casa), ni con «coger N dólares» (TASK, piso deliberado c.716 con
 * verbo distinto).
 *
 * Lockstep TRES puntos (lección c.616/c.751):
 * (1) piso — extensión ADITIVA de la rama cantidad de
 *     [ERRAND_CASH_FLOOR]: `euros?` → `(?:euros?|d[oó]lares?)` (con y
 *     sin tilde; la rama `dinero|efectivo` casa exactamente igual, cero
 *     reescritura);
 * (2) keyword-DIVISA «dólar»/«dolar» (ambas grafías, sin normalización
 *     de tildes en el motor — precedente «nómina»/«nomina» c.895b) en
 *     [ContextIntentKind.ERRAND] (subcadena: cubre «dólar»/«dólares»;
 *     0.12 sola inerte < umbral — «la entrada cuesta 50 dólares» sigue
 *     descartado aun con bono temporal 0.22 < 0.45; el piso exige
 *     «sacar» + cantidad). Sin ella la notificación ni llegaría al
 *     análisis (lección c.751);
 * (3) plantilla de título — la rama «sacar» de [extractTitle] admite la
 *     divisa («Sacar 100 dólares del cajero», grafía preservada
 *     doctrina c.653; el acuse y el prefijo temporal se despojan porque
 *     el match arranca en el verbo, lección c.616).
 * Cinturón y tirantes: cláusula de negación de [imperativeIsNegated]
 * extendida a «no sacar <cantidad> dólares» (la keyword-DIVISA + el
 * bono temporal podrían elevar el score sin pasar por el piso, cuyo
 * lookbehind sí la bloquea — precedente c.893/c.909).
 *
 * Acotado deliberado (UNA forma por ciclo, doctrina anti-overreach
 * c.615): otras divisas («libras», «pesos» sin señal de cantidad
 * inequívoca — «sacar 50 mil pesos» tiene ancla distinta) quedan como
 * laterales a medir; la asimetría de KIND «coger dinero» (TASK c.716)
 * vs «sacar dinero» (ERRAND c.893) se documenta como OBSERVACIÓN sin
 * tocar (decisión deliberada c.716).
 */
class ContextIntentEngineSacarDolaresFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ─── Capturas (RED exacto: estos y el lockstep de keyword) ──────

    @Test
    fun `sacar cantidad dolares captura ERRAND con titulo limpio`() {
        val r1 = analyze("sacar 100 dólares mañana")
        assertNotNull("«sacar 100 dólares mañana» debe capturar (NULL hasta c.910)", r1)
        assertEquals(ContextIntentKind.ERRAND, r1!!.kind)
        assertEquals("Sacar 100 dólares", r1.title)
        assertNotNull("«mañana» debe anclar dueAt", r1.dueAt)

        // Origen explícito: se preserva en el título (doctrina c.653).
        val r2 = analyze("sacar 200 dólares del cajero el viernes")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.ERRAND, r2!!.kind)
        assertEquals("Sacar 200 dólares del cajero", r2.title)
        assertNotNull("«el viernes» debe anclar dueAt", r2.dueAt)

        // Sin tilde (teclado rápido): la cantidad casa íntegra y la
        // grafía se preserva en el título (doctrina c.653).
        val r3 = analyze("sacar 50 dolares mañana")
        assertNotNull(r3)
        assertEquals(ContextIntentKind.ERRAND, r3!!.kind)
        assertEquals("Sacar 50 dolares", r3.title)
    }

    @Test
    fun `acuse y prefijo temporal se despojan del titulo dolares`() {
        // Acuse «vale, …» (lección c.616).
        val r1 = analyze("vale, sacar 100 dólares")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.ERRAND, r1!!.kind)
        assertEquals("Sacar 100 dólares", r1.title)

        // Prefijo temporal «mañana …» + destino «atm» (c.896).
        val r2 = analyze("mañana sacar 100 dólares del atm")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.ERRAND, r2!!.kind)
        assertEquals("Sacar 100 dólares del atm", r2.title)
        assertNotNull("«mañana» debe anclar dueAt", r2.dueAt)
    }

    // ─── Lockstep keyword (RED hasta añadirla) ──────────────────────

    @Test
    fun `keyword divisa dolar llega a TRIGGER_WORDS (lockstep)`() {
        assertTrue(
            "keyword-DIVISA «dólar» (lockstep c.910; subcadena de «dólares»; el verbo «sacar» sigue fuera por bivalente)",
            ContextIntentKind.TRIGGER_WORDS.contains("dólar")
        )
        assertTrue(
            "keyword-DIVISA «dolar» sin tilde (sin normalización de tildes en el motor, precedente «nómina»/«nomina» c.895b)",
            ContextIntentKind.TRIGGER_WORDS.contains("dolar")
        )
    }

    // ─── Guards (NULL deseado) — verdes desde RED ───────────────────

    @Test
    fun `guards anti-overreach permanecen NULL dolares`() {
        assertNull("negación inmediata (lookbehind)", analyze("no sacar 100 dólares mañana"))
        assertNull("negación cinturón y tirantes", analyze("no sacar 200 dólares del cajero"))
        assertNull("duda (hedge c.649)", analyze("quizá sacar 100 dólares mañana"))
        assertNull("narrativa pasado", analyze("saqué 100 dólares ayer"))
        assertNull("declarativo sin imperativo", analyze("la entrada cuesta 50 dólares"))
    }

    // ─── Regresiones (HIT/NULL esperado) — verdes desde RED ─────────

    @Test
    fun `regresiones hermanas intactas dolares`() {
        val euros = analyze("sacar 50 euros mañana") // piso c.909
        assertNotNull(euros)
        assertEquals(ContextIntentKind.ERRAND, euros!!.kind)
        assertEquals("Sacar 50 euros", euros.title)

        val dinero = analyze("sacar dinero mañana") // piso c.893
        assertNotNull(dinero)
        assertEquals(ContextIntentKind.ERRAND, dinero!!.kind)

        // «pagar N dólares» sigue siendo PAYMENT (verbo distinto,
        // keyword «pagar»; la keyword-DIVISA solo suma 0.12 inerte a
        // ERRAND). Medido HIT PAYMENT en la sonda PRE.
        val pago = analyze("pagar 50 dólares el viernes")
        assertNotNull(pago)
        assertEquals(ContextIntentKind.PAYMENT, pago!!.kind)

        // «coger N dólares» sigue TASK (piso abierto deliberado c.716);
        // la keyword-DIVISA solo suma 0.12 inerte a ERRAND.
        val coger = analyze("coger 50 dólares del cajero el lunes")
        assertNotNull(coger)
        assertEquals(ContextIntentKind.TASK, coger!!.kind)

        // «cambiar dólares por euros» sigue TASK (piso abierto c.710).
        val cambiar = analyze("cambiar dólares por euros mañana")
        assertNotNull(cambiar)
        assertEquals(ContextIntentKind.TASK, cambiar!!.kind)
    }
}
