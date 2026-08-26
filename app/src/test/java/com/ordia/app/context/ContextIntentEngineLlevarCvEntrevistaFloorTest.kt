package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1190 — Lateral ABIERTA del cierre c.1174: objeto abreviado «el CV»
 * (acrónimo hablado dominante) del piso entrevista
 * [ContextIntentEngine.ERRAND_INTERVIEW_RUN_FLOOR]. Sin el piso,
 * «llevar (me) el CV a la entrevista» se perdía (NULL): keyword «llevar»
 * 0.12 + bono temporal 0.1 = 0.22 < umbral; sin el piso el acarreo del
 * CV el día de la entrevista (olvido silencioso P1) quedaba huérfano
 * mientras su sinónimo «currículum» sí capturaba (asimetría de producto
 * dentro del mismo piso).
 *
 * Lockstep en DOS puntos (lección c.616): objeto
 * `curr[ií]culum` → `(?:curr[ií]culum|cv\b)` en
 * [ContextIntentEngine.ERRAND_INTERVIEW_RUN_FLOOR] (casa sobre `lower`)
 * + MISMO objeto en la plantilla matchInterviewRun de
 * [ContextIntentEngine.extractTitle] (grafía preservada c.653:
 * «CV» mayúsculas intactas en el título). CERO keywords nuevas (gate
 * c.751 satisfecho: «llevar» ya es keyword histórica).
 *
 * Guards NULL: negación, sin-destino, prefijo largo «CVD», duda,
 * objeto fuera pineado («informe»). Regresión: «currículum» intacto.
 */
class ContextIntentEngineLlevarCvEntrevistaFloorTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    @Test
    fun `captura llevar el CV a la entrevista`() {
        val intent = analyze("llevar el CV a la entrevista mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals(0.45f, intent.confidence, 1e-6f)
        assertEquals("Llevar el CV a la entrevista", intent.title)
    }

    @Test
    fun `captura llevarme el CV a la entrevista`() {
        val intent = analyze("llevarme el CV a la entrevista mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals(0.45f, intent.confidence, 1e-6f)
        assertEquals("Llevarme el CV a la entrevista", intent.title)
    }

    @Test
    fun `captura cv minuscula grafia preservada`() {
        val intent = analyze("llevar el cv a la entrevista")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals(0.45f, intent.confidence, 1e-6f)
        assertEquals("Llevar el cv a la entrevista", intent.title)
    }

    @Test
    fun `captura presente llevo mi CV`() {
        val intent = analyze("llevo mi CV a la entrevista")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals(0.45f, intent.confidence, 1e-6f)
    }

    @Test
    fun `guard negacion compuesta`() {
        assertNull(analyze("no voy a llevar el CV a la entrevista mañana"))
    }

    @Test
    fun `guard sin destino`() {
        assertNull(analyze("llevar el CV mañana"))
    }

    @Test
    fun `guard prefijo largo CVD`() {
        assertNull(analyze("llevar el CVD a la entrevista mañana"))
    }

    @Test
    fun `guard duda modal`() {
        assertNull(analyze("quizá lleve el CV a la entrevista"))
    }

    @Test
    fun `guard objeto informe pineado fuera`() {
        assertNull(analyze("llevar el informe a la entrevista mañana"))
    }

    @Test
    fun `regresion curriculum intacto`() {
        val intent = analyze("llevarme el currículum a la entrevista mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals(0.45f, intent.confidence, 1e-6f)
    }
}
