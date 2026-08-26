package com.ordia.app.context

import org.junit.Assert
import org.junit.Test

class ContextIntentEnginePodarRosalSetosFloorTest {

    private fun a(text: String) =
        ContextIntentEngine.analyze(ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L))

    @Test fun podarElRosal_hit() {
        val r = a("podar el rosal")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertTrue(r.confidence >= 0.45f)
        Assert.assertEquals("Podar el rosal", r.title)
    }

    @Test fun podarLosSetos_hit() {
        val r = a("podar los setos")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertTrue(r!!.confidence >= 0.45f)
        Assert.assertEquals("Podar los setos", r.title)
    }

    @Test fun podarLosRosales_hit() {
        val r = a("podar los rosales")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Podar los rosales", r.title)
    }

    @Test fun podarMiRosal_hit() {
        val r = a("podar mi rosal")
        Assert.assertNotNull(r)
    }

    @Test fun podarLosSetosConTemporal_hit() {
        val r = a("podar los setos mañana")
        Assert.assertNotNull(r)
        Assert.assertTrue(r!!.dueAt != null)
    }

    // c.1213 re-pin conservador legítimo (este lado): lateral ABIERTA
    // «árbol(es)/arbusto(s)» (canary NULL desde el pin original) RESUELTA por
    // la alternancia extensa del piso jardín; pin invertido NULL→HIT con la
    // misma frase (precedente c.1196 «haré»; mantiene guard de negación
    // (?<!no ) y guardas de pretérito/duda/nominalización in-file intactas).
    @Test fun podarElArbol_repinLateralResuelta() {
        val r = a("podar el árbol")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Podar el árbol", r!!.title)
    }

    @Test fun noPodarLosSetos_null() {
        val r = a("no podar los setos")
        Assert.assertNull(r)
    }

    @Test fun yaPodeElRosal_null() {
        val r = a("ya podé el rosal")
        Assert.assertNull(r)
    }

    @Test fun quizasPodeLosSetos_null() {
        val r = a("quizás pode los setos")
        Assert.assertNull(r)
    }

    @Test fun laPodaDelRosal_null() {
        val r = a("la poda del rosal")
        Assert.assertNull(r)
    }

    @Test fun podarElJardin_regressionNoCollision() {
        val r = a("podar el jardín")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Podar el jardín", r.title)
    }

    @Test fun recordamePodarElRosal_envelopeStillTask() {
        val r = a("recuérdame podar el rosal")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.TASK, r!!.kind)
        Assert.assertEquals("Podar el rosal", r.title)
    }

    // c.1211 una re-pin aditiva (STALE_RUN convergente): singular-el-seto;
    // acuse «vale,»; prefijo temporal; diminutivo-abortado.
    @Test fun podarElSeto_hit() {
        val r = a("podar el seto")
        Assert.assertNotNull(r)
        Assert.assertEquals("Podar el seto", r!!.title)
    }

    @Test fun valePodarElRosal_accuseHit() {
        val r = a("vale, podar el rosal")
        Assert.assertNotNull(r)
        Assert.assertEquals("Podar el rosal", r!!.title)
    }

    @Test fun manaPodarLosSetosTemporalPrefix_hit() {
        val r = a("mañana podar los setos")
        Assert.assertNotNull(r)
        Assert.assertTrue(r!!.dueAt != null)
    }

    @Test fun podarElSetitoDiminutivo_null() {
        val r = a("podar el setito")
        Assert.assertNull(r)
    }
}
