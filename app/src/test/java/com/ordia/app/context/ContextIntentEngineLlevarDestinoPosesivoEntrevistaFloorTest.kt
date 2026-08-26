package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1192 — Lateral ABIERTA (destino-posesivo) documentada en c.1174
 * (G3/G4 destino-posesivo del piso entrevista) y re-documentada en
 * c.1190/c.1191: «llevar (el|mi) curr[ií]culum|cv|informe|portfolio
 * A MI ENTREVISTA» era NULL silencioso. PRE sonda efímera
 * (/tmp/probe1204.kt sobre HEAD b25dec47): 5/5 candidatas NULL,
 * 4/4 guards NULL (negación compuesta, duda subjuntivo, pretérito,
 * objeto exigido sin objeto), 3/3 regresiones «a la entrevista»
 * HIT ERRAND byte-idénticas, envolvente «recuérdame/tengo que» HIT
 * TASK por candado c.613. Gap P1: el destino más frecuente en
 * habla real («mi entrevista») del piso c.1174 quedaba descartado.
 *
 * Fix lockstep en DOS puntos (lección c.616): destino
 * `a\s+(?:la|mi)\s+entrevista` en
 * [ContextIntentEngine.ERRAND_INTERVIEW_RUN_FLOOR] y MISMA
 * alternativa en la plantilla matchInterviewRun de
 * [ContextIntentEngine.extractTitle] (grafía preservada c.653).
 * CERO keywords nuevas (gate c.751: «llevar» es keyword histórica).
 * Guard nueva: «llevarme a mi entrevista» (sin objeto) permanece
 * NULL — el objeto sigue siendo exigido (anti-overreach).
 */
class ContextIntentEngineLlevarDestinoPosesivoEntrevistaFloorTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    @Test
    fun `captura llevar el curriculum a mi entrevista`() {
        val intent = analyze("llevar el currículum a mi entrevista mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals(0.45f, intent.confidence, 1e-6f)
        assertEquals("Llevar el currículum a mi entrevista", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura llevarme el curriculum a mi entrevista`() {
        val intent = analyze("llevarme el currículum a mi entrevista")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals(0.45f, intent.confidence, 1e-6f)
        assertEquals("Llevarme el currículum a mi entrevista", intent.title)
    }

    @Test
    fun `captura llevar el cv a mi entrevista`() {
        val intent = analyze("llevar el CV a mi entrevista el jueves")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals(0.45f, intent.confidence, 1e-6f)
        assertEquals("Llevar el CV a mi entrevista", intent.title)
    }

    @Test
    fun `captura prefijo temporal informe a mi entrevista`() {
        val intent = analyze("mañana llevar el informe a mi entrevista")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals(0.45f, intent.confidence, 1e-6f)
        assertEquals("Llevar el informe a mi entrevista", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura primera persona portfolio a mi entrevista`() {
        val intent = analyze("llevo el portfolio a mi entrevista")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals(0.45f, intent.confidence, 1e-6f)
        assertEquals("Llevo el portfolio a mi entrevista", intent.title)
    }

    @Test
    fun `guard objeto exigido llevarme a mi entrevista`() {
        assertNull(analyze("llevarme a mi entrevista"))
    }

    @Test
    fun `guard negacion compuesta`() {
        assertNull(analyze("no voy a llevar el currículum a mi entrevista"))
    }

    @Test
    fun `guard duda subjuntivo`() {
        assertNull(analyze("quizá lleve el currículum a mi entrevista"))
    }

    @Test
    fun `guard pasado`() {
        assertNull(analyze("llevé el currículum a mi entrevista"))
    }

    @Test
    fun `regresion articulo la byte-identica`() {
        val intent = analyze("llevar el currículum a la entrevista mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals(0.45f, intent.confidence, 1e-6f)
        assertEquals("Llevar el currículum a la entrevista", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `envolvente recordame candidata posesiva a TASK`() {
        val intent = analyze("recuérdame llevar el currículum a mi entrevista")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals(0.56f, intent.confidence, 1e-6f)
        assertEquals("Llevar el currículum a mi entrevista", intent.title)
    }
}
