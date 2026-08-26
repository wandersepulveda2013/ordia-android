package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Objeto «mail» del piso «contestar…» c.1187 — lateral ABIERTA de MI
 * cierre c.1182 («responder el mail» FIXED): «contestar el mail (de
 * <persona>) (esta noche)» medida NULL mientras las hermanas «contestar
 * el correo» (c.873) y «contestar el WhatsApp» (c.1177) SÍ capturan.
 * «contestar» es el verbo cotidiano dominante para responder mensajería
 * escrita; el mail sin contestar es el mismo olvido laboral canónico
 * que motivó c.1182, dicho con el otro verbo.
 *
 * Lockstep DOS puntos (lección c.616/c.751; la keyword-OBJETO «mail»
 * YA existe en `ContextIntent.kt` ERRAND desde c.1182 — la frase ya
 * llega al análisis, como demuestra el PRE: NULL por piso, no por
 * keyword): (1) objeto `mails?` en el piso «contestar…» de
 * `hasStrongTaskImperative`; (2) MISMO objeto en la plantilla
 * `matchContestarA` de `extractTitle` (grafía preservada c.653).
 *
 * Sonda persistida `tools/probe/TwentiethClassMailContestProbe.kt`:
 * PRE medido NULL 5/5 candidatas sobre HEAD 27e300b (mi c.1182
 * integrada); controles C6–C9 NULL correctos; regresiones C10–C12 HIT.
 */
class ContextIntentEngineContestarMailObjectTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    @Test
    fun `contestar el mail de persona captura`() {
        val r = analyze("contestar el mail de Marta esta noche")
        assertNotNull("«contestar el mail de Marta esta noche» (era NULL en sonda PRE C1)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Contestar el mail de Marta", r.title)
        assertNotNull("«esta noche» debe anclar dueAt", r.dueAt)
    }

    @Test
    fun `contestar el mail de trabajo captura`() {
        val r = analyze("contestar el mail de trabajo mañana")
        assertNotNull("«contestar el mail de trabajo mañana» (era NULL C2)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Contestar el mail de trabajo", r.title)
        assertNotNull("«mañana» debe anclar dueAt", r.dueAt)
    }

    @Test
    fun `contestar los mails plural captura`() {
        val r = analyze("contestar los mails del cliente")
        assertNotNull("«contestar los mails del cliente» (era NULL C3)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Contestar los mails del cliente", r.title)
    }

    @Test
    fun `contestar el mail con reconocimiento captura`() {
        val r = analyze("vale, contestar el mail del jefe")
        assertNotNull("«vale, contestar el mail del jefe» (era NULL C4)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Contestar el mail del jefe", r.title)
    }

    @Test
    fun `contestar el mail con temporal inicial captura`() {
        val r = analyze("esta noche contestar el mail")
        assertNotNull("«esta noche contestar el mail» (era NULL C5)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Contestar el mail", r.title)
        assertNotNull("«esta noche» debe anclar dueAt", r.dueAt)
    }

    @Test
    fun `contestar el mail negado no captura`() {
        assertNull(
            "«no contestar el mail del banco» debe seguir NULL (negación, (?<!no ))",
            analyze("no contestar el mail del banco")
        )
    }

    @Test
    fun `contestar el mail en pasado no captura`() {
        assertNull(
            "«contesté el mail de Marta ayer» debe seguir NULL (pasado realizado)",
            analyze("contesté el mail de Marta ayer")
        )
    }

    @Test
    fun `mail recibido y contestado no captura`() {
        assertNull(
            "«me contestó el mail ayer» debe seguir NULL (estado recibido, no acción propia)",
            analyze("me contestó el mail ayer")
        )
    }

    @Test
    fun `mail como sustantivo aislado no captura`() {
        assertNull(
            "«el mail del cliente» debe seguir NULL (sustantivo aislado, sin verbo)",
            analyze("el mail del cliente")
        )
    }

    @Test
    fun `regresion contestar el whatsapp sigue capturando`() {
        val r = analyze("contestar el WhatsApp de Marta")
        assertNotNull("regresión c.1177: «contestar el WhatsApp de Marta» debe seguir HIT", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }

    @Test
    fun `regresion responder el mail sigue capturando`() {
        val r = analyze("responder el mail de trabajo esta noche")
        assertNotNull("regresión c.1182: «responder el mail de trabajo esta noche» debe seguir HIT", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }

    @Test
    fun `regresion contestar el correo sigue capturando`() {
        val r = analyze("contestar el correo del cole esta tarde")
        assertNotNull("regresión c.873: «contestar el correo del cole esta tarde» debe seguir HIT", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }
}
