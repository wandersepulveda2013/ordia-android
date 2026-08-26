package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD RED→GREEN c.1241 (lateral (a) FUERTE de MI auditoría c.1240, clase
 * XXXI tecnología): «apagar/encender (el|la) <dispositivo-electrónico>».
 * CERO keywords nuevas — floor-only: «apagar/encender» casi monosemáticos
 * (gate c.751, precedente c.752 «votar»); objeto EXIGIDO acotado.
 */
class ContextIntentEngineApagarDispositivoFloorTest {

    private fun analyze(text: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
        )

    @Test
    fun `apagar ordenador y portatil captura task`() {
        val r1 = analyze("apagar el ordenador por la noche")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.TASK, r1!!.kind)
        assertTrue(r1.title.startsWith("Apagar el ordenador"))

        val r2 = analyze("apagar el portátil")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.TASK, r2!!.kind)
        assertEquals("Apagar el portátil", r2.title)
    }

    @Test
    fun `encender tablet y router captura task`() {
        val r1 = analyze("encender la tablet")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.TASK, r1!!.kind)
        assertEquals("Encender la tablet", r1.title)

        val r2 = analyze("encender el router")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.TASK, r2!!.kind)
        assertEquals("Encender el router", r2.title)
    }

    @Test
    fun `apagar móvil y wifi captura task`() {
        val r1 = analyze("apagar el móvil esta noche")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.TASK, r1!!.kind)
        assertTrue(r1.title.startsWith("Apagar el móvil"))

        val r2 = analyze("apagar el wifi")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.TASK, r2!!.kind)
        assertEquals("Apagar el wifi", r2.title)
    }

    @Test
    fun `guards correctos NULL`() {
        assertNull(analyze("no apagar el ordenador"))
        assertNull(analyze("quizá apagar el router"))
        assertNull(analyze("apagué el ordenador ayer"))
        assertNull(analyze("el ordenador está apagado"))
    }

    @Test
    fun `objeto fuera de la electrónica queda NULL`() {
        assertNull(analyze("apagar la luz"))
        assertNull(analyze("encender el fuego"))
    }

    @Test
    fun `envolvente captura en task`() {
        val r = analyze("recuérdame apagar el ordenador")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }

    @Test
    fun `regresiones vecinas intactas`() {
        val r1 = analyze("reiniciar el router")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.TASK, r1!!.kind)

        val r2 = analyze("formatear el portátil")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.TASK, r2!!.kind)

        val r3 = analyze("configurar el wifi")
        assertNotNull(r3)
        assertEquals(ContextIntentKind.TASK, r3!!.kind)
    }

    @Test
    fun `conectar wifi y bluetooth captura task c1242`() {
        val r1 = analyze("conectar el wifi en casa")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.TASK, r1!!.kind)
        assertTrue(r1.title.startsWith("Conectar el wifi"))

        val r2 = analyze("conectar el bluetooth")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.TASK, r2!!.kind)
        assertEquals("Conectar el bluetooth", r2.title)

        val r3 = analyze("mañana conectar el wifi")
        assertNotNull(r3)
        assertEquals(ContextIntentKind.TASK, r3!!.kind)
    }

    @Test
    fun `guards conectar NULL c1242`() {
        assertNull(analyze("no conectar el wifi"))
        assertNull(analyze("conectar a la gente"))
        assertNull(analyze("el wifi conectado"))
    }


    @Test
    fun `encender apagar clima captura task c1244`() {
        val r1 = analyze("encender la calefacción")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.TASK, r1!!.kind)
        assertEquals("Encender la calefacción", r1.title)

        val r2 = analyze("apagar el aire acondicionado")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.TASK, r2!!.kind)
        assertEquals("Apagar el aire acondicionado", r2.title)

        val r3 = analyze("hoy apagar la chimenea")
        assertNotNull(r3)
        assertEquals(ContextIntentKind.TASK, r3!!.kind)
    }

    @Test
    fun `guards clima NULL c1244`() {
        assertNull(analyze("no encender la calefacción"))
        assertNull(analyze("la chimenea está fría"))
    }

}
