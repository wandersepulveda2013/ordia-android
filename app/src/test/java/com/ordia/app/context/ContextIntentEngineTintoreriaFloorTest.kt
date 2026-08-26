package com.ordia.app.context

import org.junit.Assert
import org.junit.Test

/**
 * TDD pin de la lateral (c) de la auditoría clase VIGESIMOCTAVA
 * (ROPA/VESTIMENTA, c.1209): «llevar (el/la/… <objeto> a)? (la)?
 * tintorer[ií]a(s?)» — dirección entrega (drop-off) del dry cleaner,
 * NULL PRE medido con sonda efímera mientras la dirección recogida
 * («recoger la tintorería», [ERRAND_VERBS] c.639) ya capturaba.
 * Piso [ERRAND_DRYCLEAN_FLOOR] (objeto opcional, destino acotado —
 * lección c.684: llevar suelto es bivalente) + keyword-OBJETO
 * «tintorería» (lockstep c.751, 0.12 sola bajo umbral) + plantilla
 * matchDryclean (lockstep c.616, verbo preservado — doctrina c.653).
 */
class ContextIntentEngineTintoreriaFloorTest {

    private fun a(text: String) =
        ContextIntentEngine.analyze(ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L))

    private fun hit(text: String, expectedTitle: String) {
        val r = a(text)
        Assert.assertNotNull("«$text» debería capturar", r)
        Assert.assertEquals(ContextIntentKind.ERRAND, r!!.kind)
        Assert.assertTrue(r.confidence >= 0.45f)
        Assert.assertEquals(expectedTitle, r.title)
    }

    @Test fun llevarElTrajeATintoreria_hit() =
        hit("llevar el traje a la tintorería", "Llevar el traje a la tintorería")

    @Test fun llevarLaCamisaATintoreriaTemporal_hit() {
        val r = a("llevar la camisa a la tintorería mañana")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.ERRAND, r!!.kind)
        Assert.assertEquals("Llevar la camisa a la tintorería", r.title)
        Assert.assertNotNull("«mañana» debe resolver dueAt", r.dueAt)
    }

    @Test fun llevarLosVestidosATintoreria_hit() =
        hit("llevar los vestidos a la tintorería", "Llevar los vestidos a la tintorería")

    @Test fun llevarMiUniformeALaTintoreria_hit() =
        hit("llevar mi uniforme a la tintorería", "Llevar mi uniforme a la tintorería")

    @Test fun llevarATintoreriaObjetoDesnudo_hit() =
        hit("llevar a la tintorería", "Llevar a la tintorería")

    @Test fun llevarATintoreriaElViernes_hit() {
        val r = a("llevar a la tintorería el viernes")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.ERRAND, r!!.kind)
        Assert.assertEquals("Llevar a la tintorería", r.title)
        Assert.assertNotNull(r.dueAt)
    }

    @Test fun temporalPrefixLlevarATintoreria_hit() {
        // Prefijo temporal: el match arranca en el verbo, así el acuse no
        // ensucia el título (lección c.616).
        val r = a("mañana llevar el traje a la tintorería")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.ERRAND, r!!.kind)
        Assert.assertEquals("Llevar el traje a la tintorería", r.title)
        Assert.assertNotNull(r.dueAt)
    }

    // ---------------- guards: NULL ----------------

    @Test fun negacionGuard_null() {
        Assert.assertNull(a("no llevar a la tintorería"))
    }

    @Test fun preteritoGuard_null() {
        Assert.assertNull(a("ya lo llevé a la tintorería"))
    }

    @Test fun verbAlone_null() {
        Assert.assertNull(a("llevar"))
    }

    @Test fun declarativoTintoreriaKeywordSola_null() {
        // keyword-OBJETO 0.12 sola queda bajo el umbral (lección c.751).
        Assert.assertNull(a("la tintorería me llamó"))
    }

    @Test fun destinoVariante_null() {
        Assert.assertNull(a("tener en tintorería"))
    }

    // ---------------- regresiones ----------------

    @Test fun recogidaRecogerLaTintoreria_regresion() {
        // Dirección recogida: HIT heredado intacto (nos asegura que el
        // lockstep no robó el camino ascendente via keyword).
        val r = a("recoger la tintorería")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.ERRAND, r!!.kind)
        Assert.assertEquals("Recoger la tintorería", r.title)
    }

    @Test fun recogidaConObjeto_regresion() {
        val r = a("recoger la camisa de la tintorería")
        Assert.assertNotNull(r)
        Assert.assertEquals(ContextIntentKind.ERRAND, r!!.kind)
        Assert.assertEquals("Recoger la camisa de la tintorería", r.title)
    }

    @Test fun destinoHermanosNoRobado_regresion() {
        // Otros pisos/destinos no robados por el objeto opcional.
        val r = a("llevar el gato al veterinario")
    // PIN FUERA: el destino veterinario tiene piso propio o NULL base;
    // aquí medimos PRE NULL y exigimos que el nuevo piso no lo robe.
        Assert.assertNull(r)
    }
}
