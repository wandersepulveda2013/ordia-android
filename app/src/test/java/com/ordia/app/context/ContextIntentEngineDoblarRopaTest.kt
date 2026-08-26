package com.ordia.app.context

import org.junit.Assert
import org.junit.Test

class ContextIntentEngineDoblarRopaTest {

    private fun a(text: String) =
        ContextIntentEngine.analyze(ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L))

    @Test fun doblarLaRopa_hit() {
        val r = a("doblar la ropa")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertTrue(r.confidence >= 0.45f)
        Assert.assertEquals("Doblar la ropa", r.title)
    }

    @Test fun doblarMiRopa_hit() {
        val r = a("doblar mi ropa")
        Assert.assertNotNull(r)
    }

    @Test fun doblarLaRopaConTemporal_hit() {
        val r = a("doblar la ropa hoy")
        Assert.assertNotNull(r)
        Assert.assertTrue(r!!.dueAt != null)
    }

    @Test fun doblarLosTrapos_canary() {
        val r = a("doblar los trapos hoy")
        Assert.assertNull(r)
    }

    @Test fun noDoblarLaRopa_null() {
        val r = a("no doblar la ropa")
        Assert.assertNull(r)
    }

    @Test fun yaDobleLaRopa_null() {
        val r = a("ya doblé la ropa")
        Assert.assertNull(r)
    }

    @Test fun quizáDobleLaRopa_null() {
        val r = a("quizá doble la ropa")
        Assert.assertNull(r)
    }

    @Test fun elDobladoDeRopa_null() {
        val r = a("el doblado de ropa")
        Assert.assertNull(r)
    }

    @Test fun lavarLaRopa_regressionNoCollision() {
        val r = a("lavar la ropa")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Lavar la ropa", r.title)
    }

    @Test fun colgarLaRopa_regressionNoCollision() {
        val r = a("colgar la ropa")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Colgar la ropa", r.title)
    }

    @Test fun recordameDoblarLaRopa_envelopeStillTask() {
        val r = a("recuérdame doblar la ropa")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.TASK, r!!.kind)
        Assert.assertEquals("Doblar la ropa", r.title)
    }
}
