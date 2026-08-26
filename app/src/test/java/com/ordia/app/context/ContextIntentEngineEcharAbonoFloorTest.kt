package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1220 (lateral ABIERTA de la auditoría clase VIGESIMOSÉPTIMA jardinería/plantas c.1211):
 * «echar (el?) (abono|fertilizante) a (tus|mis|las)? (plantas|suculentas|rosales)» — verbo
 * bivalente «echar» acotado a la sustancia (abono|fertilizante), sin piso ni keyword
 * (gate c.751). Lockstep DOS puntos (lección c.616, grafías preservadas c.653):
 * (1) piso `HOUSEHOLD_FERTILIZE_FLOOR` añadido a `HOUSEHOLD_FLOORS`;
 * (2) plantilla canónica `matchEcharAbonoPlantas` (titulación).
 */
class ContextIntentEngineEcharAbonoFloorTest {
    private fun event(t: String) = ContextEvent(
        source = ContextCaptureSource.SHARED_TEXT,
        rawText = t,
        timestampMs = 0L
    )
    private fun analyze(t: String) = ContextIntentEngine.analyze(event(t))

    @Test
    fun `echar abono a las plantas captura HOUSEHOLD`() {
        val intent = analyze("echar abono a las plantas")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent?.kind)
        assertEquals("Echar abono a las plantas", intent?.title)
    }

    @Test
    fun `echar fertilizante a tus plantas captura HOUSEHOLD`() {
        val intent = analyze("echar fertilizante a tus plantas")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent?.kind)
        assertEquals("Echar fertilizante a tus plantas", intent?.title)
    }

    @Test
    fun `echar el abono a los rosales captura HOUSEHOLD`() {
        val intent = analyze("echar el abono a los rosales")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent?.kind)
        assertEquals("Echar el abono a los rosales", intent?.title)
    }

    @Test
    fun `echar con temporal captura y titula limpio`() {
        val intent = analyze("mañana echar abono a las plantas")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent?.kind)
        assertEquals("Echar abono a las plantas", intent?.title)
    }

    @Test
    fun `por favor echar fertilizante a mis plantas captura HOUSEHOLD`() {
        val intent = analyze("por favor echar fertilizante a mis plantas")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent?.kind)
        assertEquals("Echar fertilizante a mis plantas", intent?.title)
    }

    @Test
    fun `echar abono a las suculentas captura HOUSEHOLD`() {
        val intent = analyze("echar abono a las suculentas")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent?.kind)
        assertEquals("Echar abono a las suculentas", intent?.title)
    }

    @Test
    fun `guard pasado null`() {
        assertNull(analyze("eché abono a las plantas"))
    }

    @Test
    fun `guard negada null`() {
        assertNull(analyze("no echar abono a las plantas"))
    }

    @Test
    fun `guard sustantivo null`() {
        assertNull(analyze("el abono está caducado"))
    }

    @Test
    fun `guard objeto fuera de familia null`() {
        assertNull(analyze("echar a los archivos al servidor"))
    }

    @Test
    fun `guard futuro sparse null`() {
        assertNull(analyze("vale, echaré abono a las plantas"))
    }

    @Test
    fun `regresiones jardineria byte-identicas`() {
        assertEquals("Podar los setos", analyze("podar los setos")?.title)
        assertEquals("Quitar las malas hierbas", analyze("quitar las malas hierbas")?.title)
        assertEquals("Trasplantar el bonsái", analyze("trasplantar el bonsái")?.title)
    }
}
