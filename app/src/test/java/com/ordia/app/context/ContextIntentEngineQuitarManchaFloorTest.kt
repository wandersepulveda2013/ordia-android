package com.ordia.app.context

import org.junit.Assert
import org.junit.Test

/**
 * TDD pin de la lateral (d) de la auditoría clase VIGESIMOCTAVA ROPA
 * (c.1209): piso [HOUSEHOLD_STAIN_FLOOR] (`quitar (la|las|una)? mancha(s)
 * (de la camisa/pantalón/vestido/sudadera/sofá)`), cuarto piso de la
 * familia «quitar» por objeto disjunto (polvo c.732 / mesa c.754 /
 * hierbas c.1212 / mancha aquí). Sonda PRE/POST
 * `tools/probe/QuitarManchaProbe.kt`: NULL→HIT medido.
 */
class ContextIntentEngineQuitarManchaFloorTest {

    private fun a(text: String) =
        ContextIntentEngine.analyze(ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L))

    @Test fun quitarLaManchaDeLaCamisa_hit() {
        val r = a("quitar la mancha de la camisa")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertTrue(r.confidence >= 0.45f)
        Assert.assertEquals("Quitar la mancha de la camisa", r.title)
    }

    @Test fun quitarLaManchaDelPantalon_hit() {
        val r = a("quitar la mancha del pantalón")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Quitar la mancha del pantalón", r.title)
    }

    @Test fun quitarUnaManchaDeVinoDelVestido_hit() {
        val r = a("quitar una mancha de vino del vestido")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Quitar una mancha de vino del vestido", r.title)
    }

    @Test fun temporalPrefixQuitarLaMancha_hizada() {
        val r = a("mañana quitar la mancha de grasa de la sudadera")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Quitar la mancha de grasa de la sudadera", r.title)
    }

    @Test fun politePrefixQuitarLaMancha_hit() {
        val r = a("por favor quitar la mancha")
        Assert.assertNotNull(r)
        Assert.assertEquals("Quitar la mancha", r!!.title)
    }

    @Test fun futuroQuitare_pin() {
        // Futuro «quitaré» no casa con el infinitivo del piso — mismo
        // canario que «la quitaré» c.1212 (lateral ABIERTA explícita).
        val r = a("vale, quitaré la mancha del sofá")
        Assert.assertNull(r)
    }

    @Test fun pastQuite_null() {
        val r = a("quité la mancha ayer")
        Assert.assertNull(r)
    }

    @Test fun negatedNoQuitar_null() {
        val r = a("no quitar la mancha todavía")
        Assert.assertNull(r)
    }

    @Test fun declarativeMancha_null() {
        val r = a("la mancha de tinta no salió")
        Assert.assertNull(r)
    }

    @Test fun dudaSubjuntivoQuitareComicKind_null() {
        val r = a("quitaré la mancha mañana si recuerdo")
        Assert.assertNull(r)
    }

    @Test fun regresionQuitarLaMesa_hizada() {
        // Piso hermano c.754 (objeto disjunto) se mantiene byte-idéntico.
        val r = a("quitar la mesa")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Quitar la mesa", r.title)
    }

    @Test fun regresionQuitarElPolvo_hizada() {
        // Piso hermano c.732 (objeto disjunto) se mantiene byte-idéntico.
        val r = a("quitar el polvo")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Quitar el polvo", r.title)
    }

    @Test fun regresionCoserElBoton_hizada() {
        // Región ropa vecina (c.1217): verbo/objeto disjunto.
        val r = a("coser el botón")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Coser el botón", r.title)
    }
}
