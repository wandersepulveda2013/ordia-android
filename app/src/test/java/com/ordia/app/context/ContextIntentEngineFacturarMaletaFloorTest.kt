package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Piso c.1168 — lateral de c.1140: «facturar la maleta» / «facturar
 * las maletas» (clase DECIMOSEXTA viajes, listado como lateral abierta
 * en el propio bloque c.1140: «facturar el proyecto/la maleta … quedan
 * FUERA (laterales medidas NULL, UNA forma por ciclo)»). Re-medida PRE
 * con sonda efímera (motor real vía `tools/run_probe.sh`) sobre
 * `488bfe6`: 4/4 desnudas NULL («facturar la maleta mañana», «facturar
 * las maletas el lunes por la mañana», acuse «vale, facturar la maleta
 * antes del viernes», pelada), envolvente «tengo que facturar la
 * maleta hoy» TASK 0.49 por el camino genérico (título ya correcto),
 * guards 4/4 NULL, regresiones 4/4 HIT. Consecuencia real: facturar el
 * equipaje olvidado → cola/recargo de maleta en el aeropuerto (mismo
 * coste que el check-in c.1140: ventana corta y cargo directo).
 *
 * Fix quirúrgico en LOCKSTEP de dos puntos (lección c.616): el piso
 * c.1140 en [ContextIntentEngine.hasStrongTaskImperative] extiende su
 * objeto EXIGIDO «el vuelo» con la alternativa «las? maletas?» y la
 * plantilla `matchFacturarVuelo` captura el mismo grupo. Misma ancla
 * (inicio/acuse/prefijo temporal), mismo guard `(?<!no )` heredado,
 * CERO keywords nuevas (la frase ya llega al análisis: envolvente
 * medida TASK 0.49 PRE; gate c.751 satisfecho). El bivalente
 * mercantil «facturar el proyecto» sigue FUERA y la lateral «hacer el
 * check-in del hotel» sigue abierta (UNA forma por ciclo).
 *
 * Cobertura: 5 capturas (mañana, plural+lunes, acuse «vale,», pelada,
 * prefijo temporal «el viernes») + 5 guards (negación, negación tras
 * temporal, pasado «facturé…ayer», duda, pasiva «está facturada») +
 * 5 pines (vuelo c.1140 intacto, check-in c.1140 intacto, «preparar
 * la maleta» c.715, bivalente mercantil FUERA, lateral hotel FUERA) +
 * 1 envolvente (camino genérico intacto).
 */
class ContextIntentEngineFacturarMaletaFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ---------- Capturas ----------

    @Test
    fun `facturar la maleta manana captura TASK con titulo limpio`() {
        val r = analyze("facturar la maleta mañana")
        assertNotNull("«facturar la maleta mañana» debe capturar (NULL PRE c.1168)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Facturar la maleta", r.title)
        assertTrue("debe resolver la fecha de «mañana»", r.dueAt != null)
    }

    @Test
    fun `facturar las maletas el lunes captura TASK con titulo limpio`() {
        val r = analyze("facturar las maletas el lunes por la mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Facturar las maletas", r.title)
        assertTrue(r.dueAt != null)
    }

    @Test
    fun `acuse vale no ensucia el titulo`() {
        val r = analyze("vale, facturar la maleta antes del viernes")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertTrue("el título debe arrancar en el verbo (acuse despojado)",
            r.title.startsWith("Facturar la maleta"))
    }

    @Test
    fun `forma pelada facturar la maleta captura TASK`() {
        val r = analyze("facturar la maleta")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Facturar la maleta", r.title)
        assertNull("sin temporalidad no hay dueAt", r.dueAt)
    }

    @Test
    fun `prefijo temporal el viernes no ensucia el titulo`() {
        val r = analyze("el viernes facturar la maleta")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Facturar la maleta", r.title)
    }

    // ---------- Guards (deben seguir NULL) ----------

    @Test
    fun `negacion no factures la maleta no captura`() {
        assertNull(analyze("no factures la maleta todavía"))
    }

    @Test
    fun `negacion tras prefijo temporal no captura`() {
        assertNull(analyze("mañana no facturar la maleta"))
    }

    @Test
    fun `pasado facture la maleta ayer no captura`() {
        assertNull(analyze("facturé la maleta ayer"))
    }

    @Test
    fun `duda no captura`() {
        assertNull(analyze("no sé si facturar la maleta"))
    }

    @Test
    fun `pasiva la maleta esta facturada no captura`() {
        assertNull(analyze("la maleta está facturada"))
    }

    // ---------- Pines (cobertura heredada intacta) ----------

    @Test
    fun `regresion facturar el vuelo c1140 sigue capturando`() {
        val r = analyze("facturar el vuelo mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Facturar el vuelo", r.title)
    }

    @Test
    fun `regresion hacer el check-in del vuelo c1140 sigue capturando`() {
        val r = analyze("hacer el check-in del vuelo mañana por la mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Hacer el check-in del vuelo", r.title)
    }

    @Test
    fun `regresion preparar la maleta c715 sigue capturando`() {
        val r = analyze("preparar la maleta esta noche")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Preparar la maleta", r.title)
    }

    @Test
    fun `bivalente mercantil facturar el proyecto sigue fuera`() {
        assertNull(analyze("facturar el proyecto este mes"))
    }

    @Test
    fun `lateral hacer el check-in del hotel sigue fuera`() {
        assertNull(analyze("hacer el check-in del hotel mañana"))
    }

    // ---------- Envolvente (camino genérico intacto) ----------

    @Test
    fun `envolvente tengo que facturar la maleta captura TASK`() {
        val r = analyze("tengo que facturar la maleta hoy")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Facturar la maleta", r.title)
        assertTrue(r.dueAt != null)
    }
}
