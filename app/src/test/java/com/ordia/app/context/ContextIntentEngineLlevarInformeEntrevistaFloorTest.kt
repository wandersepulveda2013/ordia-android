package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1191 — Lateral ABIERTA (b) del cierre c.1190: objeto «el informe|
 * portfolio» del piso entrevista
 * [ContextIntentEngine.ERRAND_INTERVIEW_RUN_FLOOR]. PRE sonda efímera
 * (/tmp/probe1191pre.kt): 4/4 targets NULL, 3/3 guards NULL, 3/3
 * regresiones HIT («currículum»/«CV»). Gap P1: olvido silencioso del
 * documento que preparar para la entrevista, asimetría de producto
 * frente a «currículum» (c.1174) y «CV» (c.1190).
 *
 * Lockstep en DOS puntos (lección c.616): objeto
 * `(?:curr[ií]culum|cv|informe|portfolio)` en el piso + MISMO objeto
 * en la plantilla matchInterviewRun de [ContextIntentEngine.extractTitle]
 * (grafía preservada c.653). Re-pin legítimo del guard «el informe
 * fuera» en c.1174 y c.1190 (documentado, precedente c.1168/c.1185).
 * CERO keywords nuevas (gate c.751: «llevar» es keyword histórica).
 */
class ContextIntentEngineLlevarInformeEntrevistaFloorTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    @Test
    fun `captura llevar el informe a la entrevista`() {
        val intent = analyze("llevar el informe a la entrevista mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals(0.45f, intent.confidence, 1e-6f)
        assertEquals("Llevar el informe a la entrevista", intent.title)
    }

    @Test
    fun `captura llevarme el informe a la entrevista`() {
        val intent = analyze("llevarme el informe a la entrevista")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals(0.45f, intent.confidence, 1e-6f)
        assertEquals("Llevarme el informe a la entrevista", intent.title)
    }

    @Test
    fun `captura llevar el portfolio a la entrevista`() {
        val intent = analyze("llevar el portfolio a la entrevista")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals(0.45f, intent.confidence, 1e-6f)
        assertEquals("Llevar el portfolio a la entrevista", intent.title)
    }

    @Test
    fun `captura posesivo mi informe`() {
        val intent = analyze("llevar mi informe a la entrevista")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals(0.45f, intent.confidence, 1e-6f)
    }

    @Test
    fun `guard negacion compuesta`() {
        assertNull(analyze("no voy a llevar el informe a la entrevista"))
    }

    @Test
    fun `guard pasado`() {
        assertNull(analyze("llevé el informe a la entrevista ayer"))
    }

    @Test
    fun `guard duda modal`() {
        assertNull(analyze("quizá lleve el portfolio a la entrevista"))
    }

    @Test
    fun `regresion curriculum y CV intactos`() {
        val cv = analyze("llevar el CV a la entrevista mañana")
        assertNotNull(cv)
        assertEquals(ContextIntentKind.ERRAND, cv!!.kind)
        val cur = analyze("llevarme el currículum a la entrevista mañana")
        assertNotNull(cur)
        assertEquals(ContextIntentKind.ERRAND, cur!!.kind)
    }
}
