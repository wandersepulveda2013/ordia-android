package com.ordia.app.context

import org.junit.Assert
import org.junit.Test

/**
 * TDD pin de la lateral D15 de la auditoría clase VIGESIMOSÉPTIMA
 * jardinería (c.1211): piso [HOUSEHOLD_FURNITURE_FLOOR]
 * (`sacar (el|los|mis|tus|sus)? mueble(s) (a la terraza)`), nuevo piso
 * de la familia «sacar» por objeto disjunto (basura c.717 / perro-gato
 * c.740 / dinero c.893 / muebles aquí — keyword-OBJETO «mueble» NUEVA
 * gate c.751, lockstep TRES puntos c.1224). Sonda PRE/POST
 * `tools/probe/SacarMueblesProbe.kt`: NULL→HIT medido. Última lateral
 * abierta de la audit c.1211 (D14 «cubrir plantas» reclamada en c.1223).
 */
class ContextIntentEngineSacarMueblesFloorTest {

    private fun a(text: String) =
        ContextIntentEngine.analyze(ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L))

    @Test fun sacarLosMueblesALaTerraza_hit() {
        val r = a("sacar los muebles a la terraza")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertTrue(r.confidence >= 0.45f)
        Assert.assertEquals("Sacar los muebles a la terraza", r.title)
    }

    @Test fun sacarLosMueblesPelao_hit() {
        val r = a("sacar los muebles")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Sacar los muebles", r.title)
    }

    @Test fun sacarElMuebleALaTerraza_hit() {
        val r = a("sacar el mueble a la terraza")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Sacar el mueble a la terraza", r.title)
    }

    @Test fun sacarMisMueblesPosesivo_hit() {
        val r = a("sacar mis muebles")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Sacar mis muebles", r.title)
    }

    @Test fun sacarTusSusMueblesPosesivo_hit() {
        val t1 = a("sacar tus muebles")
        Assert.assertNotNull(t1)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, t1!!.kind)
        Assert.assertEquals("Sacar tus muebles", t1.title)
        val t2 = a("sacar sus muebles")
        Assert.assertNotNull(t2)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, t2!!.kind)
        Assert.assertEquals("Sacar sus muebles", t2.title)
    }

    @Test fun temporalPrefixSacarLosMuebles_hizada() {
        val r = a("mañana sacar los muebles a la terraza")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        Assert.assertEquals("Sacar los muebles a la terraza", r.title)
    }

    @Test fun politePrefixSacarLosMuebles_hit() {
        val r = a("por favor sacar los muebles")
        Assert.assertNotNull(r)
        Assert.assertEquals("Sacar los muebles", r!!.title)
    }

    @Test fun pastSaque_null() {
        // Pasado e-ódo («saqué») no casa con el infinitivo — mismo
        // canario antidrift que «quité» c.1221 / «colgué» c.743.
        val r = a("saqué los muebles de la terraza")
        Assert.assertNull(r)
    }

    @Test fun negatedNoSacar_null() {
        val r = a("no sacar los muebles todavía")
        Assert.assertNull(r)
    }

    @Test fun declarativeMuebles_null() {
        // Keyword-OBJETO «mueble» sola (0,12) inerte <0,45 — MURO c.748.
        val r = a("los muebles están en la terraza")
        Assert.assertNull(r)
    }

    @Test fun futuroSacare_null() {
        // Futuro e-édo («sacaré») bivalente en galimatías temporal —
        // pin canario de c.1221/futuroQuitare.
        val r = a("sacaré los muebles mañana si recuerdo")
        Assert.assertNull(r)
    }

    @Test fun objetoDisjuntoJueguetes_null() {
        // «sacar los juguetes a la terraza»: objeto disjunto (juguete no
        // es mueble) — responsabilidad del piso de niños/visados (no
        // añadido a la alternancia). Null-safety guard.
        val r = a("sacar los juguetes a la terraza")
        Assert.assertNull(r)
    }

    @Test fun regresionSacarAlPerro_hizada() {
        // Piso hermano c.740 «sacar al perro» se mantiene byte-idéntico.
        val r = a("sacar al perro")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
    }

    @Test fun regresionSacarLaBasura_hizada() {
        // Piso hermano c.717 «sacar la basura» (objeto disjunto).
        val r = a("sacar la basura")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
    }

    @Test fun regresionSacarDinero_hizada() {
        // Piso hermano c.893 «sacar dinero» (objeto disjunto — no es
        // HOUSEHOLD pero sigue vivo tras el lockstep).
        val r = a("sacar dinero")
        Assert.assertNotNull(r)
    }

    @Test fun quitarLaHierba_hizada() {
        // Región vecina (verbo/objeto disjuntos) c.1212/c.1219.
        val r = a("quitar la hierba")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
    }
}
