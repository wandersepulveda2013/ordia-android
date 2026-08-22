package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.889: extensión del objeto del piso reflexivo «hacerse» (familia
 * c.862/c.876/c.881/c.882) al complemento «prueba(s) de sonido» —
 * última lateral de la familia, medida NULL y registrada como guard
 * (`ContextIntentEnginePruebaSangreFloorTest.bivalente sonido
 * descartada`). La prueba de sonido (soundcheck de la banda/el técnico
 * antes del bolo o el evento) es una obligación con desplazamiento al
 * local/sala cuyo olvido tiene coste real (P1, evitar olvidos); el
 * complemento «de sonido» es el ancla inequívoca (criterio c.684/c.717/
 * c.731/c.842/c.862): «prueba» sola sigue siendo bivalente («prueba
 * del coche» → NULL). Medición PRE con sonda persistida
 * `tools/probe/PruebaSonidoProbe.kt` (run_probe.sh): 7/7 candidatas
 * NULL, 6/6 guardas NULL, 3/3 regresiones HIT (envolvente «recuérdame
 * …» → TASK 0.54 vía candado c.613). Decisión de dominio: ERRAND
 * (doctrina «la diligencia gobierna» c.842/c.862; el soundcheck exige
 * desplazamiento al local, hermana de la familia). Lockstep en DOS
 * puntos (lección c.616): piso + plantilla de título. CERO cambios en
 * [ContextIntent.kt]: «hacer» es substring de «hacerme/hacerse»
 * (hermana de c.860/c.862). Sin cláusula dedicada en
 * [imperativeIsNegated] (aritmética c.859/c.860/c.862). Acotado
 * deliberado (una forma por ciclo): el sinónimo «soundcheck» y la
 * forma desnuda quedan FUERA.
 */
class ContextIntentEnginePruebaSonidoFloorTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Capturas directas (piso) ----

    @Test
    fun `captura base con manana`() {
        val i = analyze("hacerme la prueba de sonido mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerme la prueba de sonido", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura hacerse con dia de semana`() {
        val i = analyze("hacerse la prueba de sonido el viernes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerse la prueba de sonido", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura indefinido una`() {
        val i = analyze("hacerme una prueba de sonido")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerme una prueba de sonido", i.title)
    }

    @Test
    fun `captura plural las`() {
        val i = analyze("hacerme las pruebas de sonido mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerme las pruebas de sonido", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura acuse vale`() {
        val i = analyze("vale, hacerme la prueba de sonido")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerme la prueba de sonido", i.title)
    }

    // ---- Controles (siguen NULL) ----

    @Test
    fun `negada descartada`() {
        assertNull(analyze("no hacerme la prueba de sonido mañana"))
    }

    @Test
    fun `duda descartada`() {
        assertNull(analyze("quizá hacerme la prueba de sonido mañana"))
    }

    @Test
    fun `pasado descartado`() {
        assertNull(analyze("me hice la prueba de sonido ayer"))
    }

    @Test
    fun `forma desnuda bivalente descartada`() {
        // Sin enclítico reflexivo la forma es bivalente (doctrina c.862).
        assertNull(analyze("hacer la prueba de sonido mañana"))
    }

    @Test
    fun `declarativa nominal descartada`() {
        assertNull(analyze("la prueba de sonido quedó bien"))
    }

    @Test
    fun `objeto bivalente coche descartado`() {
        // La ancla es «de sonido»; «del coche» no casa y sigue NULL.
        assertNull(analyze("hacerme la prueba del coche"))
    }

    // ---- Regresiones ----

    @Test
    fun `regresion analisis c862`() {
        val i = analyze("hacerme un análisis de sangre el lunes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerme un análisis de sangre", i.title)
    }

    @Test
    fun `regresion prueba sangre c876`() {
        val i = analyze("hacerme la prueba de sangre mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerme la prueba de sangre", i.title)
    }

    @Test
    fun `regresion tatuaje c881`() {
        val i = analyze("hacerse un tatuaje mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerse un tatuaje", i.title)
    }

    @Test
    fun `regresion embarazo c882`() {
        val i = analyze("hacerme la prueba de embarazo")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerme la prueba de embarazo", i.title)
    }
}
