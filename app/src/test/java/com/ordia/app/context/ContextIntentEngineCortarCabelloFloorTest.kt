package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1013: lateral OBJETO del piso ERRAND «cortar el pelo» c.842 —
 * «cortarme el cabello el sábado» (el sinónimo «cabello», la forma
 * mayoritaria en español latinoamericano para la cita de peluquería).
 * Candidata documentada ABIERTA en la propia fila del piso c.842
 * (acotado deliberado, UNA por ciclo): plural «los pelos» (RESUELTA
 * c.1055) y objeto
 * «cabello» quedaban FUERA; el dativo se resolvió en c.1006 y esta
 * unidad resuelve SOLO el objeto «cabello». NULL PRE medido con sonda
 * efímera `/tmp/probe1007/Probe.kt` sobre HEAD `1794a56` (motor real
 * vía `tools/run_probe.sh`): las 6 formas con «cabello» NULL (olvido
 * silencioso P1 — el ancla-objeto `pelo` del piso no casaba el
 * sinónimo y «cabello» ni siquiera era keyword, así la frase no
 * llegaba al análisis), mientras las regresiones c.842/c.1006
 * intactas HIT y los guards NULL correctos (negación, pasado «me
 * corté…», hedge «quizá…», declarativo «el cabello está largo»).
 * Fix mínimo (lockstep CUATRO puntos, lección c.616/c.751): el
 * ancla-objeto del piso pasa a `(?:pelo|cabello)` + la MISMA
 * extensión en la cláusula de negación dedicada de
 * [imperativeIsNegated] (cinturón y tirantes, precedente c.829/c.842)
 * + la MISMA extensión en la plantilla de título de [extractTitle]
 * (pronombre conservado, doctrina c.653) + keyword-OBJETO «cabello»
 * en `ContextIntent.kt` hermana de «pelo» (sin ella la frase ni
 * llegaba al análisis, lección c.751; monosémica — el cabello es
 * siempre el de la cabeza —; 0.12 sola queda bajo el umbral y con
 * bono temporal 0.22 < 0.45, misma aritmética documentada c.842).
 * Anti-overreach intacto: el ancla-objeto cerrado `(?:pelo|cabello)`
 * blinda la bivalencia de «cortar» (el césped sigue HOUSEHOLD c.731),
 * la negación inmediata la bloquean el lookbehind del piso y la
 * cláusula, el pasado no casa el infinitivo literal y el hedge sigue
 * NULL. Acotado deliberado (UNA forma por ciclo): plural «los pelos»
 * (RESUELTA c.1055)
 * quedaba FUERA — candidata documentada c.842.
 */
class ContextIntentEngineCortarCabelloFloorTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Capturas «cabello» (piso) ----

    @Test
    fun `captura reflexiva me con fecha`() {
        val i = analyze("cortarme el cabello el sábado")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Cortarme el cabello", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura forma desnuda con fecha`() {
        val i = analyze("cortar el cabello mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Cortar el cabello", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura dativa le con destinatario`() {
        val i = analyze("cortarle el cabello al niño el sábado")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Cortarle el cabello al niño", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura reflexiva se`() {
        val i = analyze("cortarse el cabello esta tarde")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Cortarse el cabello", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura tras acuse`() {
        val i = analyze("vale, cortarme el cabello el viernes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Cortarme el cabello", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura sin fecha sigue capturando`() {
        val i = analyze("cortar el cabello")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Cortar el cabello", i.title)
    }

    // ---- Guards (NULL correctos) ----

    @Test
    fun `negacion no captura`() {
        assertNull(analyze("no cortarme el cabello mañana"))
    }

    @Test
    fun `pasado no captura`() {
        assertNull(analyze("me corté el cabello ayer"))
    }

    @Test
    fun `hedge no captura`() {
        assertNull(analyze("quizá cortarme el cabello el sábado"))
    }

    @Test
    fun `declarativo keyword sola no captura`() {
        assertNull(analyze("el cabello está largo"))
    }

    // ---- Regresiones (intactas) ----

    @Test
    fun `regresion pelo reflexiva intacta`() {
        val i = analyze("cortarme el pelo el sábado")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Cortarme el pelo", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `regresion pelo dativa intacta`() {
        val i = analyze("cortarle el pelo al niño el sábado")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Cortarle el pelo al niño", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `regresion cesped household intacta`() {
        val i = analyze("cortar el césped mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Cortar el césped", i.title)
        assertNotNull(i.dueAt)
    }
}
