package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lateral ABIERTA del piso c.1140 «hacer el check-in del vuelo» (clase
 * DECIMOSEXTA viajes): objeto «hotel». El piso c.1140 pinó «check-in
 * del hotel» FUERA como lateral medida NULL (UNA forma por ciclo) y
 * BACKLOG la mantenía ABIERTA; sonda efímera PRE
 * `/tmp/CheckInHotelPreProbe.kt` midió 4 misses EXACTOS (C1-C4) con
 * guards/pines NULL y regresiones HIT. Mismo coste real que el vuelo:
 * check-in de hotel olvidado = no-show/habitación perdida.
 *
 * Fix ACOTADO: objeto «(?:vuelo|hotel)» en el MISMO piso de
 * [ContextIntentEngine.hasStrongTaskImperative] + plantilla
 * `matchHacerCheckIn` lockstep (lección c.616). CERO keywords nuevas:
 * «hacer» keyword TASK y «hotel» keyword TRAVEL ya existen (gate
 * c.751 — medido PRE: TRAVEL 0.12 + bono 0.1 = 0.22 < umbral sin
 * piso). Grafía del usuario preservada («checkin»/«check in»,
 * doctrina c.653). Re-pin documentado del guard «lateral check-in del
 * hotel no captura» (precedente c.1168 maleta) en
 * [ContextIntentEngineFacturarVueloFloorTest].
 */
class ContextIntentEngineCheckInHotelFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ---------- Capturas ----------

    @Test
    fun `hacer el check-in del hotel captura TASK con titulo limpio y fecha`() {
        val r = analyze("hacer el check-in del hotel mañana")
        assertNotNull("«hacer el check-in del hotel mañana» debe capturar (era NULL pin de c.1140)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Hacer el check-in del hotel", r.title)
        assertTrue("debe resolver la fecha de «mañana»", r.dueAt != null)
    }

    @Test
    fun `grafia checkin sin guion captura preservando la grafia`() {
        val r = analyze("hacer el checkin del hotel el jueves")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Hacer el checkin del hotel", r.title)
        assertTrue(r.dueAt != null)
    }

    @Test
    fun `grafia check in con espacio captura preservando la grafia`() {
        val r = analyze("hacer el check in del hotel esta noche")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Hacer el check in del hotel", r.title)
    }

    @Test
    fun `acuse ok no ensucia el titulo`() {
        val r = analyze("ok, hacer el check-in del hotel")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Hacer el check-in del hotel", r.title)
    }

    @Test
    fun `prefijo temporal no ensucia el titulo`() {
        val r = analyze("el jueves hacer el check-in del hotel")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Hacer el check-in del hotel", r.title)
    }

    // ---------- Guards (deben seguir NULL) ----------

    @Test
    fun `negacion no hacer el check-in del hotel no captura`() {
        assertNull(analyze("no hacer el check-in del hotel"))
    }

    @Test
    fun `pasado hice el check-in del hotel no captura`() {
        assertNull(analyze("hice el check-in del hotel ayer"))
    }

    @Test
    fun `negacion tras prefijo temporal no captura`() {
        assertNull(analyze("mañana no hacer el check-in del hotel"))
    }

    // ---------- Pines anti-overreach (status quo deliberado) ----------

    @Test
    fun `sin objeto exigido hacer el check-in pelado no captura`() {
        assertNull(analyze("hacer el check-in mañana"))
    }

    @Test
    fun `verbo distinto facturar el hotel no captura`() {
        assertNull(analyze("facturar el hotel mañana"))
    }

    @Test
    fun `otro objeto hacer el check-in del restaurante no captura`() {
        assertNull(analyze("hacer el check-in del restaurante mañana"))
    }

    // ---------- Regresiones (pins previos intactos) ----------

    @Test
    fun `regresion pin c1140 hacer el check-in del vuelo sigue capturando`() {
        val r = analyze("hacer el check-in del vuelo mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Hacer el check-in del vuelo", r.title)
    }

    @Test
    fun `regresion c1140 facturar el vuelo sigue capturando`() {
        val r = analyze("facturar el vuelo mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }

    @Test
    fun `regresion c1168 facturar la maleta sigue capturando`() {
        val r = analyze("facturar la maleta el jueves")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }

    @Test
    fun `regresion reservar el hotel sigue capturando`() {
        val r = analyze("reservar el hotel mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }
}
