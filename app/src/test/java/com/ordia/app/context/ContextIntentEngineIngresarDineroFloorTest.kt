package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * c.894 — SEGUNDA familia NULL de la clase NOVENA (dinero/banca cotidiana
 * de la sonda c.892 [NinthClassMoneyProbe]): INGRESOS / DEPÓSITO. Las
 * frases «ingresar el dinero mañana», «ingresar el reembolso el lunes»…
 * produce NULL — olvido silencioso del ingreso pendiente (depósito en la
 * sucursal, hermano de «sacar dinero» c.893).
 *
 * Fix esperado (lockstep TRES puntos, lección c.616/c.751/c.893):
 *  [1] piso-acotado ERRAND sobre el verbo «ingresar» anclado al
 *      OBJETO-moneta `dinero|reembolso` (el verbo NO es keyword; la envolvente
 *      fluye por el carril estándar de envolventes);
 *  [2] keyword-OBJETO «reembolso» nueva en TRIGGERS («dinero» ya existe
 *      c.893);
 *  [3] plantilla de título en la rama ERRAND de [extractTitle]
 *      (determinante preservado, doctrina c.653, hermano c.893) + cláusula
 *      de negación dedicada (cinturón y tirantes, precedente c.717/c.829/
 *      c.842/c.893).
 *
 * Alcance deliberado (una forma por ciclo, convención c.857): la débil
 * «hacer el ingreso mañana» queda como lateral medida fuera de este ciclo;
 * también fuera «depositar el cheque mañana» (verbo distinto, hermano del
 * BACKLOG). Acotamiento triple como c.893.
 *
 * Precisión medida PRE (HEAD 1f11582 vía probe persistida
 * [IngresarDineroProbe]): 6/6 candidatas NULL; 8/8 controles NULL;
 * 4/4 regresiones HIT.
 */
class ContextIntentEngineIngresarDineroFloorTest {

    private fun analyze(
        text: String,
        now: Long = 1_754_500_000_000L
    ): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
        )

    // ─── Capturas (RED exacto: estos y el lockstep de keywords) ─────

    @Test
    fun `ingresar dinero o reembolso captura ERRAND con título limpio`() {
        val r1 = analyze("ingresar el dinero mañana")
        assertNotNull("«ingresar el dinero mañana» debe capturar (NULL hasta c.894)", r1)
        assertEquals(ContextIntentKind.ERRAND, r1!!.kind)
        assertEquals("Ingresar el dinero", r1.title)
        assertNotNull("«mañana» debe anclar dueAt", r1.dueAt)

        // «en el banco» es contenido preservado (doctrina c.653/c.893).
        val r2 = analyze("ingresar dinero en el banco mañana")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.ERRAND, r2!!.kind)
        assertEquals("Ingresar dinero en el banco", r2.title)

        // reembolso: segunda ancla-objeto del piso.
        val r3 = analyze("ingresar el reembolso el lunes")
        assertNotNull(r3)
        assertEquals(ContextIntentKind.ERRAND, r3!!.kind)
        assertEquals("Ingresar el reembolso", r3.title)

        // Sin fecha: dueAt libre.
        val r4 = analyze("ingresar el dinero")
        assertNotNull(r4)
        assertEquals(ContextIntentKind.ERRAND, r4!!.kind)
        assertEquals("Ingresar el dinero", r4.title)
        assertNull("sin expresión temporal no hay dueAt", r4.dueAt)

        // Acuse se despoja del título (lección c.616).
        val r5 = analyze("vale, ingresar el dinero mañana")
        assertNotNull(r5)
        assertEquals(ContextIntentKind.ERRAND, r5!!.kind)
        assertEquals("Ingresar el dinero", r5.title)

        // Prefijo temporal primero (tokenizer c.824).
        val r6 = analyze("mañana ingresar el dinero")
        assertNotNull(r6)
        assertEquals(ContextIntentKind.ERRAND, r6!!.kind)
        assertEquals("Ingresar el dinero", r6.title)
    }

    // ─── Envolventes — verdes desde RED vía el guard estándar ────────

    @Test
    fun `envolventes del piso ingresos rutean a TASK`() {
        val r1 = analyze("recuérdame ingresar el dinero mañana")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.TASK, r1!!.kind)
        assertEquals("Ingresar el dinero", r1.title)

        val r2 = analyze("hay que ingresar el dinero")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.TASK, r2!!.kind)
        assertEquals("Ingresar el dinero", r2.title)
    }

    // ─── Lockstep keywords (RED hasta añadirla) ─────────────────────

    @Test
    fun `keyword objeto llega a TRIGGER_WORDS lockstep`() {
        assertTrue(
            "keyword-OBJETO «reembolso» (lockstep c.894; el verbo «ingresar» sigue fuera por bivalente)",
            ContextIntentKind.TRIGGER_WORDS.contains("reembolso")
        )
        assertTrue(
            "keyword-OBJETO «dinero» ya existente (c.893)",
            ContextIntentKind.TRIGGER_WORDS.contains("dinero")
        )
    }

    // ─── Guards (NULL deseado) — verdes desde RED ────────────────────

    @Test
    fun `guards anti-overreach permanecen NULL`() {
        assertNull("negación inmediata (lookbehind)", analyze("no ingresar el dinero mañana"))
        assertNull("negación cinturón y tirantes", analyze("no ingresar el reembolso el lunes"))
        assertNull("duda (hedge c.649)", analyze("quizá ingresar el dinero mañana"))
        assertNull("narrativa pasado", analyze("ingresé el dinero ayer"))
        assertNull("narrativa pasado 1ª pl.", analyze("ingresamos el dinero ayer"))
        assertNull("verbo aislado", analyze("ingresar"))
        assertNull("declarativo sin imperativo", analyze("el reembolso tardó dos semanas"))
        assertNull("bivalente club fuera del ancla-objeto", analyze("ingresar en el club mañana"))
    }

    // ─── Regresiones (HIT esperado) — verdes desde RED ──────────────

    @Test
    fun `regresiones hermanas cajero banco divisas y envolvente`() {
        val cajero = analyze("sacar el dinero del cajero mañana") // piso ERRAND c.893
        assertNotNull(cajero)
        assertEquals(ContextIntentKind.ERRAND, cajero!!.kind)

        val banco = analyze("pasar por el banco mañana") // piso ERRAND c.718
        assertNotNull(banco)
        assertEquals(ContextIntentKind.ERRAND, banco!!.kind)

        val divisas = analyze("cambiar dólares en el banco mañana") // TASK piso abierto c.710
        assertNotNull(divisas)
        assertEquals(ContextIntentKind.TASK, divisas!!.kind)

        // La envolvente ya ruteaba TASK vía «recuérdame» (c.613).
        val wrapper = analyze("recuérdame ingresar el dinero mañana")
        assertNotNull(wrapper)
        assertEquals(ContextIntentKind.TASK, wrapper!!.kind)
    }
}
