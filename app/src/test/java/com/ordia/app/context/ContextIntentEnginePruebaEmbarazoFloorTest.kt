package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.882: extensión del objeto del piso reflexivo «hacerse» (familia
 * c.862/c.876/c.881) al complemento «prueba(s) de embarazo» — lateral
 * registrada como deferida deliberadamente desde c.876 («embarazo queda
 * fuera como lateral»). Una prueba de embarazo suele ser una gestión de
 * salud de alta relevancia personal cuyo olvido tiene coste real (P1,
 * evitar olvidos). Medición PRE con sonda efímera
 * `/tmp/probe881/PreEmbarazoProbe.kt` sobre HEAD 3aed02d (post-c.881):
 * 4/4 candidatas NULL (gap silencioso P1), 4/4 controles NULL, 4/4
 * regresiones HIT (incluida la envolvente «recuérdame …» → TASK vía
 * candado c.613). Hermana de «hacerse un tatuaje» c.881 (misma familia
 * reflexiva; aquí la gestión de salud del laboratorio/farmacia).
 * Kind deliberado: ERRAND (doctrina «la diligencia gobierna» c.842;
 * acudir a la farmacia/laboratorio es diligencia). Objeto acotado a
 * `pruebas? de embarazo` (criterio c.684/c.717/c.731/c.842/c.862):
 * «prueba» sola es bivalente («prueba de sonido», «prueba del coche»),
 * así el complemento «de embarazo» es el ancla inequívoca; el enclítico
 * reflexivo sigue EXIGIDO (doctrina c.862: la forma desnuda queda
 * bivalente). Anti-colisión ejercida: la lateral «hacerse tatuaje»
 * fue resuelta por un run hermano (commit 3aed02d) tras mi medición PRE
 * sobre caba490; esta es la siguiente de la cola. Lockstep en DOS
 * puntos (lección c.616): piso + plantilla de título. CERO cambios en
 * [ContextIntent.kt] el verbo: «hacer» es substring de «hacerme/
 * hacerse» (hermana de c.860/c.862). Sin cláusula dedicada en
 * [imperativeIsNegated] (aritmética c.859/c.860/c.862). Acotado
 * deliberado (una forma por ciclo): el sinónimo «test de embarazo» y
 * la forma desnuda quedan FUERA como laterales.
 */
class ContextIntentEnginePruebaEmbarazoFloorTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Capturas directas (piso) ----

    @Test
    fun `captura base con manana`() {
        val i = analyze("hacerme la prueba de embarazo mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerme la prueba de embarazo", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura hacerse con dia de semana`() {
        val i = analyze("hacerse la prueba de embarazo el viernes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerse la prueba de embarazo", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura indefinido una`() {
        val i = analyze("hacerme una prueba de embarazo")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerme una prueba de embarazo", i.title)
    }

    @Test
    fun `captura plural las`() {
        val i = analyze("hacerme las pruebas de embarazo el lunes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerme las pruebas de embarazo", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `envolvente gobierna task`() {
        val i = analyze("recuérdame hacerme la prueba de embarazo mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Hacerme la prueba de embarazo", i.title)
    }

    // ---- Controles (siguen NULL) ----

    @Test
    fun `negada descartada`() {
        assertNull(analyze("no hacerme la prueba de embarazo mañana"))
    }

    @Test
    fun `duda descartada`() {
        assertNull(analyze("quizá hacerse la prueba de embarazo mañana"))
    }

    @Test
    fun `pasado descartado`() {
        assertNull(analyze("me hice la prueba de embarazo ayer"))
    }

    @Test
    fun `forma desnuda bivalente descartada`() {
        // Sin enclítico reflexivo la forma es bivalente (doctrina c.862).
        assertNull(analyze("hacer la prueba de embarazo mañana"))
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
}
