package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.876: extensión del objeto del piso ERRAND «hacerse un análisis»
 * (c.862) al sinónimo coloquial «prueba de sangre» — lateral medida
 * NULL en la sonda PRE del propio ciclo c.862 y registrada en BACKLOG.
 * Medición PRE con sonda efímera `/tmp/probe876/PreProbe.kt` sobre HEAD
 * e53a582: 8/8 declarativas NULL (olvido silencioso P1: en el habla
 * cotidiana la analítica se llama «prueba de sangre» tanto como
 * «análisis»), 6/6 controles NULL correctos, 6/6 regresiones HIT
 * (incluida la envolvente «recuérdame hacerme la prueba de sangre
 * mañana» → TASK vía candado c.613, que ya ruteaba).
 * Hermana de c.862 (misma familia reflexiva con desplazamiento al
 * laboratorio/centro de salud). Kind deliberado: ERRAND (misma
 * doctrina). Objeto acotado a `pruebas? de sangre` (criterio
 * c.684/c.717/c.731/c.842/c.862): «prueba» sola es bivalente
 * («prueba de sonido», «prueba del coche»), así el complemento
 * «de sangre» es el ancla inequívoca; el enclítico reflexivo sigue
 * EXIGIDO (doctrina c.862: la forma desnuda es bivalente). Lockstep en
 * DOS puntos (lección c.616): piso + plantilla de título. CERO cambios
 * en [ContextIntent.kt]: «hacerme» contiene la keyword TASK «hacer» por
 * subcadena (hermana de c.860/c.862). Sin cláusula dedicada en
 * [imperativeIsNegated] (aritmética c.859/c.860/c.862). Acotado
 * deliberado (una forma por ciclo): «prueba del coche» (test-drive) y
 * la forma desnuda quedan FUERA como laterales registradas en BACKLOG.
 * La lateral «prueba de embarazo» se resolvió en c.882 (clase
 * `ContextIntentEnginePruebaEmbarazoFloorTest`); la lateral «prueba de
 * sonido» se resolvió en c.889 (clase
 * `ContextIntentEnginePruebaSonidoFloorTest`).
 */
class ContextIntentEnginePruebaSangreFloorTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Capturas directas (piso) ----

    @Test
    fun `captura base con dia de semana`() {
        val i = analyze("hacerme la prueba de sangre el lunes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerme la prueba de sangre", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura indefinido una`() {
        val i = analyze("hacerme una prueba de sangre esta tarde")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerme una prueba de sangre", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura plural unas`() {
        val i = analyze("hacerme unas pruebas de sangre mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerme unas pruebas de sangre", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura enclitico te`() {
        val i = analyze("hacerte la prueba de sangre mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerte la prueba de sangre", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura con acuse`() {
        val i = analyze("vale, hacerme la prueba de sangre el lunes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerme la prueba de sangre", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val i = analyze("mañana hacerme la prueba de sangre")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerme la prueba de sangre", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura desnuda sin fecha`() {
        val i = analyze("hacerme la prueba de sangre")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerme la prueba de sangre", i.title)
    }

    @Test
    fun `envolvente gobierna task`() {
        val i = analyze("recuérdame hacerme la prueba de sangre mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Hacerme la prueba de sangre", i.title)
    }

    // ---- Controles (siguen NULL) ----

    @Test
    fun `negada descartada`() {
        assertNull(analyze("no hacerme la prueba de sangre mañana"))
    }

    @Test
    fun `duda descartada`() {
        assertNull(analyze("quizá hacerme la prueba de sangre mañana"))
    }

    @Test
    fun `pasado descartado`() {
        assertNull(analyze("me hice la prueba de sangre ayer"))
    }

    @Test
    fun `lateral sonido resuelta c889`() {
        // La lateral «prueba de sonido» (guard NULL desde c.876) se
        // resolvió en c.889: captura como ERRAND hermana de la familia
        // «hacerse»; el objeto bivalente de la guarda pasa a
        // `coche` (ver `ContextIntentEnginePruebaSonidoFloorTest`).
        val i = analyze("hacerme la prueba de sonido el viernes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerme la prueba de sonido", i.title)
    }

    @Test
    fun `forma desnuda bivalente descartada`() {
        // Sin enclítico reflexivo la forma es bivalente (doctrina c.862).
        assertNull(analyze("hacer la prueba de sangre mañana"))
    }

    @Test
    fun `lateral embarazo resuelta c882`() {
        // La lateral «prueba de embarazo» (deferida en c.876) se resolvió
        // en c.882: ahora captura como ERRAND hermana de la familia
        // «hacerse» (ver `ContextIntentEnginePruebaEmbarazoFloorTest`).
        val i = analyze("hacerme la prueba de embarazo")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerme la prueba de embarazo", i.title)
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
    fun `regresion cortarme el pelo c842`() {
        val i = analyze("cortarme el pelo mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Cortarme el pelo", i.title)
    }

    @Test
    fun `regresion envolvente analisis c862`() {
        val i = analyze("recuérdame hacerme un análisis de sangre mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
    }
}
