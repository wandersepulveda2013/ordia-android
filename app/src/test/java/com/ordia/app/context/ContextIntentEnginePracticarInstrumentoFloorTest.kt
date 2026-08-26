package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD RED→GREEN c.1249 (lateral (a) FUERTE de MI auditoría c.1248, clase
 * XXXV música/instrumentos — sonda persistida `MusicaClassXXXVProbe.kt`):
 * «practicar (el|la|mi) <instrumento-musical>». CERO keywords nuevas —
 * floor-only: «practicar <instrumento>» casi monosemántico sobre objeto
 * EXIGIDO acotado (gate c.751, precedentes c.1241/c.1247 lockstep piso +
 * plantilla, lección c.616).
 */
class ContextIntentEnginePracticarInstrumentoFloorTest {

    private fun analyze(text: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
        )

    @Test
    fun `practicar piano y violin captura task`() {
        val r1 = analyze("practicar el piano")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.TASK, r1!!.kind)
        assertEquals("Practicar el piano", r1.title)

        val r2 = analyze("practicar el violín")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.TASK, r2!!.kind)
        assertEquals("Practicar el violín", r2.title)
    }

    @Test
    fun `practicar plural y posesivo captura task`() {
        val r1 = analyze("practicar mi guitarra")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.TASK, r1!!.kind)

        val r2 = analyze("practicar los violines")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.TASK, r2!!.kind)
    }

    @Test
    fun `guard negacion no captura`() {
        assertNull(analyze("no practicar el piano"))
    }

    @Test
    fun `guard preterito no captura`() {
        assertNull(analyze("practiqué el piano ayer"))
    }

    @Test
    fun `guard objeto no instrumental no captura`() {
        assertNull(analyze("practicar el idioma"))
        assertNull(analyze("practicar deportes"))
    }

    @Test
    fun `guard nominal no captura`() {
        assertNull(analyze("la práctica de piano"))
        assertNull(analyze("el piano"))
    }

    @Test
    fun `regresiones brico y tecnologia conservan hit`() {
        val r1 = analyze("taladrar la pared")
        assertNotNull(r1)
        assertTrue(r1!!.title.startsWith("Taladrar"))

        val r2 = analyze("apagar el ordenador")
        assertNotNull(r2)
        assertTrue(r2!!.title.startsWith("Apagar"))
    }
}
