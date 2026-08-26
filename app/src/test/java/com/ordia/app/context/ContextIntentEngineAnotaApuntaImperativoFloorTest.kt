package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * TDD RED→GREEN c.1260 (descubrimiento documentado c.1255, marcador propio):
 * imperativos conjugados de captura «anota|apunta <objeto>». El piso
 * [NOTE_FLOOR] (c.714/c.856) solo cubre el infinitivo «apuntar|anotar» y el
 * reflexivo «apuntarse a»; la forma conjugada se DESCARTABA en silencio
 * (sonda PRE `tools/probe/AnotaApuntaImperativoProbe.kt`: D1–D8 NULL —
 * «anota» recibe score por la keyword «nota» subcadena pero sin piso queda
 * < MINIMUM_CONFIDENCE; «apunta» ni keyword tiene). Olvido silencioso P1 de
 * la orden de captura más natural en dictado («apunta este número»).
 * Gate c.751 POSITIVO: orden de anotación monosemántica con objeto, CERO
 * keywords nuevas (floor-only, paridad c.1231/c.1256). Ancla SOLO ^|ACUSE:
 * el prefijo temporal queda FUERA porque «el lunes anota todo» es 3ª-persona
 * habitual ambigua, no imperativo (decisión del marcador c.1260).
 */
class ContextIntentEngineAnotaApuntaImperativoFloorTest {

    private fun analyze(text: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
        )

    @Test
    fun `anota con objeto captura nota`() {
        val r1 = analyze("anota la dirección del médico")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.NOTE, r1!!.kind)
        assertEquals("Anota la dirección del médico", r1.title)

        val r2 = analyze("anota el número del banco")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.NOTE, r2!!.kind)
        assertEquals("Anota el número del banco", r2.title)
    }

    @Test
    fun `apunta con objeto captura nota`() {
        val r1 = analyze("apunta la matrícula del coche")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.NOTE, r1!!.kind)
        assertEquals("Apunta la matrícula del coche", r1.title)

        val r2 = analyze("apunta el código de la puerta")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.NOTE, r2!!.kind)
        assertEquals("Apunta el código de la puerta", r2.title)
    }

    @Test
    fun `acuse activa el piso conjugado`() {
        val r1 = analyze("vale, anota la dirección")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.NOTE, r1!!.kind)
        assertEquals("Anota la dirección", r1.title)

        val r2 = analyze("ok apunta el horario")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.NOTE, r2!!.kind)
        assertEquals("Apunta el horario", r2.title)
    }

    @Test
    fun `temporal sufijo preserva dueAt y despoja el titulo`() {
        val r = analyze("anota el número del banco mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.NOTE, r!!.kind)
        assertEquals("Anota el número del banco", r.title)
        assertNotNull(r.dueAt)
    }

    @Test
    fun `guards correctos NULL`() {
        assertNull(analyze("no anotes la dirección"))
        assertNull(analyze("anotó la dirección ayer"))
        assertNull(analyze("ya anoté todo en el cuaderno"))
        assertNull(analyze("no apunta el número"))
        assertNull(analyze("ella apunta todo en su cuaderno"))
        assertNull(analyze("el lunes anota todo lo del trabajo"))
        assertNull(analyze("apunta"))
        assertNull(analyze("anota"))
    }

    @Test
    fun `regresiones heredadas intactas`() {
        val r1 = analyze("apuntar la dirección del médico")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.NOTE, r1!!.kind)
        assertEquals("Apuntar la dirección del médico", r1.title)

        val r2 = analyze("apuntarse a la lista")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.NOTE, r2!!.kind)

        val r3 = analyze("recuérdame apuntar la dirección mañana")
        assertNotNull(r3)
        assertEquals(ContextIntentKind.TASK, r3!!.kind)

        val r4 = analyze("comprar leche")
        assertNotNull(r4)
        assertEquals(ContextIntentKind.SHOPPING, r4!!.kind)
    }

    @Test
    fun `contenido ya preservado por otra via no cambia`() {
        // PRE medido: captura APPOINTMENT vía «cita del dentista» heredada;
        // el piso conjugado no debe robar el kind (contenido ya a salvo).
        val r = analyze("anota la cita del dentista mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.APPOINTMENT, r!!.kind)
        assertNotNull(r.dueAt)
    }
}
