package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Piso c.1140 — «facturar el vuelo» / «hacer el check-in del vuelo»:
 * candidata (a) FUERTE de la clase DECIMOSEXTA (viajes/reservas/ocio),
 * medida NULL 2/2 en `tools/probe/SixteenthClassTravelProbe.kt` C3/C4
 * (c.1137) y re-medida PRE sobre HEAD c24b146 con sonda efímera
 * (5/5 candidatas NULL, 11/11 guards NULL, 7/7 regresiones HIT
 * intactas). Sin piso, «facturar el vuelo mañana» se DESCARTABA
 * silenciosamente: «facturar» en sentido aeronáutico no es keyword
 * (bivalente: «facturar el proyecto» mercantil) y la keyword TRAVEL
 * «vuelo» suma 0.12; con bono temporal 0.22 < umbral. Consecuencia
 * real: check-in perdido → recargo o asiento perdido (ventana 24-48 h,
 * el olvido más caro del viaje). Hermano EXACTO de c.865 «reclamar la
 * factura»: verbo bivalente acotado por objeto-ancla.
 *
 * El piso vive en [ContextIntentEngine.hasStrongTaskImperative] y exige:
 * ancla de inicio/acuse/prefijo temporal, guard anti-negación
 * `(?<!no )` y objeto EXIGIDO «vuelo» («facturar el vuelo» |
 * «hacer el check-in del vuelo»). Los bivalentes quedan FUERA:
 * «facturar el proyecto/la maleta», «hacer el check-in del hotel»
 * (laterales medidas NULL, UNA forma por ciclo). Lockstep en DOS puntos
 * (lección c.616; CERO keywords nuevas: «vuelo» ya es keyword TRAVEL y
 * «hacer» keyword TASK — la frase ya llega al análisis, medido PRE).
 * La grafía del usuario se preserva («checkin» sin guion, doctrina
 * c.653).
 *
 * Kind decidido: TASK — gestión previa al viaje, hermana de «reservar»
 * c.709, «confirmar» c.700, «imprimir las tarjetas» c.708 y «preparar
 * la maleta» c.715 (todas TASK); la envolvente «recuérdame hacer el
 * check-in del vuelo» ya rutea TASK vía candado c.613 → convergencia
 * de kind. TRAVEL no tiene piso y su keyword 0.12 + bono 0.1 = 0.22
 * queda bajo el umbral: no compite.
 *
 * Cobertura: 5 capturas (facturar+fecha, check-in+fecha, acuse «ok,»,
 * prefijo temporal «el jueves», grafía «checkin») + 11 guards
 * (negación, negación tras temporal, duda subjuntivo, pasado
 * «facturé/hice», bivalente mercantil, lateral «maleta», lateral
 * «hotel», sustantivo «la facturación», declarativo «el vuelo sale…»,
 * negación check-in) + 8 regresiones (envolventes c.613, reservar
 * vuelo c.709, imprimir tarjetas c.708, reclamar factura c.865,
 * preparar maleta, cancelar, «ir al aeropuerto» NULL status quo).
 */
class ContextIntentEngineFacturarVueloFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ---------- Capturas ----------

    @Test
    fun `facturar el vuelo manana captura TASK con titulo limpio`() {
        val r = analyze("facturar el vuelo mañana")
        assertNotNull("«facturar el vuelo mañana» debe capturar (era NULL en c.1137)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Facturar el vuelo", r.title)
        assertTrue("debe resolver la fecha de «mañana»", r.dueAt != null)
    }

    @Test
    fun `hacer el check-in del vuelo captura TASK con titulo limpio`() {
        val r = analyze("hacer el check-in del vuelo mañana por la mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Hacer el check-in del vuelo", r.title)
        assertTrue(r.dueAt != null)
    }

    @Test
    fun `acuse ok no ensucia el titulo`() {
        val r = analyze("ok, facturar el vuelo esta noche")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Facturar el vuelo", r.title)
    }

    @Test
    fun `prefijo temporal el jueves no ensucia el titulo`() {
        val r = analyze("el jueves hacer el check-in del vuelo")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Hacer el check-in del vuelo", r.title)
    }

    @Test
    fun `grafia checkin sin guion captura preservando la grafia del usuario`() {
        val r = analyze("hacer el checkin del vuelo mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Hacer el checkin del vuelo", r.title)
    }

    // ---------- Guards (deben seguir NULL) ----------

    @Test
    fun `negacion no facturar el vuelo no captura`() {
        assertNull(analyze("no facturar el vuelo mañana"))
    }

    @Test
    fun `negacion tras prefijo temporal no captura`() {
        assertNull(analyze("mañana no facturar el vuelo"))
    }

    @Test
    fun `duda subjuntivo no captura`() {
        assertNull(analyze("quizá facture el vuelo mañana"))
    }

    @Test
    fun `pasado facture el vuelo no captura`() {
        assertNull(analyze("facturé el vuelo ayer"))
    }

    @Test
    fun `pasado hice el check-in no captura`() {
        assertNull(analyze("hice el check-in del vuelo ayer"))
    }

    @Test
    fun `bivalente mercantil facturar el proyecto no captura`() {
        assertNull(analyze("facturar el proyecto al cliente mañana"))
    }

    @Test
    fun `lateral facturar la maleta no captura`() {
        assertNull(analyze("facturar la maleta mañana"))
    }

    @Test
    fun `lateral check-in del hotel no captura`() {
        assertNull(analyze("hacer el check-in del hotel mañana"))
    }

    @Test
    fun `sustantivo la facturacion no captura`() {
        assertNull(analyze("la facturación del vuelo abre a las 5"))
    }

    @Test
    fun `declarativo el vuelo sale no captura`() {
        assertNull(analyze("el vuelo sale el martes a las 6"))
    }

    @Test
    fun `negacion check-in no captura`() {
        assertNull(analyze("no hacer el check-in del vuelo"))
    }

    // ---------- Regresiones (status quo preservado) ----------

    @Test
    fun `envolvente tengo que facturar el vuelo sigue capturando`() {
        val r = analyze("tengo que facturar el vuelo")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Facturar el vuelo", r.title)
    }

    @Test
    fun `envolvente recuerdame hacer el check-in sigue capturando`() {
        val r = analyze("recuérdame hacer el check-in del vuelo mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Hacer el check-in del vuelo", r.title)
    }

    @Test
    fun `reservar el vuelo sigue capturando`() {
        val r = analyze("reservar el vuelo mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Reservar el vuelo", r.title)
    }

    @Test
    fun `imprimir las tarjetas de embarque sigue capturando`() {
        val r = analyze("imprimir las tarjetas de embarque esta noche")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Imprimir las tarjetas de embarque", r.title)
    }

    @Test
    fun `reclamar la factura del vuelo sigue capturando`() {
        val r = analyze("reclamar la factura del vuelo")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Reclamar la factura del vuelo", r.title)
    }

    @Test
    fun `preparar la maleta sigue capturando`() {
        val r = analyze("preparar la maleta esta noche")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Preparar la maleta", r.title)
    }

    @Test
    fun `cancelar el vuelo sigue capturando`() {
        val r = analyze("cancelar el vuelo mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Cancelar el vuelo", r.title)
    }

    @Test
    fun `ir al aeropuerto sigue sin capturar (lateral registrada)`() {
        assertNull(analyze("ir al aeropuerto"))
    }
}
