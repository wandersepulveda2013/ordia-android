package com.ordia.app.context

import org.junit.Assert
import org.junit.Test

/**
 * TDD pin del ciclo c.1215: lateral ABIERTA del hermano c.1211 (auditoría
 * clase VIGESIMOSÉPTIMA) — piso `HOUSEHOLD_PLANTING_FLOOR`
 * (`plantar (los|las|el|la|un|mi|tu|su) + cultivo`). «plantar» es
 * monosemántico de jardinería, verbo keyword hermano estructural de
 * «podar» (c.748/c.1211) — gate c.751 satisfecho por el VERBO;
 * objetos cerrados (tomates/árboles/orquídeas/jardín/menta/tomillo/
 * hierbabuena). Sonda PRE/POST `tools/probe/PlantarCultivoProbe.kt`:
 * NULL→HIT medido.
 */
class ContextIntentEnginePlantarCultivoFloorTest {

    private fun a(text: String) =
        ContextIntentEngine.analyze(ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L))

    @Test fun plantarLosTomates_hit() {
        val r = a("plantar los tomates")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertTrue(r.confidence >= 0.45f)
        Assert.assertEquals("Plantar los tomates", r!!.title)
    }

    @Test fun plantarLaOrquidea_hit() {
        val r = a("plantar la orquídea")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Plantar la orquídea", r!!.title)
    }

    @Test fun plantarUnArbol_indefinidoAlternancia_hit() {
        val r = a("plantar un árbol")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Plantar un árbol", r!!.title)
    }

    @Test fun temporalPrefixPlantarLosTomates_hit() {
        val r = a("mañana plantar los tomates")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Plantar los tomates", r!!.title)
    }

    @Test fun politePrefixPlantarHierbabuena_hit() {
        val r = a("por favor plantar la hierbabuena")
        Assert.assertNotNull(r)
        Assert.assertEquals("Plantar la hierbabuena", r!!.title)
    }

    @Test fun pluralOrquideas_hit() {
        val r = a("plantar las orquídeas")
        Assert.assertNotNull(r)
        Assert.assertEquals("Plantar las orquídeas", r!!.title)
    }

    @Test fun futuroPlantareLateral_pin() {
        // Lateral ABIERTA explícita (TDD): «plantaré» (futuro) no casa con
        // el infinitivo del piso — mismo canario que «quitaré» c.1214.
        val r = a("plantaré los tomates el sábado")
        Assert.assertNull(r)
    }

    @Test fun negatedNoPlantar_null() {
        val r = a("no plantar los tomates")
        Assert.assertNull(r)
    }

    @Test fun declarativeTomates_null() {
        // Guard de verbo declarativo: la keyword sola («tomate» a 0.12)
        // queda bajo el umbral.
        val r = a("los tomates no gustan a los caracoles")
        Assert.assertNull(r)
    }

    @Test fun plantarSinObjetoAcotado_null() {
        // Guard anti-bivalencia: «plantar» sin objeto de la lista cerrada
        // no casa (familia de pisos por objeto disjunto).
        val r = a("vamos a plantar mañana en la huerta")
        Assert.assertNull(r)
    }

    @Test fun podarLosSetos_regressionNoCollision() {
        val r = a("podar los setos")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Podar los setos", r!!.title)
    }

    @Test fun quitarLasMalasHierbas_regressionNoCollision() {
        val r = a("quitar las malas hierbas")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Quitar las malas hierbas", r!!.title)
    }

    @Test fun cortarElCesped_regressionNoCollision() {
        val r = a("cortar el césped")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Cortar el césped", r!!.title)
    }
}
