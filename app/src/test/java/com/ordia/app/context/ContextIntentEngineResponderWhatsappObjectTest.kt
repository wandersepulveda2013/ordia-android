package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Objeto «whatsapp» del piso «responder…» c.1192 — hermana ABIERTA del
 * piso responder c.860/c.867/c.869 (`mails?` c.1182) y de MI cierre
 * c.1187 (piso contestar): «responder el whatsapp (de <persona>)
 * (mañana)» medida NULL en sonda PRE persistida
 * `tools/probe/ResponderWhatsappObjectProbe.kt` (5/5 candidatas NULL,
 * guards NULL correctos, 4 regresiones HIT).
 *
 * Lockstep DOS puntos (lección c.616/c.751; la keyword-OBJETO
 * «whatsapp» YA existe en `ContextIntent.kt` desde c.1177 — la frase
 * llega al análisis, NULL por piso, no por keyword): (1) objeto
 * `whatsapps?` en el piso «responder…» de `hasStrongTaskImperative`;
 * (2) MISMO objeto en la plantilla `matchResponderCorreo` (grafía
 * preservada c.653). La hermana «grafías wasap/wassap» sigue lateral.
 */
class ContextIntentEngineResponderWhatsappObjectTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    @Test
    fun `responder el whatsapp de persona captura`() {
        val r = analyze("responder el whatsapp de Marta mañana")
        assertNotNull("«responder el whatsapp de Marta mañana» (era NULL en sonda PRE C1)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Responder el whatsapp de Marta", r.title)
        assertNotNull("«mañana» debe anclar dueAt", r.dueAt)
    }

    @Test
    fun `responder el whatsapp del grupo captura`() {
        val r = analyze("responder el whatsapp del grupo esta tarde")
        assertNotNull("«responder el whatsapp del grupo esta tarde» (era NULL C2)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Responder el whatsapp del grupo", r.title)
        assertNotNull("«esta tarde» debe anclar dueAt", r.dueAt)
    }

    @Test
    fun `responder los whatsapps plural captura`() {
        val r = analyze("responder los whatsapps pendientes")
        assertNotNull("«responder los whatsapps pendientes» (era NULL C3)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Responder los whatsapps pendientes", r.title)
    }

    @Test
    fun `responder el whatsapp con reconocimiento captura`() {
        val r = analyze("vale, responder el whatsapp del jefe")
        assertNotNull("«vale, responder el whatsapp del jefe» (era NULL C4)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Responder el whatsapp del jefe", r.title)
    }

    @Test
    fun `responder el whatsapp con temporal inicial captura`() {
        val r = analyze("esta noche responder el whatsapp")
        assertNotNull("«esta noche responder el whatsapp» (era NULL C5)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Responder el whatsapp", r.title)
        assertNotNull("«esta noche» debe anclar dueAt", r.dueAt)
    }

    @Test
    fun `responder whatsapp negado no captura`() {
        assertNull(
            "«no responder el whatsapp del banco» debe seguir NULL (negación, (?<!no ))",
            analyze("no responder el whatsapp del banco")
        )
    }

    @Test
    fun `responder whatsapp en pasado no captura`() {
        assertNull(
            "«respondí el whatsapp ayer» debe seguir NULL (pasado realizado)",
            analyze("respondí el whatsapp ayer")
        )
    }

    @Test
    fun `whatsapp respondido por tercero no captura`() {
        assertNull(
            "«me respondió el whatsapp ayer» debe seguir NULL (estado recibido)",
            analyze("me respondió el whatsapp ayer")
        )
    }

    @Test
    fun `whatsapp como sustantivo aislado no captura`() {
        assertNull(
            "«el whatsapp del cliente» debe seguir NULL (sustantivo aislado)",
            analyze("el whatsapp del cliente")
        )
    }

    @Test
    fun `responder a la pregunta no captura`() {
        assertNull(
            "«responder a la pregunta del examen» debe seguir NULL (rama bivalente)",
            analyze("responder a la pregunta del examen")
        )
    }

    @Test
    fun `regresion responder el correo sigue capturando`() {
        val r = analyze("responder el correo del trabajo mañana")
        assertNotNull("regresión c.860: «responder el correo del trabajo mañana» debe seguir HIT", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }

    @Test
    fun `regresion responder el mail sigue capturando`() {
        val r = analyze("responder el mail de trabajo esta noche")
        assertNotNull("regresión c.1182: «responder el mail de trabajo esta noche» debe seguir HIT", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }

    @Test
    fun `regresion contestar el whatsapp sigue capturando`() {
        val r = analyze("contestar el WhatsApp de Marta")
        assertNotNull("regresión c.1177: «contestar el WhatsApp de Marta» debe seguir HIT", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }
}
