package com.ordia.app.context

import org.junit.Assert
import org.junit.Test

/**
 * TDD pin de la lateral (b) ABIERTA de la auditoría clase
 * VIGESIMOCTAVA (ROPA/VESTIMENTA, c.1209): «coser (el|los)? botón(es)».
 * PRE medido con sonda efímera (5/5 targets NULL, 6/6 guards NULL,
 * 2/2 vecinos HIT heredado — doblar/colgar ropa c.1209). Piso
 * [HOUSEHOLD_SEW_BUTTON_FLOOR] (hermano de la familia ROPA) +
 * keyword-OBJETO literal «botón»/«boton» (lección c.751: la tilde
 * rompe la subcadena, así van las dos formas) + plantilla
 * matchSewButton en [extractTitle] (lockstep c.616).
 */
class ContextIntentEngineSewButtonFloorTest {

    private fun a(text: String) =
        ContextIntentEngine.analyze(ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L))

    private fun hit(text: String, expectedTitle: String) {
        val r = a(text)
        Assert.assertNotNull("«$text» debería capturar", r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertTrue(r.confidence >= 0.45f)
        Assert.assertEquals(expectedTitle, r.title)
    }

    @Test fun coserElBoton_hit() =
        hit("coser el botón", "Coser el botón")

    @Test fun coserLosBotones_hit() =
        hit("coser los botones", "Coser los botones")

    @Test fun coserBotonesBare_hit() =
        hit("coser botones", "Coser botones")

    @Test fun temporalPrefixCosersDeCamisa_hit() {
        val r = a("mañana coser el botón de la camisa")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Coser el botón de la camisa", r.title)
        Assert.assertNotNull(r.dueAt)
    }

    @Test fun ackPrefixCosers_hit() =
        hit("vale, coser el botón", "Coser el botón")

    // ---------------- guards: NULL ----------------

    @Test fun negacionGuard_null() = Assert.assertNull(a("no coser el botón"))

    @Test fun verbAlone_null() = Assert.assertNull(a("coser"))

    @Test fun preteritoYaloCosi_null() = Assert.assertNull(a("ya lo cosí"))

    @Test fun declarativoArte_null() = Assert.assertNull(a("coser es un arte"))

    @Test fun keywordObjetoDeclarativo_null() =
        Assert.assertNull(a("el botón de la chaqueta vino suelto"))

    @Test fun preteritoCosio_null() = Assert.assertNull(a("cosió el botón ayer"))

    // ---------------- regresiones (vecinos c.1209 intactos) ----------------

    @Test fun doblarLaRopa_regresion() = hit("doblar la ropa", "Doblar la ropa")

    @Test fun colgarLaRopa_regresion() = hit("colgar la ropa", "Colgar la ropa")
}
