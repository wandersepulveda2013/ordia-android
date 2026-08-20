package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.775: piso TASK acotado a la pareja reflexivo enclítico `medirme` +
 * objeto `presi[oó]n` (autocontrol de la tensión arterial, forma real más
 * cotidiana; hermana del piso c.772 "medir la tensión" y de la alternancia
 * enclítica c.770 "tomarme la medicina").
 * NULL PRE verificado por la sonda sobre HEAD c5031be (las 5 capturas
 * directas daban NULL; la envolvente "recuérdame…" ya era TASK vía candado
 * de envolvente).
 * Acotado deliberado: la NO reflexiva "medir la presión" y la reflexiva
 * con objeto tensión ("medirme la tensión") quedan FUERA — una forma por
 * ciclo, doctrina de la sonda.
 */
class ContextIntentEngineMedirmePresionFloorTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Capturas directas (piso) ----

    @Test
    fun `captura forma base manana`() {
        val i = analyze("medirme la presión mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Medirme la presión", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura con articulo hoy`() {
        val i = analyze("medirme la presión hoy")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Medirme la presión", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura con acuse vale`() {
        val i = analyze("vale, medirme la presión hoy")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Medirme la presión", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val i = analyze("hoy medirme la presión")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Medirme la presión", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura sin tilde presion`() {
        val i = analyze("medirme la presion mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Medirme la presion", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `envolvente recuerdame`() {
        val i = analyze("recuérdame medirme la presión hoy")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Medirme la presión", i.title)
        assertNotNull(i.dueAt)
    }

    // ---- Regresiones (no debe romperse) ----

    @Test
    fun `regresion medir la tension c772`() {
        val i = analyze("medir la tensión mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `regresion hacer copia de seguridad c774`() {
        val i = analyze("hacer copia de seguridad hoy")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertNotNull(i.dueAt)
    }

    // ---- Descartes (anti-overreach) ----

    @Test
    fun `descartada negada`() {
        assertNull(analyze("no medirme la presión mañana"))
    }

    @Test
    fun `descartada duda quiza`() {
        assertNull(analyze("quizá medirme la presión hoy"))
    }

    @Test
    fun `descartada pasado me midi`() {
        assertNull(analyze("me medí la presión ayer"))
    }

    @Test
    fun `descartada objeto bivalente estatura`() {
        assertNull(analyze("medirme la estatura mañana"))
    }

    @Test
    fun `descartada no reflexiva residual`() {
        // Acotado deliberado: "medir la presión" (sin reflexivo) queda OPEN
        // — una forma por ciclo (doctrina de la sonda).
        assertNull(analyze("medir la presión mañana"))
    }

    @Test
    fun `descartada sustantivo suelto`() {
        assertNull(analyze("la presión está alta"))
    }
}
