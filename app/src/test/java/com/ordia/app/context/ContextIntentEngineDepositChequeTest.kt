package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * c.895 — laterales que el hermano c.894 (commit `cec1e29`, máster
 * «ingresar dinero/reembolso») dejó medidas como pendientes en el piso
 * `[ERRAND_DEPOSIT_FLOOR]`: «depositar el cheque» (verbo hermano) y
 * «hacer el ingreso» (forma sustantiva). Familia (2/8) de la clase NOVENA
 * (sonda c.892). NULL PRE medido por la sonda persistida
 * `tools/probe/DepositChequeProbe.kt` (3/3 NULL; 5/5 guards NULL; 2/2
 * laterales NULL; 6/6 regresiones HIT), base `cec1e29`, motor real vía
 * `tools/run_probe.sh`.
 *
 * Decisión de dominio heredada (c.894): ERRAND (desplazamiento al banco/
 * ventanilla, doctrina c.842/c.862, hermano de «sacar dinero» c.893).
 *
 * Extensión del lockstep del hermano en los mismos TRES puntos (lección
 * c.616/c.751): (1) piso — añade el verbo «depositar»+ancla-objeto
 * `cheque|reembolso|ingreso` y la forma sustantiva «hacer el ingreso»
 * con guard `(?<!no )` (familia c.640/c.643; «hacer dinero» excluido por
 * bivalente); (2) keywords-OBJETO «cheque»/«ingreso» (NO el verbo
 * «depositar», bivalente — la basura/la confianza; lección c.829/c.893;
 * alimentan TRIGGER_WORDS c.751); (3) plantilla de título en
 * [extractTitle] (verbos «depositar»/«hacer» preservados, doctrina
 * c.653). Cinturón y tirantes: cláusula de negación dedicada en
 * [imperativeIsNegated] (precedente c.894).
 *
 * Acotado deliberado: laterales bivalentes sin ancla «ir a ingresar»/
 * «pasar a depositar» (hospital/club/universidad) permanecen NULL.
 */
class ContextIntentEngineDepositChequeTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ─── Capturas (laterales del hermano) ─────────────────────────

    @Test
    fun `depositar el cheque captura ERRAND con título limpio`() {
        val intent = analyze("depositar el cheque mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Depositar el cheque", intent.title)
    }

    @Test
    fun `depositar el reembolso captura ERRAND`() {
        val intent = analyze("depositar el reembolso mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Depositar el reembolso", intent.title)
    }

    @Test
    fun `hacer el ingreso captura ERRAND forma sustantiva`() {
        val intent = analyze("hacer el ingreso mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Hacer el ingreso", intent.title)
    }

    @Test
    fun `acuse a prueba de fallos no rompe la captura`() {
        val intent = analyze("vale, depositar el cheque")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Depositar el cheque", intent.title)
    }

    @Test
    fun `confianza supera el umbral mínimo`() {
        val intent = analyze("hacer el ingreso mañana")
        assertNotNull(intent)
        assertTrue(intent!!.confidence >= 0.45f)
    }

    // ─── Negaciones (cinturón y tirantes) ─────────────────────────

    @Test
    fun `no depositar el cheque queda descartado`() {
        assertNull(analyze("no depositar el cheque mañana"))
    }

    @Test
    fun `no hacer el ingreso queda descartado`() {
        assertNull(analyze("no hacer el ingreso mañana"))
    }

    @Test
    fun `no ingresar el reembolso sigue descartado`() {
        assertNull(analyze("no ingresar el reembolso mañana"))
    }

    // ─── Guards anti-overreach ────────────────────────────────────

    @Test
    fun `objeto distinto no captura`() {
        assertNull(analyze("depositar la basura mañana"))
        assertNull(analyze("ingresar la contraseña mañana"))
    }

    @Test
    fun `hacer con otro objeto no captura`() {
        assertNull(analyze("hacer la limpieza mañana"))
    }

    @Test
    fun `duda queda descartada`() {
        assertNull(analyze("quizá depositar el cheque mañana"))
    }

    @Test
    fun `laterales bivalentes sin ancla quedan NULL deliberadamente`() {
        // «ir a ingresar» / «pasar a depositar»: bivalentes (hospital,
        // club, universidad, matrícula). Acotado deliberado c.895.
        assertNull(analyze("ir a ingresar mañana"))
        assertNull(analyze("pasar a depositar esta tarde"))
    }

    @Test
    fun `declarativos sin imperativo quedan descartados`() {
        assertNull(analyze("el cheque está en la mesa"))
        assertNull(analyze("deposité el cheque ayer"))
    }

    // ─── Regresiones (máster del hermano ya captura) ──────────────

    @Test
    fun `ingresar dinero sigue capturando ERRAND`() {
        val intent = analyze("ingresar dinero en el banco mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Ingresar dinero en el banco", intent.title)
    }

    @Test
    fun `ingresar el reembolso sigue capturando ERRAND`() {
        val intent = analyze("ingresar el reembolso mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Ingresar el reembolso", intent.title)
    }

    @Test
    fun `sacar dinero sigue capturando ERRAND`() {
        val intent = analyze("sacar dinero mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
    }

    @Test
    fun `pager la tarjeta sigue capturando PAYMENT y revisar el extracto TASK`() {
        val payment = analyze("pagar la tarjeta mañana")
        assertNotNull(payment)
        assertEquals(ContextIntentKind.PAYMENT, payment!!.kind)
        val task = analyze("revisar el extracto del banco mañana")
        assertNotNull(task)
        assertEquals(ContextIntentKind.TASK, task!!.kind)
    }
}
