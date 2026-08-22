package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Piso c.865 — «reclamar la factura (del banco/de la luz…)»: séptimo y
 * último gap medido NULL en c.857 por `tools/probe/EighthClassAdminProbe.kt`
 * (octava clase: gestiones de la vida adulta — finanzas del hogar). Sin
 * piso, «reclamar la factura del banco mañana» se DESCARTABA
 * silenciosamente: el verbo «reclamar» es bivalente (el premio/el turno/
 * al camarero — nunca keyword) y la keyword «factura» (lista junto a
 * «pagar»/«recibo») suma 0.12; con bono temporal 0.22 < umbral (medición
 * PRE c.865 sobre HEAD 52371cf: 6/6 declarativas NULL). Consecuencia real:
 * un cobro indebido del banco/la luz que no se reclama a tiempo.
 * Paradoja medida en c.857: «revisar la factura» HIT (piso propio) y
 * «pagar la factura» PAYMENT, pero «reclamar la factura» NULL.
 *
 * El piso vive en [ContextIntentEngine.hasStrongTaskImperative] y exige:
 * ancla de inicio/acuse/prefijo temporal, guard anti-negación `(?<!no )`
 * y objeto acotado a «facturas?» con determinante/posesivo opcional
 * (el/la/los/las/mi/tu/su — patrón hermano c.860/c.863/c.864). Los
 * bivalentes quedan FUERA: «reclamar el premio…», «reclamar al banco…»
 * (sin objeto factura), medidos NULL en la sonda PRE. Lockstep en DOS
 * puntos (lección c.616; CERO cambios en ContextIntent.kt: la keyword
 * «factura» ya lleva la frase al análisis, hermana de c.860/c.862/c.863
 * — a diferencia de c.864 no hace falta keyword nueva). Lateral medida
 * NULL registrada como candidata propia: «reclamar una factura…»
 * (indefinido — el patrón hermano se acota a determinante definido/
 * posesivo).
 *
 * Kind decidido: TASK, en deliberación contra PAYMENT/ERRAND/CALL —
 * reclamar un cobro es una gestión (escrita o telefónica, sin
 * desplazamiento), hermana de «responder el correo» c.860 y distinta de
 * «pagar la factura» (PAYMENT: es el sentido contrario del dinero); la
 * envolvente «recuérdame reclamar la factura del banco mañana» ya rutea
 * TASK vía candado c.613 → convergencia de kind (asimetría de ruta
 * hermana de c.765…c.864).
 *
 * Cobertura: 6 capturas (fecha, otro emisor, acuse «vale,», prefijo
 * temporal, desnuda, plural) + 7 guards (negación, duda, pasado, verbo
 * aislado, sustantivo «la reclamación…», bivalente «el premio», «al
 * banco» sin factura) + 4 regresiones (revisar factura, pagar factura
 * PAYMENT, envolvente c.613, escanear DNI c.864).
 */
class ContextIntentEngineReclamarFacturaFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    @Test
    fun `reclamar la factura del banco manana captura TASK con titulo limpio`() {
        val r = analyze("reclamar la factura del banco mañana")
        assertNotNull("«reclamar la factura del banco mañana» debe capturar (era NULL en c.857)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Reclamar la factura del banco", r.title)
    }

    @Test
    fun `reclamar la factura de la luz captura TASK`() {
        val r = analyze("reclamar la factura de la luz esta tarde")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Reclamar la factura de la luz", r.title)
    }

    @Test
    fun `acuse vale no ensucia el titulo`() {
        val r = analyze("vale, reclamar la factura del banco")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Reclamar la factura del banco", r.title)
    }

    @Test
    fun `prefijo temporal delantero captura y se despoja`() {
        val r = analyze("mañana reclamar la factura del banco")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Reclamar la factura del banco", r.title)
    }

    @Test
    fun `forma desnuda sin fecha captura TASK`() {
        val r = analyze("reclamar la factura")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Reclamar la factura", r.title)
    }

    @Test
    fun `plural las facturas captura TASK`() {
        val r = analyze("reclamar las facturas mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Reclamar las facturas", r.title)
    }

    @Test
    fun `negacion no captura`() {
        assertNull(analyze("no reclamar la factura del banco"))
    }

    @Test
    fun `duda quiza no captura`() {
        assertNull(analyze("quizá reclamar la factura del banco mañana"))
    }

    @Test
    fun `pasado narrativo no captura`() {
        assertNull(analyze("reclamé la factura del banco ayer"))
    }

    @Test
    fun `verbo aislado no captura`() {
        assertNull(analyze("reclamar"))
    }

    @Test
    fun `sustantivo la reclamacion no captura`() {
        assertNull(analyze("la reclamación de la factura está en curso"))
    }

    @Test
    fun `bivalente reclamar el premio no captura`() {
        assertNull(analyze("reclamar el premio mañana"))
    }

    @Test
    fun `reclamar al banco sin objeto factura no captura`() {
        assertNull(analyze("reclamar al banco mañana"))
    }

    @Test
    fun `regresion revisar la factura sigue TASK`() {
        val r = analyze("revisar la factura de la luz esta noche")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }

    @Test
    fun `regresion pagar la factura sigue PAYMENT`() {
        val r = analyze("pagar la factura mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.PAYMENT, r!!.kind)
    }

    @Test
    fun `regresion envolvente recuerdame sigue TASK`() {
        val r = analyze("recuérdame reclamar la factura del banco mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }

    @Test
    fun `regresion escanear el DNI c864 sigue TASK`() {
        val r = analyze("escanear el DNI esta tarde")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }
}
