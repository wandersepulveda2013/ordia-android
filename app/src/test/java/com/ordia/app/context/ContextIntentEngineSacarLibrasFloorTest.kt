package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Piso c.912 — lateral de la familia (1) efectivo/cajero de la clase
 * NOVENA (registrada c.893 en el BACKLOG), hermana directa de
 * c.909/c.910/c.911: «sacar <cantidad> libras». Medida NULL con sonda
 * efímera PRE (`/tmp/probe912/PreProbe.kt`, motor real vía
 * `tools/run_probe.sh`, HEAD `9708574`): 4/4 candidatas NULL
 * (declarativa, del cajero, acuse «vale,», prefijo temporal),
 * 4/4 guards NULL, 3/3 bivalencia NULL («perder 5 libras», «pesar
 * 150 libras», «levantar 100 libras en el gimnasio» — «libras» como
 * unidad de peso), regresiones HIT (euros/dólares/pesos ERRAND,
 * «pagar 50 libras» PAYMENT, «cambiar libras por euros» TASK).
 * Causa raíz (idéntica a c.909/c.910/c.911, lección c.751): la rama
 * cantidad de [ERRAND_CASH_FLOOR] exige la divisa y «libras» no era
 * keyword de ningún kind — «sacar 50 libras mañana» ni llegaba al
 * análisis mientras «pagar 50 libras» (PAYMENT, keyword «pagar») y
 * «cambiar libras por euros» (TASK, piso abierto c.710) ya
 * capturaban.
 *
 * Decisión de dominio deliberada: ERRAND, hermana de c.911 —
 * «sacar N libras» es el retiro de efectivo en libras esterlinas
 * (viajero al Reino Unido; doctrina c.842/c.862 «la diligencia
 * gobierna»). La bivalencia «libras» = unidad de peso no colide
 * con el piso: «sacar» + cantidad + «libras» solo se lee como
 * divisa (nadie «saca» libras del cuerpo); y la keyword-DIVISA sola
 * suma 0.12 inerte < umbral, así «perder 5 libras»/«pesar 150
 * libras»/«levantar 100 libras» siguen descartados aun con bono
 * temporal (0.12 + 0.22 < 0.45, medido NULL en la sonda).
 *
 * Lockstep TRES puntos (lección c.616/c.751):
 * (1) piso — extensión ADITIVA de la rama cantidad de
 *     [ERRAND_CASH_FLOOR]: `(?:euros?|d[oó]lares?|pesos?)` →
 *     `(?:euros?|d[oó]lares?|pesos?|libras?)` (la rama
 *     `dinero|efectivo` casa exactamente igual, cero reescritura);
 * (2) keyword-DIVISA «libra» en [ContextIntentKind.ERRAND]
 *     (subcadena: cubre «libra»/«libras»; 0.12 sola inerte < umbral).
 *     Sin ella la notificación ni llegaría al análisis (lección
 *     c.751);
 * (3) plantilla de título — la rama «sacar» de [extractTitle]
 *     admite la divisa («Sacar 200 libras del cajero», grafía
 *     preservada doctrina c.653; el acuse y el prefijo temporal se
 *     despojan porque el match arranca en el verbo, lección c.616).
 * Cinturón y tirantes: cláusula de negación de [imperativeIsNegated]
 * extendida a «no sacar <cantidad> libras» (la keyword-DIVISA + el
 * bono temporal podrían elevar el score sin pasar por el piso, cuyo
 * lookbehind sí la bloquea — precedente c.893/c.909/c.910/c.911).
 *
 * Acotado deliberado (UNA forma por ciclo, doctrina anti-overreach
 * c.615): el símbolo «£» tiene ancla distinta (símbolo, no palabra)
 * y queda FUERA; otras divisas («yenes», «francos») quedan a medir.
 */
class ContextIntentEngineSacarLibrasFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ─── Capturas (RED exacto: estos y el lockstep de keyword) ──────

    @Test
    fun `sacar cantidad libras captura ERRAND con titulo limpio`() {
        val r1 = analyze("sacar 50 libras mañana")
        assertNotNull("«sacar 50 libras mañana» debe capturar (NULL hasta c.912)", r1)
        assertEquals(ContextIntentKind.ERRAND, r1!!.kind)
        assertEquals("Sacar 50 libras", r1.title)
        assertNotNull("«mañana» debe anclar dueAt", r1.dueAt)

        // Origen explícito: se preserva en el título (doctrina c.653).
        val r2 = analyze("sacar 200 libras del cajero el viernes")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.ERRAND, r2!!.kind)
        assertEquals("Sacar 200 libras del cajero", r2.title)
        assertNotNull("«el viernes» debe anclar dueAt", r2.dueAt)
    }

    @Test
    fun `acuse y prefijo temporal se despojan del titulo libras`() {
        // Acuse «vale, …» (lección c.616).
        val r1 = analyze("vale, sacar 100 libras")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.ERRAND, r1!!.kind)
        assertEquals("Sacar 100 libras", r1.title)

        // Prefijo temporal «mañana …» + destino «atm» (c.896).
        val r2 = analyze("mañana sacar 300 libras del atm")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.ERRAND, r2!!.kind)
        assertEquals("Sacar 300 libras del atm", r2.title)
        assertNotNull("«mañana» debe anclar dueAt", r2.dueAt)
    }

    // ─── Lockstep keyword (RED hasta añadirla) ──────────────────────

    @Test
    fun `keyword divisa libra llega a TRIGGER_WORDS (lockstep)`() {
        assertTrue(
            "keyword-DIVISA «libra» (lockstep c.912; subcadena de «libras»; el verbo «sacar» sigue fuera por bivalente)",
            ContextIntentKind.TRIGGER_WORDS.contains("libra")
        )
    }

    // ─── Guards y bivalencia (NULL deseado) — verdes desde RED ──────

    @Test
    fun `guards anti-overreach y bivalencia permanecen NULL libras`() {
        assertNull("negación inmediata (lookbehind)", analyze("no sacar 50 libras mañana"))
        assertNull("negación cinturón y tirantes", analyze("no sacar 200 libras del cajero"))
        assertNull("duda (hedge c.649)", analyze("quizá saque 100 libras mañana"))
        assertNull("narrativa pasado", analyze("saqué 200 libras ayer"))
        assertNull("declarativo sin imperativo", analyze("la entrada cuesta 50 libras"))
        // Bivalencia «libras» = unidad de peso: la keyword-DIVISA sola
        // suma 0.12 inerte < umbral (medido NULL en sonda PRE/POST).
        assertNull("«perder libras» = peso corporal", analyze("perder 5 libras este mes"))
        assertNull("«pesar libras» = báscula", analyze("pesar 150 libras mañana"))
        assertNull("«levantar libras» = pesas del gimnasio", analyze("levantar 100 libras en el gimnasio"))
    }

    // ─── Regresiones (HIT/NULL esperado) — verdes desde RED ─────────

    @Test
    fun `regresiones hermanas intactas libras`() {
        val euros = analyze("sacar 50 euros mañana") // piso c.909
        assertNotNull(euros)
        assertEquals(ContextIntentKind.ERRAND, euros!!.kind)

        val dolares = analyze("sacar 100 dólares mañana") // piso c.910
        assertNotNull(dolares)
        assertEquals(ContextIntentKind.ERRAND, dolares!!.kind)

        val pesos = analyze("sacar 2000 pesos mañana") // piso c.911
        assertNotNull(pesos)
        assertEquals(ContextIntentKind.ERRAND, pesos!!.kind)
        assertEquals("Sacar 2000 pesos", pesos.title)

        // «pagar N libras» sigue siendo PAYMENT (verbo distinto,
        // keyword «pagar»; la keyword-DIVISA solo suma 0.12 inerte a
        // ERRAND). Medido HIT PAYMENT en la sonda PRE.
        val pago = analyze("pagar 50 libras el viernes")
        assertNotNull(pago)
        assertEquals(ContextIntentKind.PAYMENT, pago!!.kind)

        // «cambiar libras por euros» sigue TASK (piso abierto c.710).
        val cambiar = analyze("cambiar libras por euros mañana")
        assertNotNull(cambiar)
        assertEquals(ContextIntentKind.TASK, cambiar!!.kind)
    }
}
