package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

// c.1218: PIN de DESCARTE DOCUMENTADO — la lateral ABIERTA «lavar (el)
// coche / (la) camioneta» (marcador propio c.1218) resultó FALSO GAP:
// sonda PRE persistida `tools/probe/LavarCocheProbe.kt` (motor real vía
// `tools/run_probe.sh`) muestra 7/7 formas ya HIT HOUSEHOLD 0.45 con
// títulos limpios («Lavar el coche», «Lavar la camioneta», «Lavar el
// auto», «Lavar mi coche») vía el piso genérico [HOUSEHOLD_FLOOR]
// (`lavar\s+\w`, familia c.638/c.647 posición libre) + plantilla
// genérica. Un piso acotado «lavar + coche» (como proponía el
// marcador) sería código REDUNDANTE (feature bloat — contra la
// filosofía menos-es-más); se descarta el fix y se pinea el
// comportamiento: 6 capturas HIT + 4 guards NULL (futuro, negación,
// declarativa, verbo solo) + 2 regresiones hermanas HIT (lavar los
// platos/la ropa). Determinista (regex), cero random, cero UI.
class ContextIntentEngineLavarCocheDescartePinTest {
    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(
            source = ContextCaptureSource.SHARED_TEXT,
            rawText = text,
            timestampMs = 0L
        )
    )

    @Test
    fun lavarElCoche_hitGenerico() {
        val r = analyze("lavar el coche")
        assertNotNull(r)
        assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        assertEquals(0.45f, r.confidence)
        assertEquals("Lavar el coche", r.title)
    }

    @Test
    fun lavarLaCamioneta_hitGenerico() {
        val r = analyze("lavar la camioneta")
        assertNotNull(r)
        assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        assertEquals(0.45f, r.confidence)
        assertEquals("Lavar la camioneta", r.title)
    }

    @Test
    fun lavarElAuto_hitGenerico() {
        val r = analyze("lavar el auto")
        assertNotNull(r)
        assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        assertEquals(0.45f, r.confidence)
        assertEquals("Lavar el auto", r.title)
    }

    @Test
    fun lavarMiCoche_hitGenerico() {
        val r = analyze("lavar mi coche")
        assertNotNull(r)
        assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        assertEquals(0.45f, r.confidence)
        assertEquals("Lavar mi coche", r.title)
    }

    @Test
    fun lavarElCocheManana_hitConColaTemporal() {
        val r = analyze("lavar el coche mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        assertEquals("Lavar el coche", r.title)
    }

    @Test
    fun mananaLavarElCoche_hitConPrefijoTemporal() {
        val r = analyze("mañana lavar el coche")
        assertNotNull(r)
        assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        assertEquals("Lavar el coche", r.title)
    }

    @Test
    fun lavareElCoche_futuroNull() {
        assertNull(analyze("lavaré el coche el sábado"))
    }

    @Test
    fun noLavarElCoche_negacionNull() {
        assertNull(analyze("no lavar el coche"))
    }

    @Test
    fun elCocheEstaSucio_declarativaNull() {
        assertNull(analyze("el coche está sucio"))
    }

    @Test
    fun lavar_verboSoloNull() {
        assertNull(analyze("lavar"))
    }

    @Test
    fun lavarLosPlatos_regresionHermana() {
        val r = analyze("lavar los platos")
        assertNotNull(r)
        assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        assertEquals("Lavar los platos", r.title)
    }

    @Test
    fun lavarLaRopa_regresionHermana() {
        val r = analyze("lavar la ropa")
        assertNotNull(r)
        assertEquals(ContextIntentKind.HOUSEHOLD, r!!.kind)
        assertEquals("Lavar la ropa", r.title)
    }
}
