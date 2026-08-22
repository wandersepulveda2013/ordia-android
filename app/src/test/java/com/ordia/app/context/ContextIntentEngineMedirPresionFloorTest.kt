package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.843: piso TASK «medirme la presión» (c.775) admite la forma DESNUDA
 * «medir la presión» — la diagonal no reflexiva del autocontrol de la
 * tensión arterial (candidata documentada desde c.840; NULL PRE
 * verificado por sonda efímera `/tmp/probe843/MedirPresionPreProbe.kt`
 * sobre HEAD 4fa5bf0: las 6 capturas directas daban NULL; la envolvente
 * «recuérdame…» ya era TASK 0.54 vía candado de envolvente — asimetría
 * de ruta hermana de c.765…c.842).
 * Cierra la diagonal reflexivo×objeto presión (c.772 «medir la tensión»,
 * c.775 «medirme la presión», c.840 «medirme la tensión», c.843 «medir
 * la presión»). El título conserva la grafía del usuario (doctrina
 * c.653): «la presion» sin tilde queda tal cual.
 * Decisión de alcance (documentada, evaluada con la sonda): «medir la
 * presión de los neumáticos» CAPTURA deliberadamente — es una tarea real
 * de mantenimiento del vehículo (hermana de «echar gasolina» c.829), no
 * overreach; la semántica social usa el plural «presiones», que sigue
 * NULL. La perífrasis conjugada «me mido la presión» queda FUERA (una
 * forma por ciclo — contrato simétrico del guard c.841 «me mido la
 * tensión»).
 */
class ContextIntentEngineMedirPresionFloorTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Capturas directas (piso) ----

    @Test
    fun `captura forma base manana`() {
        val i = analyze("medir la presión mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Medir la presión", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura sin tilde presion`() {
        val i = analyze("medir la presion mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Medir la presion", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura esta noche`() {
        val i = analyze("medir la presión esta noche")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Medir la presión", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura con acuse vale`() {
        val i = analyze("vale, medir la presión el lunes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Medir la presión", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura con posesivo mi`() {
        val i = analyze("medir mi presión hoy")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Medir mi presión", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura la semana que viene`() {
        val i = analyze("medir la presión la semana que viene")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Medir la presión", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura sin fecha`() {
        val i = analyze("medir la presión")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Medir la presión", i.title)
    }

    // ---- Guards aditivos c.844 (no cubiertos por el hermano c.843) ----

    @Test
    fun `captura con prefijo temporal hoy`() {
        // Contrato del ancla temporal DELANTE del verbo (alternativa
        // `\b(?:$TASK_FLOOR_TEMPORAL)\s+` del piso): el hermano c.843
        // cubre base/sin tilde/esta noche/acuse/posesivo/semana/sin fecha
        // pero no el prefijo temporal. Aditivo (precedente c.841).
        val i = analyze("hoy medir la presión")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Medir la presión", i.title)
        assertNotNull(i.dueAt)
    }

    // ---- Envolvente (el candado sigue gobernando, regresión de ruta) ----

    @Test
    fun `envolvente recuerdame sigue gobernando`() {
        val i = analyze("recuérdame medir la presión mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertNotNull(i.dueAt)
    }

    // ---- Decisión de alcance: neumáticos = mantenimiento real ----

    @Test
    fun `neumaticos captura como mantenimiento del vehiculo`() {
        val i = analyze("medir la presión de los neumáticos mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Medir la presión de los neumáticos", i.title)
        assertNotNull(i.dueAt)
    }

    // ---- Guards anti-overreach (deben permanecer NULL) ----

    @Test
    fun `negada no captura`() {
        assertNull(analyze("no medir la presión mañana"))
    }

    @Test
    fun `duda quizas no captura`() {
        assertNull(analyze("quizá medir la presión mañana"))
    }

    @Test
    fun `pasado no captura`() {
        assertNull(analyze("medí la presión ayer"))
    }

    @Test
    fun `declarativo no captura`() {
        assertNull(analyze("la presión está alta"))
    }

    @Test
    fun `plural social no captura`() {
        assertNull(analyze("medir las presiones del equipo"))
    }

    @Test
    fun `perifrasis conjugada c883 ahora captura`() {
        // c.883: el guard de contrato pasa a regresión de captura —
        // la perífrasis conjugada «me mido la presión» era NULL
        // deliberado (c.843); resuelta como candidata propia en c.883
        // (piso acotado «me mido…» con objeto ancla presión|tensión).
        val i = analyze("me mido la presión mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Me mido la presión", i.title)
    }

    @Test
    fun `verbo bivalente sin objeto ancla no captura`() {
        assertNull(analyze("medir la mesa mañana"))
    }

    // ---- Regresiones de la familia (c.772/c.775/c.840 intactas) ----

    @Test
    fun `regresion c775 medirme la presion`() {
        val i = analyze("medirme la presión mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Medirme la presión", i.title)
    }

    @Test
    fun `regresion c772 medir la tension`() {
        val i = analyze("medir la tensión mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Medir la tensión", i.title)
    }

    @Test
    fun `regresion c840 medirme la tension`() {
        val i = analyze("medirme la tensión mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Medirme la tensión", i.title)
    }
}
