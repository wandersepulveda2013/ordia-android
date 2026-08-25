package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Delta c.1057 — vía pasear diminutivo «pasear al perrito»/«pasear al gatito»
 * (lateral documentada en BACKLOG c.1054/c.1056; pins FUERA medidos).
 *
 * El piso `HOUSEHOLD_WALK_DOG_FLOOR` anclaba `(?:perr[oa]|gat[oa])s?` y los
 * diminutivos de mascota caían FUERA por el `\b` final — PRE medido 6/6 NULL
 * con sonda efímera `/tmp/probe1057/Probe.kt`. Fix: ancla de objeto extendida a
 * `(?:perrit[oa]|gatit[oa]|perr[oa]|gat[oa])s?` en lockstep 3 puntos (piso +
 * cláusula negación + plantilla título; doctrina c.653). CERO keywords nuevas.
 */
class ContextIntentEnginePasearDiminutivoDeltaTest {
    private fun analyze(text: String): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    private fun hit(text: String, kind: ContextIntentKind) {
        val intent = analyze(text)
        assertNotNull("$text", intent)
        assertEquals("$text", kind, intent!!.kind)
    }

    @Test fun `c1 pasear al perrito es HOUSEHOLD`() { hit("pasear al perrito", ContextIntentKind.HOUSEHOLD) }
    @Test fun `c2 pasear a la perrita es HOUSEHOLD`() { hit("pasear a la perrita", ContextIntentKind.HOUSEHOLD) }
    @Test fun `c3 pasear a mi gatito es HOUSEHOLD`() { hit("pasear a mi gatito", ContextIntentKind.HOUSEHOLD) }
    @Test fun `c4 pasear al gatito es HOUSEHOLD`() { hit("pasear al gatito", ContextIntentKind.HOUSEHOLD) }
    @Test fun `c5 pasear al perrito a las 8 es HOUSEHOLD con dueAt`() {
        val i = analyze("pasear al perrito a las 8"); assertNotNull(i); assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind); assertNotNull(i.dueAt)
    }
    @Test fun `c5b pasear al gatito a las 9 es HOUSEHOLD con dueAt`() {
        val i = analyze("pasear al gatito a las 9"); assertNotNull(i); assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind); assertNotNull(i.dueAt)
    }
    @Test fun `c6 envolvente tengo que pasear al perrito es TASK`() { hit("tengo que pasear al perrito", ContextIntentKind.TASK) }
    @Test fun `guard negación inmediata no pasear al perrito es NULL`() { assertNull(analyze("no pasear al perrito hoy")) }
    @Test fun `guard negación envolvente no voy a pasear al perrito es NULL`() { assertNull(analyze("no voy a pasear al perrito")) }
    @Test fun `guard pasado paseé al perrito ayer es NULL`() { assertNull(analyze("paseé al perrito ayer")) }
    @Test fun `guard hedge quizá pasee al perrito mañana es NULL`() { assertNull(analyze("quizá pasee al perrito mañana")) }
    @Test fun `regresión pasear al perro es HOUSEHOLD`() { hit("pasear al perro", ContextIntentKind.HOUSEHOLD) }
    @Test fun `regresión pasear a la gata es HOUSEHOLD`() { hit("pasear a la gata", ContextIntentKind.HOUSEHOLD) }
    @Test fun `regresión pasear al perro a las 8 es HOUSEHOLD con dueAt`() {
        val i = analyze("pasear al perro a las 8"); assertNotNull(i); assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind); assertNotNull(i.dueAt)
    }
    @Test fun `fuera salir a pasear bivalente es NULL`() { assertNull(analyze("salir a pasear mañana")) }
    @Test fun `fuera pasear al bebé destinatario humano es NULL`() { assertNull(analyze("pasear al bebé mañana")) }
}
