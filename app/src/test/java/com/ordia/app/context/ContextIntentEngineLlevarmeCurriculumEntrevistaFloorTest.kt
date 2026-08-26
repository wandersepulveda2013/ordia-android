package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * c.1180: lateral ABIERTA de MI cierre c.1174 — reflexivo errand
 * «llevarme (el|mi|tu|su) curr[ií]culum a la entrevista (mañana)».
 * La forma hablada natural del caso P1 de c.1174 («llevar el
 * currículum a la entrevista») con el pronombre enclítico «me» se
 * DESCARTABA en silencio: los pisos «llevar» exigen el verbo sin
 * enclítico. Mismo olvido silencioso P1: «tengo que llevarme el
 * currículum a la entrevista» es como se dice de verdad.
 * PRE medido (sonda efímera `/tmp/probe1174lat.kt`, motor real vía
 * tools/run_probe.sh, HEAD `a98c7e5`): 5/5 laterales de la fila
 * c.1174 NULL (reflexivo/informe/portfolio/CV/destino-posesivo);
 * UNA por ciclo (doctrina anti-overreach) — se cierra el reflexivo
 * y quedan pineadas NULL las variantes de objeto y destino.
 * Fix lockstep 2 puntos (lección c.616; CERO keywords nuevas):
 * el piso `ERRAND_INTERVIEW_RUN_FLOOR` (c.1174) admite el verbo
 * enclítico «llevarme» + MISMA forma en la plantilla
 * `matchInterviewRun` de [ContextIntentEngine.extractTitle] (verbo
 * preservado con su enclítico, doctrina c.653: «Llevarme el
 * currículum a la entrevista»). Objeto y destino EXIGIDOS se
 * mantienen: «llevarme el informe» / «llevarme a la entrevista»
 * (transporte de uno mismo) siguen NULL pineados.
 */
class ContextIntentEngineLlevarmeCurriculumEntrevistaFloorTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1000)
    )

    // ---- Capturas directas (piso) ----

    @Test
    fun `captura llevarme curriculum entrevista manana`() {
        val intent = analyze("llevarme el currículum a la entrevista mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevarme el currículum a la entrevista", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura llevarme posesivo mi curriculum`() {
        val intent = analyze("llevarme mi currículum a la entrevista mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevarme mi currículum a la entrevista", intent.title)
    }

    @Test
    fun `captura llevarme grafia sin tilde curriculum`() {
        val intent = analyze("llevarme el curriculum a la entrevista")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevarme el curriculum a la entrevista", intent.title)
    }

    @Test
    fun `captura prefijo temporal`() {
        val intent = analyze("mañana llevarme el currículum a la entrevista")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevarme el currículum a la entrevista", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura entrevista de trabajo`() {
        val intent = analyze("llevarme el currículum a la entrevista de trabajo mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertTrue(intent.title.startsWith("Llevarme el currículum a la entrevista"))
    }

    // ---- Guards (NULLs correctos) ----

    @Test
    fun `guard negacion directa`() {
        assertNull(analyze("no llevarme el currículum a la entrevista"))
    }

    @Test
    fun `guard negacion compuesta`() {
        assertNull(analyze("no voy a llevarme el currículum a la entrevista mañana"))
    }

    @Test
    fun `guard sin destino`() {
        assertNull(analyze("llevarme el currículum mañana"))
    }

    @Test
    fun `guard sin objeto transporte propio`() {
        assertNull(analyze("llevarme a la entrevista mañana"))
    }

    @Test
    fun `guard objeto informe pineado fuera`() {
        assertNull(analyze("llevarme el informe a la entrevista mañana"))
    }
}
