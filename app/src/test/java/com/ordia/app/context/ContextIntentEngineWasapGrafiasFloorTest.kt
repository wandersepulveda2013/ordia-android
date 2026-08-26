package com.ordia.app.context

/**
 * Ciclo c.1197: piso responder/contestar captura las grafías coloquiales
 * «wasap/wassap(s)» (hermana ABIERTA documentada de c.1192; fix
 * DISJUNTO del marcador activo del hermano c.1194 «después de comer»
 * — sanitización temporal en parser). «responder el wasap/cwassap
 * <…> mañana» caía a NULL porque la keyword-OBJETO era sólo
 * «whatsapp» y el objeto del piso/plantillas cerraba con
 * `whatsapps?` (PRE sonda efímera /tmp/WasapGrafiasProbe.kt:
 * 4/4 capturas NULL; guards NULL; regresiones 3/3 HIT).
 * Lockstep 3 puntos: keyword «wasap»/«wassap» + pisos responder
 * (c.1192) y contestar (c.1177) + plantillas [matchResponderCorreo]
 * y [matchContestarA]. Gate c.751 satisfecho: «wasap» solo 0.12
 * inerte.
 */
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ContextIntentEngineWasapGrafiasFloorTest {
    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1700000000000L)
    )
    private fun assertCapturesTask(title: String) {
        val i = analyze(title)
        assertNotNull(title, i)
        assertEquals(title, ContextIntentKind.TASK, i!!.kind)
        assertTrue(title, i.confidence >= 0.45f)
    }
    private fun assertNullish(text: String) = assertNull("$text", analyze(text))
    private fun assertDueAt(text: String) {
        val i = analyze(text)!!
        assertTrue("$text debe tener dueAt", i.dueAt != null)
    }

    @Test
    fun `c1197 - responder el wasap de alguien captura (grafía coloquial)`() {
        assertCapturesTask("responder el wasap de Marta")
    }

    @Test
    fun `c1197 - contestar el wasap captura (objeto en lista cerrada)`() {
        assertCapturesTask("contestar el wasap de Ana")
    }

    @Test
    fun `c1197 - responder los wassap captura (grafía con doble s)`() {
        assertCapturesTask("responder los wassap del grupo")
    }

    @Test
    fun `c1197 - contestar los wassaps captura (plural)`() {
        assertCapturesTask("contestar los wassaps del trabajo")
    }

    @Test
    fun `c1197 - título limpio con temporal depurado`() {
        val i = analyze("responder el wasap de Marta mañana")!!
        assertEquals("Responder el wasap de Marta", i.title)
        assertDueAt("responder el wasap de Marta mañana")
    }

    @Test
    fun `c1197 - keyword sola inerte (gate c751)`() {
        assertNullish("me llegó un wasap")
        assertNullish("el wassap sonó dos veces")
    }

    @Test
    fun `c1197 - negación de plan guard NULL (c1009)`() {
        assertNullish("no voy a responder el wasap")
    }

    @Test
    fun `c1197 - pretérito simple guard NULL`() {
        assertNullish("respondí el wasap ayer")
    }
}
