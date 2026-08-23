package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Piso c.917 (renumerado c.915→c.916→c.917 por DOBLE colisión cycle-ID con hermanos c.915 «mil» y c.916 test-only, convención c.857) — lateral de la familia (1) efectivo/cajero de la clase
 * NOVENA (registrada c.893 en el BACKLOG), hermana directa de
 * c.909/c.910/c.911/c.912: «sacar <cantidad> yenes». Medida NULL con
 * sonda efímera PRE (`/tmp/probe915/PreProbe.kt`, motor real vía
 * `tools/run_probe.sh`, HEAD `2e148ff` integrado): 4/4 candidatas NULL
 * (desnuda, temporal «mañana»/«esta tarde», origen «en el
 * aeropuerto»), 3/3 guards NULL (negación inmediata, declarativo
 * «los yenes están caros», verbo distinto «ganar»), regresiones HIT
 * (euros/dólares/pesos/libras ERRAND, «pagar 50 euros» PAYMENT).
 * Causa raíz (idéntica a c.909-c.912, lección c.751): la rama cantidad
 * de [ERRAND_CASH_FLOOR] exige la divisa y «yenes» no era keyword de
 * ningún kind — «sacar 20000 yenes mañana» ni llegaba al análisis.
 *
 * Decisión de dominio deliberada: ERRAND, hermana de c.912 — «sacar N
 * yenes» es el retiro de efectivo en yenes (viajero a Japón; doctrina
 * c.842/c.862 «la diligencia gobierna»). «Yen» es MONOSÉMICA (solo la
 * divisa japonesa; sin bivalencia que guardar, a diferencia de
 * «libras» c.912). La keyword-DIVISA sola suma 0.12 inerte < umbral,
 * así los declarativos financieros («los yenes están caros», «el yen
 * se fortalece») siguen descartados aun con bono temporal (medido
 * NULL en la sonda).
 *
 * Lockstep TRES puntos (lección c.616/c.751):
 * (1) piso — extensión ADITIVA de la rama cantidad de
 *     [ERRAND_CASH_FLOOR]: `(?:euros?|d[oó]lares?|pesos?|libras?)` →
 *     `(?:euros?|d[oó]lares?|pesos?|libras?|yenes?)` (la rama
 *     `dinero|efectivo` casa exactamente igual, cero reescritura);
 * (2) keyword-DIVISA «yen» en [ContextIntentKind.ERRAND]
 *     (subcadena: cubre «yen»/«yenes»; 0.12 sola inerte < umbral).
 *     Sin ella la notificación ni llegaría al análisis (lección
 *     c.751);
 * (3) plantilla de título — la rama «sacar» de [extractTitle]
 *     admite la divisa («Sacar 5000 yenes del cajero», grafía
 *     preservada doctrina c.653; el acuse y el prefijo temporal se
 *     despojan porque el match arranca en el verbo, lección c.616).
 * Cinturón y tirantes: cláusula de negación de [imperativeIsNegated]
 * extendida a «no sacar <cantidad> yenes» (la keyword-DIVISA + el
 * bono temporal podrían elevar el score sin pasar por el piso, cuyo
 * lookbehind sí la bloquea — precedente c.893/c.909-c.912).
 *
 * Acotado deliberado (UNA forma por ciclo, doctrina anti-overreach
 * c.615): el símbolo «¥» tiene ancla distinta (símbolo, no palabra)
 * y queda FUERA; otras divisas frías («francos») quedan a medir.
 */
class ContextIntentEngineSacarYenesFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ─── Capturas (RED exacto: estos y el lockstep de keyword) ──────

    @Test
    fun `sacar cantidad yenes captura ERRAND con titulo limpio`() {
        val r1 = analyze("sacar 20000 yenes mañana")
        assertNotNull("«sacar 20000 yenes mañana» debe capturar (NULL hasta c.917)", r1)
        assertEquals(ContextIntentKind.ERRAND, r1!!.kind)
        assertEquals("Sacar 20000 yenes", r1.title)
        assertNotNull("«mañana» debe anclar dueAt", r1.dueAt)

        // Origen explícito: se preserva en el título (doctrina c.653).
        val r2 = analyze("sacar 5000 yenes del cajero el viernes")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.ERRAND, r2!!.kind)
        assertEquals("Sacar 5000 yenes del cajero", r2.title)
        assertNotNull("«el viernes» debe anclar dueAt", r2.dueAt)
    }

    @Test
    fun `acuse y prefijo temporal se despojan del titulo yenes`() {
        // Acuse «vale, …» (lección c.616).
        val r1 = analyze("vale, sacar 10000 yenes")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.ERRAND, r1!!.kind)
        assertEquals("Sacar 10000 yenes", r1.title)

        // Prefijo temporal «mañana …» + destino «atm» (c.896).
        val r2 = analyze("mañana sacar 30000 yenes del atm")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.ERRAND, r2!!.kind)
        assertEquals("Sacar 30000 yenes del atm", r2.title)
        assertNotNull("«mañana» debe anclar dueAt", r2.dueAt)
    }

    // ─── Lockstep keyword (RED hasta añadirla) ──────────────────────

    @Test
    fun `keyword divisa yen llega a TRIGGER_WORDS (lockstep)`() {
        assertTrue(
            "keyword-DIVISA «yen» (lockstep c.917; subcadena de «yenes»; el verbo «sacar» sigue fuera por bivalente)",
            ContextIntentKind.TRIGGER_WORDS.contains("yen")
        )
    }

    // ─── Guards anti-overreach (NULL deseado) — verdes desde RED ────

    @Test
    fun `guards anti-overreach permanecen NULL yenes`() {
        assertNull("negación inmediata (lookbehind)", analyze("no sacar 20000 yenes mañana"))
        assertNull("negación cinturón y tirantes", analyze("no sacar 5000 yenes del cajero"))
        assertNull("duda (hedge c.649)", analyze("quizá saque 10000 yenes mañana"))
        assertNull("narrativa pasado", analyze("saqué 20000 yenes ayer"))
        assertNull("declarativo sin imperativo", analyze("los yenes están caros esta semana"))
        // Keyword-DIVISA sola inerte < umbral (monosémica, sin
        // bivalencia; 0.12 + 0.22 < 0.45, medido NULL en la sonda).
        assertNull("declarativo financiero singular", analyze("el yen se fortalece este año"))
    }

    // ─── Regresiones (HIT/NULL esperado) — verdes desde RED ─────────

    @Test
    fun `regresiones hermanas intactas yenes`() {
        val euros = analyze("sacar 50 euros mañana") // piso c.909
        assertNotNull(euros)
        assertEquals(ContextIntentKind.ERRAND, euros!!.kind)

        val dolares = analyze("sacar 100 dólares mañana") // piso c.910
        assertNotNull(dolares)
        assertEquals(ContextIntentKind.ERRAND, dolares!!.kind)

        val pesos = analyze("sacar 2000 pesos mañana") // piso c.911
        assertNotNull(pesos)
        assertEquals(ContextIntentKind.ERRAND, pesos!!.kind)

        val libras = analyze("sacar 50 libras mañana") // piso c.912
        assertNotNull(libras)
        assertEquals(ContextIntentKind.ERRAND, libras!!.kind)
        assertEquals("Sacar 50 libras", libras.title)

        // «pagar N yenes» sigue siendo PAYMENT (verbo distinto,
        // keyword «pagar»; la keyword-DIVISA solo suma 0.12 inerte a
        // ERRAND).
        val pago = analyze("pagar 500 yenes el viernes")
        assertNotNull(pago)
        assertEquals(ContextIntentKind.PAYMENT, pago!!.kind)

        // «cambiar yenes por euros» sigue TASK (piso abierto c.710).
        val cambiar = analyze("cambiar yenes por euros mañana")
        assertNotNull(cambiar)
        assertEquals(ContextIntentKind.TASK, cambiar!!.kind)
    }
}
