package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.758: forma "pintar la casa" (CUARTA clase, hogar — sonda
 * `FourthClassVerbDiscoveryProbe.kt`, PRE NULL) — piso
 * `HOUSEHOLD_PAINT_HOUSE_FLOOR` acotado al objeto `casas?`. "pintar"
 * suelto es bivalente (un cuadro/la veranda), así se acota al objeto
 * (familia de [HOUSEHOLD_TRASH_FLOOR] c.717 / BED c.728 / GARDEN c.748).
 * Keyword de lockstep: "casa" ya existida — cero keywords nuevas (el
 * VERBO "pintar" NO se añade a HOUSEHOLD_VERBS por bivalente).
 * Anti-overreach: negada/duda/pasado/objeto no casa/diminutivo → NULL.
 */
class ContextIntentEnginePintarLaCasaFloorTest {

    private fun analyze(raw: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1000)
    )

    @Test
    fun `captura pintar la casa mañana`() {
        val intent = analyze("pintar la casa mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Pintar la casa", intent.title)
    }

    @Test
    fun `captura plural las casas`() {
        val intent = analyze("pintar las casas mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Pintar las casas", intent.title)
    }

    @Test
    fun `negada no pintar la casa descartada`() {
        val intent = analyze("no pintar la casa mañana")
        assertNull(intent)
    }

    @Test
    fun `pasado pinte la casa descartado`() {
        val intent = analyze("pinté la casa ayer")
        assertNull(intent)
    }

    @Test
    fun `duda quizá pintar la casa descartada`() {
        val intent = analyze("quizá pintar la casa mañana")
        assertNull(intent)
    }

    @Test
    fun `objeto no casa un cuadro descartado`() {
        val intent = analyze("pintar un cuadro mañana")
        assertNull(intent)
    }

    @Test
    fun `diminutivo casita descartado`() {
        val intent = analyze("pintar la casita este fin de semana")
        assertNull(intent)
    }
}
