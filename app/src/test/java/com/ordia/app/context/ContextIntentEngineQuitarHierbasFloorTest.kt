package com.ordia.app.context

import org.junit.Assert
import org.junit.Test

/**
 * TDD pin del gap (a) de la auditoría clase VIGESIMOSÉPTIMA (c.1212):
 * piso [HOUSEHOLD_WEED_FLOOR] (`quitar (las) (malas) hierba(s)`), tercer
 * piso de la familia «quitar» por objeto disjunto (polvo c.732 / mesa
 * c.754 / hierbas aquí). Sonda PRE/POST `tools/probe/QuitarHierbasProbe.kt`
 * (segunda sonda del dominio a propósito, hermano de
 * `PodarRosalSetosProbe.kt` c.1211): NULL→HIT medido.
 */
class ContextIntentEngineQuitarHierbasFloorTest {

    private fun a(text: String) =
        ContextIntentEngine.analyze(ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L))

    @Test fun quitarLasMalasHierbas_hit() {
        val r = a("quitar las malas hierbas")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertTrue(r.confidence >= 0.45f)
        Assert.assertEquals("Quitar las malas hierbas", r.title)
    }

    @Test fun quitarLaHierba_hit() {
        val r = a("quitar la hierba")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Quitar la hierba", r.title)
    }

    @Test fun quitarLasHierbas_hit() {
        val r = a("quitar las hierbas")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Quitar las hierbas", r.title)
    }

    @Test fun temporalPrefixQuitarLasMalasHierbas_hit() {
        val r = a("mañana quitar las malas hierbas")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Quitar las malas hierbas", r.title)
    }

    @Test fun politePrefixQuitarLaHierba_hit() {
        val r = a("por favor quitar la hierba")
        Assert.assertNotNull(r)
        Assert.assertEquals("Quitar la hierba", r!!.title)
    }

    @Test fun futuroQuitareLateral_pin() {
        // Lateral ABIERTA explícita (TDD): «quitaré» (futuro) no casa con
        // el infinitivo del piso — mismo canario que «la poda» c.1211.
        val r = a("vale, quitaré las malas hierbas mañana")
        Assert.assertNull(r)
    }

    @Test fun diminutivoHierbajillo_canaryBoundary() {
        // Guard `\b` final: diminutivos no casan (canary como «seta» c.1211).
        val r = a("quitar el hierbajillo de la esquina")
        Assert.assertNull(r)
    }

    @Test fun pastQuite_null() {
        val r = a("quité las malas hierbas")
        Assert.assertNull(r)
    }

    @Test fun negatedNoQuitar_null() {
        val r = a("no quitar las malas hierbas")
        Assert.assertNull(r)
    }

    @Test fun declarativeHierbasHuerta_null() {
        val r = a("las malas hierbas no dejan crecer la huerta")
        Assert.assertNull(r)
    }

    @Test fun culinaryHierbasAromaticas_canaryKeywordAlone() {
        // keyword-OBJETO «hierbas» sola (0.12) queda bajo el umbral —
        // guard anti-bivalencia familias c.754 («quitar la mesa»).
        val r = a("las hierbas aromáticas están en la cocina")
        Assert.assertNull(r)
    }

    @Test fun quitarLaMesa_regressionNoCollision() {
        val r = a("quitar la mesa")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Quitar la mesa", r.title)
    }

    @Test fun quitarElPolvo_regressionNoCollision() {
        val r = a("quitar el polvo")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Quitar el polvo", r.title)
    }

    @Test fun podarLosSetos_regressionNoCollision() {
        val r = a("podar los setos")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Podar los setos", r.title)
    }
}
