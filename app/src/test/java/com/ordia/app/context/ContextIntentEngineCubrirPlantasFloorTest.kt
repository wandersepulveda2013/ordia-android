package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1223 (lateral ABIERTA de la auditoría clase VIGESIMOSÉPTIMA jardinería/plantas c.1211):
 * «cubrir (las)? (plantas|suculentas|rosales|jardín) (del frío)?» — verbo bivalente
 * «cubrir» acotado al objeto familia-planta, motivo meteo opcional; sin keyword nueva
 * (gate c.751). Lockstep DOS puntos (lección c.616, grafías preservadas c.653):
 * (1) piso `HOUSEHOLD_COVER_PLANTS_FLOOR` añadido a `HOUSEHOLD_FLOORS`;
 * (2) plantilla canónica `matchCubrirPlantas` (titulación).
 */
class ContextIntentEngineCubrirPlantasFloorTest {
    private fun event(t: String) = ContextEvent(
        source = ContextCaptureSource.SHARED_TEXT,
        rawText = t,
        timestampMs = 0L
    )
    private fun analyze(t: String) = ContextIntentEngine.analyze(event(t))

    @Test
    fun `cubrir las plantas captura HOUSEHOLD`() {
        val intent = analyze("cubrir las plantas")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent?.kind)
        assertEquals("Cubrir las plantas", intent?.title)
    }

    @Test
    fun `cubrir las plantas cuando hace frio captura HOUSEHOLD`() {
        val intent = analyze("cubrir las plantas cuando hace frío")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent?.kind)
        assertEquals("Cubrir las plantas cuando hace frío", intent?.title)
    }

    @Test
    fun `cubrir los rosales del frio captura HOUSEHOLD`() {
        val intent = analyze("cubrir los rosales del frío")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent?.kind)
        assertEquals("Cubrir los rosales del frío", intent?.title)
    }

    @Test
    fun `cubrir con temporal captura y titula limpio`() {
        val intent = analyze("mañana cubrir las plantas")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent?.kind)
        assertEquals("Cubrir las plantas", intent?.title)
    }

    @Test
    fun `por favor cubrir las suculentas captura HOUSEHOLD`() {
        val intent = analyze("por favor cubrir las suculentas")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent?.kind)
        assertEquals("Cubrir las suculentas", intent?.title)
    }

    @Test
    fun `cubrir el jardin por la helada captura HOUSEHOLD`() {
        val intent = analyze("cubrir el jardín por la helada")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent?.kind)
        assertEquals("Cubrir el jardín por la helada", intent?.title)
    }

    @Test
    fun `guard pasado null`() {
        assertNull(analyze("cubrí las plantas"))
    }

    @Test
    fun `guard negada null`() {
        assertNull(analyze("no cubrir las plantas"))
    }

    @Test
    fun `guard sustantivo null`() {
        assertNull(analyze("la cubierta está rota"))
    }

    @Test
    fun `guard objeto fuera de familia null`() {
        assertNull(analyze("cubrir la mesa"))
    }

    @Test
    fun `guard futuro sparse null`() {
        assertNull(analyze("vale, cubriré las plantas"))
    }

    @Test
    fun `regresiones jardineria ropa byte-identicas`() {
        assertEquals("Quitar la mancha de la camisa", analyze("quitar la mancha de la camisa")?.title)
        assertEquals("Coser el botón", analyze("coser el botón")?.title)
        assertEquals("Echar abono a las plantas", analyze("echar abono a las plantas")?.title)
    }
}
