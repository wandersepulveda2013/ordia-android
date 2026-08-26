package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lateral ABIERTA c.1208 — plural PELADO «sacar billetes» (sin
 * artículo): la forma natural de la reserva (de tren/bus/avión)
 * que se quedaba NULL en el parentesis heredado del cierre
 * c.1201→c.1203 del hermano (que exigía «(el|los) billete(s)»).
 * PRE efímera `/tmp/pre1208/PreProbe.kt` (base remoto 7589c51f):
 * 5/5 NULL candidatas peladas, guards 6/6 NULL, regresiones 6/6
 * HIT. Fix hermano del c.1203 en TRES puntos (lección c.616,
 * gate c.751 — keyword-frase pelada NUEVA, CERO verbos sueltos):
 *
 *  1. Keyword-frase «sacar billetes» en ContextIntent.kt TASK
 *     (tercera hermana de «sacar el billete»/«sacar los billetes»).
 *  2. Piso hermano ampliado: determinante `(?:el|los)\s+` hecho
 *     OPCIONAL para capturar el pelado plural mayoritario (la
 *     forma desnuda sin artículo, usuaria coloquial). Guard
 *     `(?<!no )` y ancla inicio/acuse/temporal intactos.
 *  3. Plantilla matchSacarBillete MISMA ampliación (título
 *     «Sacar billetes»; calificador opcional «de|del <→bus>»).
 *
 * Anti-overreach: el bivalente «sacar el pasaporte» y la lateral
 * «sacar la entrada» siguen NULL deliberado (documentados en la
 * suite como ABIERTA por diseño). Guards de negación/pasado/duda
 * intactos. Kinds hermanos intactos (basura c.717, perro c.740,
 * dinero c.893, cita c.1117, visado c.1151).
 */
class ContextIntentEngineSacarBilletesPeladoTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ---------- Capturas (pelado plural) ----------

    @Test
    fun `sacar billetes manana captura TASK con titulo limpio y dueAt`() {
        val r = analyze("sacar billetes mañana")
        assertNotNull("«sacar billetes mañana» debe capturar (era NULL en PRE)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Sacar billetes", r.title)
        assertTrue("debe resolver «mañana»", r.dueAt != null)
    }

    @Test
    fun `sacar billetes desnuda captura TASK`() {
        val r = analyze("sacar billetes")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Sacar billetes", r.title)
    }

    @Test
    fun `temporal prefija sacar billetes captura TASK`() {
        val r = analyze("mañana sacar billetes")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Sacar billetes", r.title)
        assertTrue(r.dueAt != null)
    }

    @Test
    fun `acuse vale sacar billetes manana captura TASK`() {
        val r = analyze("vale, sacar billetes mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Sacar billetes", r.title)
        assertTrue(r.dueAt != null)
    }

    @Test
    fun `sacar billetes para el tren manana captura TASK con dueAt`() {
        val r = analyze("sacar billetes para el tren mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Sacar billetes", r.title)
        assertTrue(r.dueAt != null)
    }

    // ---------- Guards (objetivo: NULL siempre) ----------

    @Test
    fun `negacion pelado no sacar billetes null`() {
        assertNull(analyze("no sacar billetes todavía"))
    }

    @Test
    fun `conjugado negado no saques billetes null`() {
        assertNull(analyze("no saques billetes todavía"))
    }

    @Test
    fun `pasado saque billetes null`() {
        assertNull(analyze("saqué billetes ayer"))
    }

    @Test
    fun `nominal singular el billete cuesta null`() {
        assertNull(analyze("el billete cuesta 50 euros"))
    }

    @Test
    fun `bivalente pasaporte sigue null deliberado`() {
        assertNull(analyze("sacar el pasaporte antes del vuelo"))
    }

    @Test
    fun `lateral abierta sacar la entrada sigue null documentado`() {
        assertNull(analyze("sacar la entrada mañana"))
    }

    // ---------- Pines (siblings determinados + otros pisos sacar) ----------

    @Test
    fun `forma determinada hermano sigue hit`() {
        val r = analyze("sacar el billete de tren mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Sacar el billete de tren", r.title)
    }

    @Test
    fun `plural determinado hermano sigue hit`() {
        val r = analyze("sacar los billetes del tren mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Sacar los billetes del tren", r.title)
    }

    @Test
    fun `sacar basura household intacto`() {
        val r = analyze("sacar la basura mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
    }

    @Test
    fun `sacar al perro household intacto`() {
        val r = analyze("sacar al perro mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
    }

    @Test
    fun `sacar dinero errand intacto`() {
        val r = analyze("sacar dinero mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.ERRAND, r!!.kind)
    }

    @Test
    fun `sacar el visado task intacto`() {
        val r = analyze("sacar el visado antes del viaje")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }
}
