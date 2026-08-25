package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1143 — candidata (c) de la clase DECIMOQUINTA (burocracia/administración,
 * sonda persistida `tools/probe/FifteenthClassAdminProbe.kt` c.1132 del
 * hermano, C1): «sellar el paro el día 4». NULL PRE medido con sonda
 * efímera (motor real vía `tools/run_probe.sh`, base `8eba7fe`): las
 * formas DESNUDAS «sellar el paro [temporal]» NULL (C1/C2), las
 * envolventes «tengo que…»/«recuérdame…» ya capturan por camino genérico,
 * 5/5 guards NULL, regresiones HIT. Olvido silencioso P1: sellar el paro
 * es una obligación periódica — olvidarla cuesta la prestación por
 * desempleo (el olvido más caro de la clase DECIMOQUINTA).
 *
 * Decisión de dominio: TASK (obligación administrativa periódica SIN
 * desplazamiento explícito; hermana de «dar de alta/baja <suministro>»
 * TASK c.1139/c.895c — la doctrina ERRAND c.842/c.862 gobierna solo el
 * desplazamiento).
 *
 * Lockstep TRES puntos (lección c.616/c.751, hermano EXACTO de c.1139):
 * (1) keyword-frase «sellar el paro» en TASK (monosemántica: sólo la
 * obligación del SEPE; «sellar» solo NO se añade — bivalente
 * «sellar el pasaporte/la carta»); (2) piso NUEVO «sellar (el)? paro»
 * en `hasStrongTaskImperative` junto al piso «dar de alta» c.1139
 * (ancla ^|acuse|temporal y guard `(?<!no )` heredados de la familia);
 * (3) plantilla hermana matchSellarParo en [extractTitle] (doctrina
 * c.653: verbo-frase preservado).
 *
 * Acotado deliberado: «sellar el pasaporte» (bivalente fronterizo) NULL,
 * «sellar la carta» NULL — el objeto «paro» es EXIGIDO por el piso.
 * Lateral ABIERTA (UNA por ciclo): «sellar el paro por internet»,
 * candidata (d) «empadronarme»/«hacer la mudanza».
 */
class ContextIntentEngineSellarParoFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ─── Capturas «sellar el paro» ────────────────────────────────

    @Test
    fun `sellar el paro con dia de mes captura TASK`() {
        val intent = analyze("sellar el paro el día 4")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `sellar el paro manana captura TASK con titulo limpio`() {
        val intent = analyze("sellar el paro mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Sellar el paro", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `sellar el paro con dia de semana captura TASK`() {
        val intent = analyze("sellar el paro el lunes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Sellar el paro", intent.title)
    }

    @Test
    fun `sellar el paro sin fecha captura TASK sin dueAt`() {
        val intent = analyze("sellar el paro")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Sellar el paro", intent.title)
        assertEquals(null, intent.dueAt)
    }

    @Test
    fun `acuse vale sellar el paro captura TASK`() {
        val intent = analyze("vale, sellar el paro mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Sellar el paro", intent.title)
    }

    // ─── Guards (NULL deliberado) ─────────────────────────────────

    @Test
    fun `negada no selles el paro no captura`() {
        assertNull(analyze("no selles el paro todavía"))
    }

    @Test
    fun `duda no se si sellar el paro no captura`() {
        assertNull(analyze("no sé si sellar el paro mañana o pasado"))
    }

    @Test
    fun `duda quizas sellar el paro no captura`() {
        assertNull(analyze("quizá sellar el paro mañana"))
    }

    @Test
    fun `pasado selle el paro no captura`() {
        assertNull(analyze("sellé el paro ayer"))
    }

    @Test
    fun `sustantivo el sello del paro no captura`() {
        assertNull(analyze("el sello del paro me llega por correo"))
    }

    @Test
    fun `bivalente sellar el pasaporte no captura`() {
        assertNull(analyze("sellar el pasaporte en la frontera"))
    }

    @Test
    fun `otro objeto sellar la carta no captura`() {
        assertNull(analyze("sellar la carta mañana"))
    }

    // ─── Regresiones (byte-idénticas) ─────────────────────────────

    @Test
    fun `dar de alta la luz sigue TASK c1139`() {
        val intent = analyze("dar de alta la luz del piso nuevo mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Dar de alta la luz del piso nuevo", intent.title)
    }

    @Test
    fun `presentar el recurso sigue TASK c1134`() {
        val intent = analyze("presentar el recurso de la multa esta semana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `pagar la luz sigue PAYMENT`() {
        val intent = analyze("pagar la luz mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
    }

    @Test
    fun `envolvente tengo que sellar el paro sigue TASK`() {
        val intent = analyze("tengo que sellar el paro el día 4")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `envolvente recuerdame sellar el paro sigue TASK`() {
        val intent = analyze("recuérdame sellar el paro el lunes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Sellar el paro", intent.title)
    }
}
