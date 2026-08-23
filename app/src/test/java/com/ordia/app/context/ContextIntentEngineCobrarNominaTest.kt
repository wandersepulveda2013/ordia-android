package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * c.895b (ciclo complementario de c.895) — familia (3/8) de la clase
 * NOVENA dinero/banca (sonda c.892, BACKLOG P1): «cobrar la nómina/el
 * reembolso». NULL PRE medido por la sonda persistida
 * `tools/probe/CobrarNominaProbe.kt` (4/4 NULL candidatas; 7/7 guards+
 * laterales NULL; 4/4 regresiones HIT), base `3b3766c` (c.895), motor
 * real vía `tools/run_probe.sh`.
 *
 * Decisión de dominio: TASK (gestión financiera SIN desplazamiento —
 * el dinero entra por cuenta/transferencia; hermana de «revisar el
 * extracto» TASK, doctrina «la diligencia gobierna» ERRAND c.842/c.862
 * gobierna solo cuando hay desplazamiento físico al banco/ATM).
 *
 * Lockstep TRES puntos (lección c.616/c.751): (1) piso en
 * [hasStrongTaskImperative] — ancla-objeto `n[oó]mina|reembolso` con
 * guard `(?<!no )`; (2) keywords-OBJETO «nómina»/«nomina» (verbo
 * «cobrar» NO — bivalente «la compra/los favores»; alimentan
 * TRIGGER_WORDS junto con la keyword «reembolso» c.894 que ya abre la
 * puerta); (3) plantilla de título en [extractTitle] (verbo «cobrar»
 * preservado, doctrina c.653, acuse/temporal despojado).
 *
 * Acotado deliberado: laterales bivalentes sin ancla «cobrar el
 * alquiler/la deuda/la compra» permanecen NULL.
 */
class ContextIntentEngineCobrarNominaTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ─── Capturas (familia 3/8 cobros) ────────────────────────────

    @Test
    fun `cobrar la nomina captura TASK con titulo limpio`() {
        val intent = analyze("cobrar la nómina mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cobrar la nómina", intent.title)
    }

    @Test
    fun `cobrar el reembolso captura TASK`() {
        val intent = analyze("cobrar el reembolso mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cobrar el reembolso", intent.title)
    }

    @Test
    fun `cobrar con dia de semana captura TASK`() {
        val intent = analyze("cobrar la nómina el viernes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `envolvente recuerdame sigue TASK por candado c613`() {
        val intent = analyze("recuérdame cobrar la nómina mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cobrar la nómina", intent.title)
    }

    // ─── Negación (guard anterior a plantilla) ────────────────────

    @Test
    fun `negada no cobrar la nomina no captura`() {
        assertNull(analyze("no cobrar la nómina mañana"))
    }

    @Test
    fun `negada no cobrar el reembolso no captura`() {
        assertNull(analyze("no cobrar el reembolso mañana"))
    }

    // ─── Guards anti-overreach ────────────────────────────────────

    @Test
    fun `duda quizá no captura`() {
        assertNull(analyze("quizá cobrar el reembolso mañana"))
    }

    @Test
    fun `pasado cobre no captura`() {
        assertNull(analyze("ayer cobré la nómina"))
    }

    @Test
    fun `sustantivo el cobro no captura`() {
        assertNull(analyze("el cobro de la nómina llegó"))
    }

    @Test
    fun `bivalente la compra no captura`() {
        assertNull(analyze("cobrar la compra mañana"))
    }

    @Test
    fun `lateral alquiler no captura`() {
        assertNull(analyze("cobrar el alquiler mañana"))
    }

    @Test
    fun `lateral deuda no captura`() {
        assertNull(analyze("cobrar la deuda mañana"))
    }

    // ─── Lockstep: keywords-OBJETO presentes en TASK ──────────────

    @Test
    fun `keyword objeto nomina presente en TASK`() {
        assertTrue(
            ContextIntentKind.TASK.keywords.containsAll(listOf("nómina", "nomina"))
        )
    }

    // ─── Regresiones (envolvente hermanos intacta) ────────────────

    @Test
    fun `regresion ingresar el reembolso sigue ERRAND hermano`() {
        assertEquals(ContextIntentKind.ERRAND, analyze("ingresar el reembolso mañana")?.kind)
    }

    @Test
    fun `regresion revisar el extracto sigue TASK`() {
        assertEquals(ContextIntentKind.TASK, analyze("revisar el extracto del banco mañana")?.kind)
    }

    @Test
    fun `regresion pagar la tarjeta sigue PAYMENT`() {
        assertEquals(ContextIntentKind.PAYMENT, analyze("pagar la tarjeta mañana")?.kind)
    }
}
