package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Piso c.911 — lateral de la familia (1) efectivo/cajero de la clase
 * NOVENA (registrada c.893 en el BACKLOG), hermana directa de
 * c.909/c.910: «sacar <cantidad> pesos». Medida NULL con sonda
 * efímera PRE (`/tmp/probe911/PreProbe.kt`, motor real vía
 * `tools/run_probe.sh`, HEAD `3bc95ba`): 4/4 candidatas NULL
 * (declarativa, del cajero, acuse «vale,», prefijo temporal),
 * 4/4 guards NULL, regresiones HIT (euros/dólares/dinero ERRAND,
 * «pagar 500 pesos» PAYMENT, «cambiar pesos por dólares» TASK) y
 * observaciones NULL («sacar 50 mil pesos» — ancla distinta, FUERA
 * de alcance deliberado; «pesar las maletas», «medirme el peso»).
 * Causa raíz (idéntica a c.909/c.910, lección c.751): la rama
 * cantidad de [ERRAND_CASH_FLOOR] exige la divisa y «pesos» no era
 * keyword de ningún kind — «sacar 2000 pesos mañana» ni llegaba al
 * análisis mientras «pagar 500 pesos» (PAYMENT, keyword «pagar») y
 * «cambiar pesos por dólares» (TASK, piso abierto c.710) ya
 * capturaban.
 *
 * Decisión de dominio deliberada: ERRAND, hermana de c.910 — «sacar
 * N pesos» es la forma cotidiana del retiro de efectivo en LatAm
 * (México/Colombia/Argentina/Chile; doctrina c.842/c.862 «la
 * diligencia gobierna»). La ancla cantidad+divisa es inequívoca en
 * posición de compromiso: no colide con «pagar N pesos» (PAYMENT,
 * verbo distinto), ni con declarativos («la entrada cuesta 500
 * pesos» — sin imperativo «sacar» el piso no casa), ni con la
 * bivalencia «peso» = pesas/balanza (el piso exige «sacar» +
 * cantidad + «pesos» en posición de divisa; la keyword-DIVISA sola
 * suma 0.12 inerte < umbral).
 *
 * Lockstep TRES puntos (lección c.616/c.751):
 * (1) piso — extensión ADITIVA de la rama cantidad de
 *     [ERRAND_CASH_FLOOR]: `(?:euros?|d[oó]lares?)` →
 *     `(?:euros?|d[oó]lares?|pesos?)` (la rama `dinero|efectivo`
 *     casa exactamente igual, cero reescritura);
 * (2) keyword-DIVISA «peso» en [ContextIntentKind.ERRAND]
 *     (subcadena: cubre «peso»/«pesos»; 0.12 sola inerte < umbral —
 *     «la entrada cuesta 500 pesos» sigue descartado aun con bono
 *     temporal 0.22 < 0.45; el piso exige «sacar» + cantidad). Sin
 *     ella la notificación ni llegaría al análisis (lección c.751);
 * (3) plantilla de título — la rama «sacar» de [extractTitle]
 *     admite la divisa («Sacar 500 pesos del cajero», grafía
 *     preservada doctrina c.653; el acuse y el prefijo temporal se
 *     despojan porque el match arranca en el verbo, lección c.616).
 * Cinturón y tirantes: cláusula de negación de [imperativeIsNegated]
 * extendida a «no sacar <cantidad> pesos» (la keyword-DIVISA + el
 * bono temporal podrían elevar el score sin pasar por el piso, cuyo
 * lookbehind sí la bloquea — precedente c.893/c.909/c.910).
 *
 * Acotado deliberado (UNA forma por ciclo, doctrina anti-overreach
 * c.615): la forma «sacar <N> mil pesos» tiene ancla distinta
 * (cuantificador «mil» entre la cantidad y la divisa) y queda FUERA
 * (medida NULL en la sonda, OBS-P1); «libras» queda como lateral a
 * medir (bivalencia «libras» = peso en el Reino Unido requiere
 * medición propia).
 */
class ContextIntentEngineSacarPesosFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ─── Capturas (RED exacto: estos y el lockstep de keyword) ──────

    @Test
    fun `sacar cantidad pesos captura ERRAND con titulo limpio`() {
        val r1 = analyze("sacar 2000 pesos mañana")
        assertNotNull("«sacar 2000 pesos mañana» debe capturar (NULL hasta c.911)", r1)
        assertEquals(ContextIntentKind.ERRAND, r1!!.kind)
        assertEquals("Sacar 2000 pesos", r1.title)
        assertNotNull("«mañana» debe anclar dueAt", r1.dueAt)

        // Origen explícito: se preserva en el título (doctrina c.653).
        val r2 = analyze("sacar 500 pesos del cajero el viernes")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.ERRAND, r2!!.kind)
        assertEquals("Sacar 500 pesos del cajero", r2.title)
        assertNotNull("«el viernes» debe anclar dueAt", r2.dueAt)
    }

    @Test
    fun `acuse y prefijo temporal se despojan del titulo pesos`() {
        // Acuse «vale, …» (lección c.616).
        val r1 = analyze("vale, sacar 1000 pesos")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.ERRAND, r1!!.kind)
        assertEquals("Sacar 1000 pesos", r1.title)

        // Prefijo temporal «mañana …» + destino «atm» (c.896).
        val r2 = analyze("mañana sacar 300 pesos del atm")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.ERRAND, r2!!.kind)
        assertEquals("Sacar 300 pesos del atm", r2.title)
        assertNotNull("«mañana» debe anclar dueAt", r2.dueAt)
    }

    // ─── Lockstep keyword (RED hasta añadirla) ──────────────────────

    @Test
    fun `keyword divisa peso llega a TRIGGER_WORDS (lockstep)`() {
        assertTrue(
            "keyword-DIVISA «peso» (lockstep c.911; subcadena de «pesos»; el verbo «sacar» sigue fuera por bivalente)",
            ContextIntentKind.TRIGGER_WORDS.contains("peso")
        )
    }

    // ─── Guards (NULL deseado) — verdes desde RED ───────────────────

    @Test
    fun `guards anti-overreach permanecen NULL pesos`() {
        assertNull("negación inmediata (lookbehind)", analyze("no sacar 2000 pesos mañana"))
        assertNull("negación cinturón y tirantes", analyze("no sacar 500 pesos del cajero"))
        assertNull("duda (hedge c.649)", analyze("quizá saque 500 pesos mañana"))
        assertNull("narrativa pasado", analyze("saqué 1000 pesos ayer"))
        assertNull("declarativo sin imperativo", analyze("la entrada cuesta 500 pesos"))
        // Forma «mil pesos»: ancla distinta, FUERA de alcance deliberado.
        assertNull("«50 mil pesos» fuera de alcance (ancla distinta)", analyze("sacar 50 mil pesos mañana"))
        // Bivalencia «peso» = pesas/balanza: sin ancla de divisa.
        assertNull("«medirme el peso» bivalente", analyze("medirme el peso mañana"))
        assertNull("«pesar las maletas» verbo distinto", analyze("pesar las maletas mañana"))
    }

    // ─── Regresiones (HIT/NULL esperado) — verdes desde RED ─────────

    @Test
    fun `regresiones hermanas intactas pesos`() {
        val euros = analyze("sacar 50 euros mañana") // piso c.909
        assertNotNull(euros)
        assertEquals(ContextIntentKind.ERRAND, euros!!.kind)
        assertEquals("Sacar 50 euros", euros.title)

        val dolares = analyze("sacar 100 dólares mañana") // piso c.910
        assertNotNull(dolares)
        assertEquals(ContextIntentKind.ERRAND, dolares!!.kind)
        assertEquals("Sacar 100 dólares", dolares.title)

        val dinero = analyze("sacar dinero mañana") // piso c.893
        assertNotNull(dinero)
        assertEquals(ContextIntentKind.ERRAND, dinero!!.kind)

        // «pagar N pesos» sigue siendo PAYMENT (verbo distinto,
        // keyword «pagar»; la keyword-DIVISA solo suma 0.12 inerte a
        // ERRAND). Medido HIT PAYMENT en la sonda PRE.
        val pago = analyze("pagar 500 pesos el viernes")
        assertNotNull(pago)
        assertEquals(ContextIntentKind.PAYMENT, pago!!.kind)

        // «cambiar pesos por dólares» sigue TASK (piso abierto c.710).
        val cambiar = analyze("cambiar pesos por dólares mañana")
        assertNotNull(cambiar)
        assertEquals(ContextIntentKind.TASK, cambiar!!.kind)
    }
}
