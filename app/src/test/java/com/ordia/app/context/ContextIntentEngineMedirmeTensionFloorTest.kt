package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.840: piso TASK «medir la tensión» (c.772) admite el enclítico
 * reflexivo «medirme la tensión» — la forma real más cotidiana del
 * autocontrol de la tensión arterial (candidata 1/4 de la sonda
 * persistida c.834 `tools/probe/SixthClassEncliticProbe.kt`; NULL PRE
 * verificado por sonda efímera `/tmp/probe839/` sobre HEAD e744807:
 * las 6 capturas directas daban NULL; la envolvente «recuérdame…» ya
 * era TASK 0.54 vía candado de envolvente — asimetría de ruta hermana
 * de c.765…c.837).
 * Hermana simétrica del piso c.775 «medirme la presión»: cierra la
 * diagonal reflexivo×objeto tensión. Acotado deliberado: la NO
 * reflexiva con objeto presión («medir la presión») sigue FUERA —
 * una forma por ciclo, doctrina de la sonda (queda como candidata).
 * El título CONSERVA el pronombre («Medirme la tensión», precedente
 * c.770 «Tomarme la pastilla», doctrina c.653).
 */
class ContextIntentEngineMedirmeTensionFloorTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Capturas directas (piso) ----

    @Test
    fun `captura forma base manana`() {
        val i = analyze("medirme la tensión mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Medirme la tensión", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura esta noche`() {
        val i = analyze("medirme la tensión esta noche")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Medirme la tensión", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura con acuse vale`() {
        val i = analyze("vale, medirme la tensión esta tarde")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Medirme la tensión", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val i = analyze("hoy medirme la tensión")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Medirme la tensión", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura sin fecha`() {
        val i = analyze("medirme la tensión")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Medirme la tensión", i.title)
    }

    @Test
    fun `captura sin tilde tension`() {
        val i = analyze("medirme la tension mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Medirme la tension", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `envolvente recuerdame`() {
        val i = analyze("recuérdame medirme la tensión mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Medirme la tensión", i.title)
        assertNotNull(i.dueAt)
    }

    // ---- Regresiones (no debe romperse) ----

    @Test
    fun `regresion medir la tension c772`() {
        val i = analyze("medir la tensión mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Medir la tensión", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `regresion medirme la presion c775`() {
        val i = analyze("medirme la presión mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Medirme la presión", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `regresion tomarme la pastilla c770`() {
        val i = analyze("tomarme la pastilla esta noche")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertNotNull(i.dueAt)
    }

    // ---- Descartes (anti-overreach) ----

    @Test
    fun `descartada negada`() {
        assertNull(analyze("no medirme la tensión mañana"))
    }

    @Test
    fun `descartada duda quiza`() {
        assertNull(analyze("quizá medirme la tensión mañana"))
    }

    @Test
    fun `descartada pasado me medi`() {
        assertNull(analyze("me medí la tensión ayer"))
    }

    @Test
    fun `descartada objeto bivalente peso`() {
        assertNull(analyze("medirme el peso mañana"))
    }

    @Test
    fun `descartada verbo suelto`() {
        assertNull(analyze("medirme"))
    }

    @Test
    fun `descartada sustantivo suelto`() {
        assertNull(analyze("la tensión está alta"))
    }

    @Test
    fun `regresion c843 no reflexiva con presion`() {
        // c.843: «medir la presión» (sin reflexivo, objeto presión — la
        // diagonal descartada deliberadamente en c.840) pasa a CAPTURAR.
        val i = analyze("medir la presión mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Medir la presión", i.title)
    }

    // ---- Guards aditivos c.841 (no cubiertos por el hermano c.840) ----

    @Test
    fun `descartada plural las tensiones`() {
        // «las tensiones» (fricciones interpersonales) NO es la medición
        // de salud: el piso no admite plural (decisión c.772, intacta).
        assertNull(analyze("medirme las tensiones mañana"))
    }

    @Test
    fun `descartada perifrasis me mido`() {
        // Perífrasis conjugada «me mido»: fuera del alcance del piso
        // (infinitivo con enclítico) — candidata potencial futura, una
        // forma por ciclo (doctrina de la sonda).
        assertNull(analyze("me mido la tensión mañana"))
    }
}
