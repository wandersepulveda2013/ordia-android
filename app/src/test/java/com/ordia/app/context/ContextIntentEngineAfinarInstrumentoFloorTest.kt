package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD RED→GREEN c.1251 (lateral (b) FUERTE de MI auditoría c.1248, clase
 * XXXV música/instrumentos): «afinar (el|la|un|mi)? <instrumento acotado>».
 * CERO keywords nuevas — floor-only (gate c.751, precedente monosemántico
 * c.752 «votar»/c.864 «escanear»/c.1241 «apagar dispositivo»); objeto
 * EXIGIDO acotado al conjunto cerrado de instrumentos. DISJUNTA de la
 * lateral del hermano «practicar <instrumento>» (c.1249, primer-push-gana).
 */
class ContextIntentEngineAfinarInstrumentoFloorTest {

    private fun analyze(text: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
        )

    @Test
    fun `afinar guitarra y piano captura task`() {
        val r1 = analyze("afinar la guitarra")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.TASK, r1!!.kind)
        assertEquals("Afinar la guitarra", r1.title)

        val r2 = analyze("afinar el piano")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.TASK, r2!!.kind)
        assertEquals("Afinar el piano", r2.title)
    }

    @Test
    fun `afinar violin con temporal captura task`() {
        val r = analyze("afinar el violín por la tarde")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertTrue(r.title.startsWith("Afinar el violín"))
    }

    @Test
    fun `afinar ukelele y saxofón con posesivo captura task`() {
        val r1 = analyze("afinar mi ukelele")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.TASK, r1!!.kind)
        assertEquals("Afinar mi ukelele", r1.title)

        val r2 = analyze("afinar el saxofón mañana")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.TASK, r2!!.kind)
        assertTrue(r2.title.startsWith("Afinar el saxofón"))
    }

    @Test
    fun `afinar trompeta y plural captura task`() {
        val r1 = analyze("afinar la trompeta")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.TASK, r1!!.kind)
        assertEquals("Afinar la trompeta", r1.title)

        val r2 = analyze("afinar las guitarras")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.TASK, r2!!.kind)
        assertEquals("Afinar las guitarras", r2.title)
    }

    @Test
    fun `guards correctos NULL`() {
        assertNull(analyze("no afinar la guitarra"))
        assertNull(analyze("afinó la guitarra ayer"))
        assertNull(analyze("quizá afinar el violín"))
        assertNull(analyze("la guitarra está afinada"))
        assertNull(analyze("afinar"))
        assertNull(analyze("la afinación del piano"))
    }

    @Test
    fun `uso figurado fuera del objeto acotado queda NULL`() {
        assertNull(analyze("afinar la puntería"))
        assertNull(analyze("afinar los detalles del proyecto"))
    }

    @Test
    fun `envolvente captura en task`() {
        val r1 = analyze("recuérdame afinar la guitarra")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.TASK, r1!!.kind)

        val r2 = analyze("tengo que afinar el piano")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.TASK, r2!!.kind)
    }
}
