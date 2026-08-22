package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.889: extensión del objeto del piso reflexivo «hacerse» (familia
 * c.862/c.876/c.881/c.882) al complemento «prueba(s) de sonido» —
 * ÚLTIMA lateral viva de la familia «hacerme/se la prueba…», guardada
 * como sentinel NULL («bivalente sonido descartada») en el test del
 * hermano c.876. Decisión de dominio c.889: el complemento «de sonido»
 * (soundcheck previo al evento) es tan inequívoco como «de sangre»
 * (c.876) / «de embarazo» (c.882) → complemento-ancla del objeto en el
 * piso ERRAND («la diligencia gobierna», doctrina c.842); «prueba del
 * coche» (ITV/formalismo vehicular) sigue FUERA (lateral registrada,
 * UNA por ciclo); enclítico reflexivo EXIGIDO (doctrina c.862: la forma
 * desnuda «hacer la prueba de sonido…» es bivalente y sigue FUERA).
 *
 * Sonda PRE persistida (tools/probe/PruebaSonidoProbe.kt — convención
 * c.822): 8/8 candidatas NULL (gap silencioso P1), 6/6 guards NULL
 * (negación, duda, pasado, forma desnuda, complemento coche, afirmación
 * nominal), envolvente → TASK 0.54 vía el candado c.613, regresiones
 * HIT (sangre / embarazo / tatuaje). Guard-sentinel c.876 convertida a
 * regresión de captura (precedente c.843, procedimiento hermano c.882).
 *
 * Lockstep DOS puntos (lección c.616/c.751, espejo c.882): piso +
 * plantilla en [extractTitle]. CERO cambios en [ContextIntent.kt]:
 * «hacerme» contiene la keyword TASK «hacer» por subcadena (hermana de
 * c.876/c.882). Sin cláusula dedicada en [imperativeIsNegated]:
 * aritmética 0.12 < umbral (paridad c.859/c.860/c.862).
 *
 * Acotamientos verificados: «prueba del coche» y forma desnuda siguen
 * NULL; plural «las pruebas de sonido» captura (artículo plural ya
 * admitido por la familia); negación, duda y pasado descartados.
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
    fun `captura hacerte forma`() {
        val i = analyze("hacerte la prueba de sonido mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerte la prueba de sonido", i.title)
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
        val i = analyze("hacerme las pruebas de sonido el lunes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerme las pruebas de sonido", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `envolvente gobierna task`() {
        val i = analyze("recuérdame hacerme la prueba de sonido mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
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
    fun `forma desnuda descartada`() {
        assertNull(analyze("hacer la prueba de sonido mañana"))
    }

    @Test
    fun `bivalente coche descartada`() {
        assertNull(analyze("hacerme la prueba del coche mañana"))
    }

    @Test
    fun `afirmacion nominal descartada`() {
        assertNull(analyze("la prueba de sonido quedó hecha"))
    }

    @Test
    fun `regresiones sangre embarazo tatuaje intactas`() {
        for (t in listOf(
            "hacerme la prueba de sangre mañana",
            "hacerte la prueba de embarazo mañana",
            "hacernos un tatuaje mañana",
        )) {
            val i = analyze(t)
            assertNotNull(i)
            assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        }
    }
}
