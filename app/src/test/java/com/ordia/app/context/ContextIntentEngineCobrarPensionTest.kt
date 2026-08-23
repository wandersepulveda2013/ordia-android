package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.897 — laterales salariales de la familia cobros (3/8 clase NOVENA)
 * del hermano c.895b (máster «cobrar la nómina/reembolso» TASK): el
 * máster cubrió `n[oó]min/as?o?|reembolsos?` pero dejó NULL los
 * objetos-hermanos «la pensión/el sueldo/el salario». PRE medido por la
 * sonda persistida `tools/probe/CobrarPensionProbe.kt` (3/3 candidatas
 * NULL; 6/6 guards bivalentes NULL; 5/5 regresiones HIT), base remota
 * `88a6d73` (c.895c), motor real vía `tools/run_probe.sh`.
 *
 * Extensión ADITIVA del lockstep hermano (sin reescritura): (1) objetos
 * del piso acotado ampliados a `pensi[oó]n(?:es)?|sueldos?|salarios?`;
 * (2) keywords-OBJETO «pensión/pension/sueldo/salario» en TASK
 * (alimentan TRIGGER_WORDS, lección c.751); (3) la plantilla de título
 * del hermano `(?<!no )cobrar\s+(.+)` ya captura el objeto completo —
 * no hizo falta tocar [extractTitle]. Guard de bivalentes intacta:
 * «cobrar la compra/el alquiler/la deuda» siguen NULL.
 */
class ContextIntentEngineCobrarPensionTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ─── Capturas (laterales c.897) ───────────────────────────────

    @Test
    fun `cobrar la pension captura TASK con titulo limpio`() {
        val intent = analyze("cobrar la pensión mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cobrar la pensión", intent.title)
    }

    @Test
    fun `cobrar el sueldo captura TASK`() {
        val intent = analyze("cobrar el sueldo esta tarde")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cobrar el sueldo", intent.title)
    }

    @Test
    fun `cobrar el salario captura TASK`() {
        val intent = analyze("cobrar el salario el viernes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cobrar el salario", intent.title)
    }

    @Test
    fun `laterales salariales rutean con fecha marcamiento`() {
        val intent = analyze("cobrar la pensión mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals(true, intent.dueAt != null)
    }

    // ─── Lockstep keywords (hacen que el gatillo TRIGGER_WORDS llegue) ──

    @Test
    fun `keywords objeto pension sueldo salario activan TASK kind`() {
        assertTrueContains(ContextIntentKind.TASK, "pensión")
        assertTrueContains(ContextIntentKind.TASK, "pension")
        assertTrueContains(ContextIntentKind.TASK, "sueldo")
        assertTrueContains(ContextIntentKind.TASK, "salario")
    }

    private fun assertTrueContains(kind: ContextIntentKind, word: String) {
        org.junit.Assert.assertTrue(
            "TASK debe contener la keyword-OBJETO «$word»",
            kind.keywords.contains(word)
        )
    }

    // ─── Guards anti-overreach (objetivo: NULL) ───────────────────

    @Test
    fun `negacion excluida - no cobrar pension`() {
        assertNull(analyze("no cobrar la pensión mañana"))
        assertNull(analyze("no cobrar el sueldo mañana"))
    }

    @Test
    fun `bivalentes sin ancla excluidos - compra alquiler deuda`() {
        assertNull(analyze("cobrar la compra mañana"))
        assertNull(analyze("cobrar el alquiler mañana"))
        assertNull(analyze("cobrar la deuda mañana"))
    }

    @Test
    fun `duda tentativa quizas excluida`() {
        assertNull(analyze("quizá cobrar la pensión mañana"))
    }

    // ─── Regresiones del hermano (HIT inalterado) ─────────────────

    @Test
    fun `regresion master c895b nomina y reembolso intactos`() {
        val nomina = analyze("cobrar la nómina mañana")
        assertNotNull(nomina)
        assertEquals(ContextIntentKind.TASK, nomina!!.kind)
        assertEquals("Cobrar la nómina", nomina.title)

        val reembolso = analyze("cobrar el reembolso mañana")
        assertNotNull(reembolso)
        assertEquals(ContextIntentKind.TASK, reembolso!!.kind)
        assertEquals("Cobrar el reembolso", reembolso.title)
    }

    @Test
    fun `regresiones vecinas de clase NOVENA intactas`() {
        val errand = analyze("ingresar el reembolso mañana")
        assertNotNull(errand)
        assertEquals(ContextIntentKind.ERRAND, errand!!.kind)

        val payment = analyze("pagar la tarjeta mañana")
        assertNotNull(payment)
        assertEquals(ContextIntentKind.PAYMENT, payment!!.kind)

        val task = analyze("revisar el extracto del banco mañana")
        assertNotNull(task)
        assertEquals(ContextIntentKind.TASK, task!!.kind)
    }
}
