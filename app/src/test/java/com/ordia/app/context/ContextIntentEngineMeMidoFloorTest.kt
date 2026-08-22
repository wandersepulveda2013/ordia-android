package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.883: perífrasis conjugada de 1ª persona «me mido…» para el autocontrol
 * de la tensión arterial — candidata documentada desde c.841/c.843 como
 * guard de contrato simétrico (UNA por ciclo, doctrina de la propia sonda),
 * movida ahora de NULL a captura. Cierra la diagonal conjugada reflexiva
 * (c.772 «medir la tensión», c.775 «medirme la presión», c.840 «medirme
 * la tensión», c.843 «medir la presión», c.883 «me mido la presión»/
 * «me mido la tensión»). El título conserva la grafía del usuario
 * (doctrina c.653): «la presion»/«la tension» sin tilde quedan tal cual.
 * Decisión de alcance (documentada, evaluada con sonda efímera
 * `/tmp/probe883/LateralProbe.kt` sobre HEAD 4dbd73e — NULL PRE
 * verificado): «me mido la presión de los neumáticos» CAPTURA
 * deliberadamente — tarea real de mantenimiento del vehículo (hermana de
 * «echar gasolina» c.829 y de la decisión c.843), no overreach; la
 * semántica social usa el plural «presiones»/«tensiones», que sigue NULL,
 * igual que la 2ª persona «midete…» y el objeto bivalente «la mesa».
 */
class ContextIntentEngineMeMidoFloorTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Capturas directas (piso) ----

    @Test
    fun `captura forma base presion manana`() {
        val i = analyze("me mido la presión mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Me mido la presión", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura forma base tension manana`() {
        val i = analyze("me mido la tensión mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Me mido la tensión", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura esta noche`() {
        val i = analyze("me mido la presión esta noche")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Me mido la presión", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura sin tilde presion`() {
        val i = analyze("me mido la presion mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Me mido la presion", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura sin tilde tension`() {
        val i = analyze("me mido la tension mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Me mido la tension", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura con acuse vale`() {
        val i = analyze("vale, me mido la tensión el lunes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Me mido la tensión", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura con prefijo temporal hoy`() {
        val i = analyze("hoy me mido la presión")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Me mido la presión", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura con posesivo mi`() {
        val i = analyze("me mido mi presión hoy")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Me mido mi presión", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura la semana que viene`() {
        val i = analyze("me mido la presión la semana que viene")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Me mido la presión", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura sin fecha presion`() {
        val i = analyze("me mido la presión")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Me mido la presión", i.title)
    }

    @Test
    fun `captura sin fecha tension`() {
        val i = analyze("me mido la tensión")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Me mido la tensión", i.title)
    }

    // ---- Envolvente (el candado sigue gobernando, regresión de ruta) ----

    @Test
    fun `envolvente recuerdame sigue gobernando`() {
        val i = analyze("recuérdame medirme la presión mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertNotNull(i.dueAt)
    }

    // ---- Decisión de alcance: neumáticos = mantenimiento real ----

    @Test
    fun `neumaticos captura como mantenimiento del vehiculo`() {
        val i = analyze("me mido la presión de los neumáticos mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Me mido la presión de los neumáticos", i.title)
        assertNotNull(i.dueAt)
    }

    // ---- Guards anti-overreach (deben permanecer NULL) ----

    @Test
    fun `negada no captura`() {
        assertNull(analyze("no me mido la presión mañana"))
    }

    @Test
    fun `duda quizas no captura`() {
        assertNull(analyze("quizá me mido la presión mañana"))
    }

    @Test
    fun `pasado no captura`() {
        assertNull(analyze("me medí la presión ayer"))
    }

    @Test
    fun `declarativo no captura`() {
        assertNull(analyze("la presión está alta"))
    }

    @Test
    fun `plural social presiones no captura`() {
        assertNull(analyze("me mido las presiones del equipo"))
    }

    @Test
    fun `plural social tensiones no captura`() {
        assertNull(analyze("me mido las tensiones del equipo"))
    }

    @Test
    fun `segunda persona midete no captura`() {
        assertNull(analyze("midete la presión mañana"))
    }

    @Test
    fun `verbo bivalente sin objeto ancla no captura`() {
        assertNull(analyze("me mido la mesa mañana"))
    }

    @Test
    fun `perdida desnuda me mido no captura`() {
        // «me mido» sin objeto ancla (la estatura/el alimento): el ancla
        // de objeto es el guard de bivalencia (lección c.772/c.775).
        assertNull(analyze("me mido mañana"))
    }

    // ---- Regresiones de la familia (c.772/c.775/c.840/c.843 intactas) ----

    @Test
    fun `regresion c772 desnuda tension`() {
        val i = analyze("medir la tensión mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
    }

    @Test
    fun `regresion c775 enclitica presion`() {
        val i = analyze("medirme la presión mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
    }

    @Test
    fun `regresion c840 enclitica tension`() {
        val i = analyze("medirme la tensión mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
    }

    @Test
    fun `regresion c843 desnuda presion`() {
        val i = analyze("medir la presión mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
    }
}
