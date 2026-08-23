package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Piso c.893 — «sacar dinero/efectivo (del cajero)» + «ir al cajero»:
 * PRIMERA familia NULL de la clase NOVENA (gestiones de dinero y banca
 * cotidiana, sonda `NinthClassMoneyProbe.kt` c.892, 8 familias registradas
 * en el BACKLOG). Medida NULL en la sonda PRE persistida
 * `tools/probe/SacarDineroProbe.kt` (6/6 candidatas NULL — declarativas,
 * poseído, acuse, prefijo temporal; 8/8 guards NULL; 7/7 regresiones HIT),
 * ejecutada con el motor real vía `tools/run_probe.sh`.
 *
 * Decisión de dominio deliberada en este ciclo (BACKLOG P1): ERRAND, no
 * TASK — la forma cotidiana implica desplazamiento al cajero (doctrina
 * c.842/c.862 «la diligencia gobierna», hermano de «echar gasolina» c.829
 * y de «ir al banco» c.639). La gestión abstracta («revisar el extracto
 * del banco») sigue siendo TASK; el efectivo se coge yendo.
 *
 * Lockstep TRES puntos (lección c.616/c.751):
 * (1) piso acotado `ERRAND_CASH_FLOOR` — verbo «sacar» + ancla-objeto
 *     `dinero|efectivo` (determinante opcional), ancla `\b` libre y guard
 *     `(?<!no )` (familia c.640/c.643); extensión del destino del piso
 *     `ir a` con `cajero|atm`;
 * (2) keywords-OBJETO/DESTINO «dinero»/«efectivo»/«cajero» en
 *     ContextIntent.kt — NO el verbo «sacar» (bivalente: la basura c.717,
 *     al perro c.740, a bailar, fotos); sin ellas la notificación no llega
 *     al análisis (lección c.751);
 * (3) plantilla de título «sacar» en [extractTitle] (verbo preservado,
 *     lección c.616; grafía del objeto preservada, doctrina c.653);
 *     «ir al cajero» usa el fallback [generateTitle]+[sanitizeTitle] igual
 *     que «ir al banco» (sin plantilla nueva).
 * Cinturón y tirantes: cláusula de negación dedicada en
 * [imperativeIsNegated] (precedente c.829 «echar gasolina»/c.717 «sacar
 * la basura»); la negación de destino «no ir al cajero» comparte la lista
 * extendida en la misma cláusula del piso `ir a`.
 *
 * Acotado deliberado (UNA familia por ciclo, doctrina anti-overreach
 * c.615): «coger dinero» (anglicismo), «sacar 50 euros» (plural) y
 * «sacar la tarjeta» (bivalente) quedan como laterales a medir; las 7
 * familias restantes del BACKLOG (ingresos, divisas dialectal, cobros,
 * membresía, comida, comida-invariante, deberes) intocadas.
 */
class ContextIntentEngineSacarDineroFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ─── Capturas (RED exacto: estos y el lockstep de keywords) ─────

    @Test
    fun `sacar dinero o efectivo captura ERRAND con título limpio`() {
        val r1 = analyze("sacar dinero mañana")
        assertNotNull("«sacar dinero mañana» debe capturar (NULL hasta c.893)", r1)
        assertEquals(ContextIntentKind.ERRAND, r1!!.kind)
        assertEquals("Sacar dinero", r1.title)
        assertNotNull("«mañana» debe anclar dueAt", r1.dueAt)

        // Poseído: el determinante se preserva en el título (doctrina c.653).
        val r2 = analyze("sacar el dinero del cajero mañana")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.ERRAND, r2!!.kind)
        assertEquals("Sacar el dinero del cajero", r2.title)

        // efectivo: segunda ancla-objeto del piso.
        val r3 = analyze("sacar efectivo del cajero el viernes")
        assertNotNull(r3)
        assertEquals(ContextIntentKind.ERRAND, r3!!.kind)
        assertEquals("Sacar efectivo del cajero", r3.title)

        // «antes del viaje» es contenido preservado (doctrina c.832,
        // [sanitizeTitle] conserva el cualificador no-temporal).
        val r4 = analyze("sacar efectivo del atm antes del viaje")
        assertNotNull(r4)
        assertEquals(ContextIntentKind.ERRAND, r4!!.kind)
        assertEquals("Sacar efectivo del atm antes del viaje", r4.title)

        // Acuse se despoja del título (lección c.616).
        val r5 = analyze("vale, sacar dinero")
        assertNotNull(r5)
        assertEquals(ContextIntentKind.ERRAND, r5!!.kind)
        assertEquals("Sacar dinero", r5.title)
    }

    @Test
    fun `ir al cajero captura ERRAND via el piso ir a extendido`() {
        val r = analyze("ir al cajero mañana")
        assertNotNull("«ir al cajero mañana» debe capturar (NULL hasta c.893)", r)
        assertEquals(ContextIntentKind.ERRAND, r!!.kind)
        assertEquals("Ir al cajero", r.title)
        assertNotNull("«mañana» debe anclar dueAt", r.dueAt)
    }

    // ─── Lockstep keywords (RED hasta añadirlas) ────────────────────

    @Test
    fun `keywords objeto destino llegan a TRIGGER_WORDS (lockstep)`() {
        assertTrue(
            "keyword-OBJETO «dinero» (lockstep c.893; el verbo «sacar» sigue fuera por bivalente)",
            ContextIntentKind.TRIGGER_WORDS.contains("dinero")
        )
        assertTrue(
            "keyword-OBJETO «efectivo» (lockstep c.893)",
            ContextIntentKind.TRIGGER_WORDS.contains("efectivo")
        )
        assertTrue(
            "keyword-DESTINO «cajero» (lockstep c.893; hermana de «banco»)",
            ContextIntentKind.TRIGGER_WORDS.contains("cajero")
        )
    }

    // ─── Guards (NULL deseado) — verdes desde RED ───────────────────

    @Test
    fun `guards anti-overreach permanecen NULL`() {
        assertNull("negación inmediata (lookbehind)", analyze("no sacar dinero mañana"))
        assertNull("negación cinturón y tirantes", analyze("no sacar el dinero del cajero"))
        assertNull("duda (hedge c.649)", analyze("quizá sacar dinero mañana"))
        assertNull("narrativa pasado", analyze("saqué dinero ayer"))
        assertNull("verbo aislado", analyze("sacar"))
        assertNull("declarativo sin imperativo", analyze("el dinero está en la mesa"))
        assertNull("bivalente tarjeta fuera del ancla-objeto", analyze("sacar la tarjeta mañana"))
        assertNull("bivalente a bailar fuera del ancla-objeto", analyze("sacar a bailar mañana"))
        assertNull("negación de destino (cinturón y tirantes)", analyze("no ir al cajero mañana"))
    }

    // ─── Regresiones (HIT esperado) — verdes desde RED ──────────────

    @Test
    fun `regresiones hermanas hogar banco y envolvente`() {
        val basura = analyze("sacar la basura mañana") // piso HOUSEHOLD c.717
        assertNotNull(basura)
        assertEquals(ContextIntentKind.HOUSEHOLD, basura!!.kind)

        val perro = analyze("sacar al perro esta tarde") // piso HOUSEHOLD c.740
        assertNotNull(perro)
        assertEquals(ContextIntentKind.HOUSEHOLD, perro!!.kind)

        val banco = analyze("ir al banco mañana") // keyword «banco» c.639
        assertNotNull(banco)
        assertEquals(ContextIntentKind.ERRAND, banco!!.kind)

        val pasar = analyze("pasar por el banco mañana") // piso c.718
        assertNotNull(pasar)
        assertEquals(ContextIntentKind.ERRAND, pasar!!.kind)

        val revisar = analyze("revisar el extracto del banco mañana") // TASK c.691
        assertNotNull(revisar)
        assertEquals(ContextIntentKind.TASK, revisar!!.kind)

        val pagar = analyze("pagar la tarjeta el viernes") // PAYMENT
        assertNotNull(pagar)
        assertEquals(ContextIntentKind.PAYMENT, pagar!!.kind)

        // La envolvente ya ruteaba TASK vía «recuérdame» (c.613); convergente.
        val wrapper = analyze("recuérdame sacar dinero mañana")
        assertNotNull(wrapper)
        assertEquals(ContextIntentKind.TASK, wrapper!!.kind)
    }
}
