package com.ordia.app.context

import org.junit.Assert
import org.junit.Test

/**
 * TDD pin de la lateral (e) de la auditoría clase VIGESIMOCTAVA ROPA
 * (c.1209): piso [HOUSEHOLD_TIDY_CLOTHES_FLOOR] (`guardar (la|las)? ropa
 * (en el armario/…)`), familia «ropa» por objeto disjunto (colgar c.743 /
 * lavar+lavado c.731 / doblar c.1209 / coser-botón c.1217 / guardar aquí).
 * Sonda PRE/POST `tools/probe/GuardarRopaProbe.kt`: NULL→HIT medido.
 */
class ContextIntentEngineGuardarRopaFloorTest {

    private fun a(text: String) =
        ContextIntentEngine.analyze(ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L))

    @Test fun guardarLaRopa_hit() {
        val r = a("guardar la ropa")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Guardar la ropa", r.title)
    }

    @Test fun guardarLaRopaEnElArmario_hit() {
        val r = a("guardar la ropa en el armario")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Guardar la ropa en el armario", r.title)
    }

    @Test fun temporalPrefixGuardarLaRopa_hizada() {
        val r = a("mañana guardar la ropa del pasillo")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Guardar la ropa del pasillo", r.title)
    }

    @Test fun politePrefixGuardarLaRopa_hit() {
        val r = a("por favor guardar la ropa")
        Assert.assertNotNull(r)
        Assert.assertEquals("Guardar la ropa", r!!.title)
    }

    @Test fun pastGuarde_null() {
        val r = a("guardé la ropa ayer")
        Assert.assertNull(r)
    }

    @Test fun negatedNoGuardar_null() {
        val r = a("no guardar la ropa todavía")
        Assert.assertNull(r)
    }

    @Test fun bivalentDocumentos_null() {
        // «guardar documentos/archivo» es TI (digital), no HOUSEHOLD —
        // la keyword-OBJETO «ropa» hace el piso objeto-específico (c.753).
        val r = a("guardar documentos en la nube")
        Assert.assertNull(r)
    }

    @Test fun bivalentArchivo_null() {
        val r = a("guardar el archivo pdf")
        Assert.assertNull(r)
    }

    @Test fun regresionColgarLaRopa_hizada() {
        // Piso hermano c.743 (misma keyword-OBJETO) intacto.
        val r = a("colgar la ropa")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Colgar la ropa", r.title)
    }

    @Test fun regresionLavarLaRopa_hizada() {
        val r = a("lavar la ropa")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Lavar la ropa", r.title)
    }

    @Test fun regresionCoserElBoton_hizada() {
        // Región ropa vecina (c.1217): verbo/objeto disjunto.
        val r = a("coser el botón")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Coser el botón", r.title)
    }

    @Test fun declarativeRopa_null() {
        val r = a("la ropa de invierno ocupa todo el armario")
        Assert.assertNull(r)
    }
}
