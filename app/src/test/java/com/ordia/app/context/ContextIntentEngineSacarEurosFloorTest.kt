package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Piso c.909 — lateral de la familia (1) efectivo/cajero de la clase
 * NOVENA (registrada c.893 en el BACKLOG): «sacar <cantidad> euros».
 * Medida NULL con sonda efímera PRE (`/tmp/probe909/PreProbe.kt`, motor
 * real vía `tools/run_probe.sh`, HEAD `7551b1f`): 4/4 candidatas NULL
 * (declarativa, del cajero, acuse «vale,», prefijo temporal), 5/5 guards
 * NULL, 4/4 regresiones HIT. Causa raíz DOBLE (lección c.751): la ancla
 * `dinero|efectivo` de [ERRAND_CASH_FLOOR] exige la palabra y «euros» no
 * era keyword de ningún kind — la forma cotidiana con cantidad («sacar 50
 * euros mañana») ni llegaba al análisis y se perdía en silencio mientras
 * «sacar dinero mañana» (c.893) y «pagar 50 euros» (PAYMENT, keyword
 * «pagar») ya capturaban.
 *
 * Decisión de dominio deliberada: ERRAND, hermana de c.893 — «sacar N
 * euros» es la forma cotidiana del retiro de efectivo en el cajero
 * (doctrina c.842/c.862 «la diligencia gobierna»). La ancla
 * cantidad+divisa es inequívoca en posición de compromiso: no colide con
 * «pagar N euros» (PAYMENT, verbo distinto), ni con «sacar 50 fotos»
 * (sin divisa, sigue NULL), ni con declarativos («la tarifa son 50
 * euros» — sin imperativo «sacar» el piso no casa).
 *
 * Lockstep TRES puntos (lección c.616/c.751):
 * (1) piso — extensión ADITIVA de [ERRAND_CASH_FLOOR] con la rama
 *     cantidad `\d+(?:[.,]\d+)?\s+euros?` (admite «1.000 euros» y
 *     decimales «50,50 euros»; la rama `dinero|efectivo` casa exactamente
 *     igual, cero reescritura);
 * (2) keyword-DIVISA «euro» en [ContextIntentKind.ERRAND] (subcadena:
 *     cubre «euro»/«euros»; 0.12 sola inerte < umbral — «la tarifa son
 *     50 euros» sigue descartado; con bono temporal 0.22 < 0.45; el piso
 *     exige «sacar» + cantidad). Sin ella la notificación ni llegaría al
 *     análisis (lección c.751);
 * (3) plantilla de título — la rama «sacar» de [extractTitle] admite la
 *     cantidad («Sacar 50 euros del cajero», grafía preservada doctrina
 *     c.653; el acuse y el prefijo temporal se despojan porque el match
 *     arranca en el verbo, lección c.616).
 * Cinturón y tirantes: cláusula de negación de [imperativeIsNegated]
 * extendida a «no sacar <cantidad> euros» (la keyword-DIVISA + el bono
 * temporal podrían elevar el score sin pasar por el piso, cuyo lookbehind
 * sí la bloquea — precedente c.893).
 *
 * Acotado deliberado (UNA forma por ciclo, doctrina anti-overreach
 * c.615): otras divisas («sacar 100 dólares») y el anglicismo «coger
 * dinero» quedan como laterales a medir; «sacar la tarjeta» (bivalente)
 * permanece NULL deliberado.
 */
class ContextIntentEngineSacarEurosFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ─── Capturas (RED exacto: estos y el lockstep de keyword) ──────

    @Test
    fun `sacar cantidad euros captura ERRAND con titulo limpio`() {
        val r1 = analyze("sacar 50 euros mañana")
        assertNotNull("«sacar 50 euros mañana» debe capturar (NULL hasta c.909)", r1)
        assertEquals(ContextIntentKind.ERRAND, r1!!.kind)
        assertEquals("Sacar 50 euros", r1.title)
        assertNotNull("«mañana» debe anclar dueAt", r1.dueAt)

        // Origen explícito: se preserva en el título (doctrina c.653).
        val r2 = analyze("sacar 200 euros del cajero el viernes")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.ERRAND, r2!!.kind)
        assertEquals("Sacar 200 euros del cajero", r2.title)
        assertNotNull("«el viernes» debe anclar dueAt", r2.dueAt)

        // Miles con separador: la cantidad casa íntegra.
        val r3 = analyze("sacar 1.000 euros del cajero mañana")
        assertNotNull(r3)
        assertEquals(ContextIntentKind.ERRAND, r3!!.kind)
        assertEquals("Sacar 1.000 euros del cajero", r3.title)
    }

    @Test
    fun `acuse y prefijo temporal se despojan del titulo`() {
        // Acuse «vale, …» (lección c.616).
        val r1 = analyze("vale, sacar 50 euros")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.ERRAND, r1!!.kind)
        assertEquals("Sacar 50 euros", r1.title)

        // Prefijo temporal «mañana …» + destino «atm» (c.896).
        val r2 = analyze("mañana sacar 100 euros del atm")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.ERRAND, r2!!.kind)
        assertEquals("Sacar 100 euros del atm", r2.title)
        assertNotNull("«mañana» debe anclar dueAt", r2.dueAt)
    }

    // ─── Lockstep keyword (RED hasta añadirla) ──────────────────────

    @Test
    fun `keyword divisa euro llega a TRIGGER_WORDS (lockstep)`() {
        assertTrue(
            "keyword-DIVISA «euro» (lockstep c.909; subcadena de «euros»; el verbo «sacar» sigue fuera por bivalente)",
            ContextIntentKind.TRIGGER_WORDS.contains("euro")
        )
    }

    // ─── Guards (NULL deseado) — verdes desde RED ───────────────────

    @Test
    fun `guards anti-overreach permanecen NULL`() {
        assertNull("negación inmediata (lookbehind)", analyze("no sacar 50 euros mañana"))
        assertNull("negación cinturón y tirantes", analyze("no sacar 200 euros del cajero"))
        assertNull("duda (hedge c.649)", analyze("quizá sacar 50 euros mañana"))
        assertNull("narrativa pasado", analyze("saqué 50 euros ayer"))
        assertNull("cantidad sin divisa", analyze("sacar 50 fotos mañana"))
        assertNull("declarativo sin imperativo", analyze("la tarifa son 50 euros"))
    }

    // ─── Regresiones (HIT/NULL esperado) — verdes desde RED ─────────

    @Test
    fun `regresiones hermanas intactas`() {
        val dinero = analyze("sacar dinero mañana") // piso c.893
        assertNotNull(dinero)
        assertEquals(ContextIntentKind.ERRAND, dinero!!.kind)
        assertEquals("Sacar dinero", dinero.title)

        val efectivo = analyze("sacar efectivo del cajero el viernes") // piso c.893
        assertNotNull(efectivo)
        assertEquals(ContextIntentKind.ERRAND, efectivo!!.kind)

        // «pagar N euros» sigue siendo PAYMENT (verbo distinto, keyword
        // «pagar»; la keyword-DIVISA «euro» solo suma 0.12 inerte a ERRAND).
        val pago = analyze("pagar 50 euros el viernes")
        assertNotNull(pago)
        assertEquals(ContextIntentKind.PAYMENT, pago!!.kind)

        // Bivalente deliberado: la tarjeta NO es divisa ni ancla-objeto.
        assertNull("«sacar la tarjeta» sigue NULL deliberado", analyze("sacar la tarjeta mañana"))
    }
}
