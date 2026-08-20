package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Antes: solo "alimentar al gato" (c.744) era capturable; la variante
 * conversacional "dar de comer al gato hoy" filtraba como ruido de expresiones
 * anunciadas, inflando los incumplimientos (BACKLOG c.753).
 * Ahora: el motor crea HOUSEHOLD "Dar de comer al gato" (+ dueAt) sólo cuando
 * la construcción "dar de comer" va acotada al objeto mascota; los falsos
 * positivos quedan descartados.
 */
class ContextIntentEngineDarDeComerGatoFloorTest {

    private fun assertCapturesHouseholdGato(text: String, expected: String) {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1000)
        )
        assertNotNull("texto: $text", intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals(expected, intent.title)
    }

    @Test
    fun `dar de comer al gato con prefijo temporal acompaña`() {
        assertCapturesHouseholdGato("dar de comer al gato hoy", "Dar de comer al gato")
    }

    @Test
    fun `dar de comer al gato con acuse de proximidad`() {
        assertCapturesHouseholdGato("vale, dar de comer al gato mañana", "Dar de comer al gato")
    }

    @Test
    fun `dar de comer al gato con prefio temporal extenso`() {
        assertCapturesHouseholdGato("hoy dar de comer al gato", "Dar de comer al gato")
    }

    @Test
    fun `dar de comer a la gata acota a mascota`() {
        assertCapturesHouseholdGato("dar de comer a la gata", "Dar de comer a la gata")
    }

    @Test
    fun `negada no genera intención`() {
        assertDiscards("no dar de comer al gato")
    }

    @Test
    fun `pasado imperfecto no genera intención`() {
        assertDiscards("di de comer al gato ayer")
    }

    @Test
    fun `dar de comer suelto no genera intención`() {
        assertDiscards("dar de comer")
    }

    @Test
    fun `objeto no mascota no genera intención`() {
        assertDiscards("dar de comer al bebé")
    }

    @Test
    fun `sustantivo comida no genera intención`() {
        assertDiscards("la comida del gato")
    }

    private fun assertDiscards(text: String) {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1000)
        )
        assertNull("el texto no debe generar intención: $text", intent)
    }
}
