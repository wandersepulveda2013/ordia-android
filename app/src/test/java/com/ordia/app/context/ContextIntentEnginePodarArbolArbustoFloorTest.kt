package com.ordia.app.context

import org.junit.Assert
import org.junit.Test

// c.1213: extensión de objetos del piso jardín (lateral ABIERTA de MI auditoría
// c.1211, clase VIGESIMOSÉPTIMA jardinería/plantas) — árbol(es)/arbusto(s). PRE
// medido con sonda efímera /tmp/probe1213.kt: C1–C4 NULL (hueco de captura),
// G1–G5 ya NULL (negación/pretérito/duda/nominalización/diminutivo), R1–R3
// jardín/rosal/setos HIT, E1 envoltura TASK. Lockstep DOS puntos (lección
// c.616): alternancia `(?:árbol(?:es)?|arbustos?)` en `HOUSEHOLD_GARDEN_FLOOR`
// + MISMA en `matchPodarJardin` (grafía preservada c.653). CERO keywords
// nuevas (gate c.751: «podar» histórica basta). Re-pin legítimo del canary
// in-file del hermano `podarElArbol_canaryLateralAbierta` invertido NULL→HIT
// (precedente c.1196 «haré»).
class ContextIntentEnginePodarArbolArbustoFloorTest {

    private fun a(text: String) =
        ContextIntentEngine.analyze(ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L))

    @Test fun podarElArbol_hit() {
        val r = a("podar el árbol")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertTrue(r.confidence >= 0.45f)
        Assert.assertEquals("Podar el árbol", r.title)
    }

    @Test fun podarLosArboles_hit() {
        val r = a("podar los árboles")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Podar los árboles", r.title)
    }

    @Test fun podarElArbusto_hit() {
        val r = a("podar el arbusto")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Podar el arbusto", r.title)
    }

    @Test fun podarLosArbustos_hit() {
        val r = a("podar los arbustos")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Podar los arbustos", r.title)
    }

    @Test fun podarLosArbolesConTemporal_hit() {
        val r = a("podar los árboles mañana")
        Assert.assertNotNull(r)
        Assert.assertTrue(r!!.dueAt != null)
    }

    @Test fun noPodarLosArboles_null() {
        val r = a("no podar los árboles")
        Assert.assertNull(r)
    }

    @Test fun yaPodeElArbol_null() {
        val r = a("ya podé el árbol")
        Assert.assertNull(r)
    }

    @Test fun quizasPodeElArbol_null() {
        val r = a("quizás pode el árbol")
        Assert.assertNull(r)
    }

    @Test fun laPodaDelArbol_null() {
        val r = a("la poda del árbol")
        Assert.assertNull(r)
    }

    @Test fun podarElArbolitoDiminutivo_null() {
        val r = a("podar el arbolito")
        Assert.assertNull(r)
    }

    @Test fun podarElJardin_regressionNoCollision() {
        val r = a("podar el jardín")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Podar el jardín", r.title)
    }

    @Test fun recordamePodarElArbol_envelopeStillTask() {
        val r = a("recuérdame podar el árbol")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.TASK, r!!.kind)
        Assert.assertEquals("Podar el árbol", r.title)
    }
}
