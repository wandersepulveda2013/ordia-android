package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1115: extensión del objeto del piso reflexivo «hacerse» (familia
 * c.862/c.876/c.881/c.882/c.889) al sustantivo coloquial «analítica(s)» —
 * candidata (a) de la clase DECIMOTERCERA, medida NULL y registrada en la
 * sonda persistida c.1102 (`tools/probe/ThirteenthClassHealthProbe.kt`,
 * caso C2). «Hacerse las analíticas» es LA forma cotidiana de referirse a
 * la analítica de sangre en el habla común (femenina, del latín analítica);
 * su olvido tiene coste real (P1, evitar olvidos: ayuno, desplazamiento al
 * laboratorio). Medición PRE con sonda efímera `/tmp/probe1115/Probe.kt`
 * (motor real vía tools/run_probe.sh): 8/8 candidatas NULL, 7/7 pines NULL,
 * envolvente «recuérdame hacerme las analíticas mañana» → TASK 0.54 vía
 * candado c.613, 8/8 regresiones HIT. Canarios de título medidos: la cola
 * «la semana que viene» la depura [sanitizeTitle] y ancla dueAt; «en
 * ayunas» se CONSERVA en el título (es contenido — la condición de la
 * analítica—, no residuo temporal). Decisión de dominio: ERRAND heredada
 * (doctrina «la diligencia gobierna» c.842/c.862: misma gestión con
 * desplazamiento al laboratorio que «análisis»/«prueba de sangre»).
 * Lockstep en DOS puntos (lección c.616): piso + plantilla de título.
 * CERO cambios en [ContextIntent.kt]: «hacer» es substring de
 * «hacerme/hacerse» (hermana de c.860/c.862/c.889). Sin cláusula dedicada
 * en [imperativeIsNegated] (aritmética c.859/c.860/c.862). Acotado
 * deliberado (una forma por ciclo): la forma desnuda «hacer la analítica»
 * sigue FUERA por bivalente (doctrina c.862: «hacer la analítica de datos»
 * es estudio, no analítica médica) y «mis analíticas» queda lateral.
 */
class ContextIntentEngineHacerseAnaliticasFloorTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Capturas directas (piso) ----

    @Test
    fun `captura base en ayunas la semana que viene`() {
        // Caso C2 exacto de la sonda persistida c.1102.
        val i = analyze("hacerme las analíticas en ayunas la semana que viene")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerme las analíticas en ayunas", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura singular con manana`() {
        val i = analyze("hacerme la analítica mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerme la analítica", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura hacerse con dia de semana`() {
        val i = analyze("hacerse las analíticas el lunes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerse las analíticas", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura indefinido unas`() {
        // «esta semana» queda en el título sin anclar dueAt: lateral ABIERTA
        // de colas ya documentada de la familia de pisos (c.845/c.852/c.1079,
        // observada c.1102 — NO es esta unidad).
        val i = analyze("hacerme unas analíticas esta semana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerme unas analíticas esta semana", i.title)
    }

    @Test
    fun `captura sin tilde`() {
        val i = analyze("hacerme las analiticas mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerme las analiticas", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura acuse vale`() {
        val i = analyze("vale, hacerme las analíticas")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerme las analíticas", i.title)
        assertNull(i.dueAt)
    }

    @Test
    fun `captura dativo te`() {
        val i = analyze("hacerte las analíticas mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerte las analíticas", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura dativo nos`() {
        val i = analyze("hacernos las analíticas el jueves")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacernos las analíticas", i.title)
        assertNotNull(i.dueAt)
    }

    // ---- Controles (siguen NULL) ----

    @Test
    fun `negada descartada`() {
        assertNull(analyze("no hacerme las analíticas mañana"))
    }

    @Test
    fun `duda descartada`() {
        assertNull(analyze("quizá hacerme las analíticas mañana"))
    }

    @Test
    fun `pasado me hice descartado`() {
        assertNull(analyze("me hice las analíticas ayer"))
    }

    @Test
    fun `pasado hice descartado`() {
        assertNull(analyze("hice las analíticas ayer"))
    }

    @Test
    fun `forma desnuda bivalente descartada`() {
        // Sin enclítico reflexivo la forma es bivalente (doctrina c.862):
        // «hacer la analítica de datos» es estudio, no analítica médica.
        assertNull(analyze("hacer la analítica de datos del informe mañana"))
    }

    @Test
    fun `declarativa web descartada`() {
        assertNull(analyze("la analítica web del sitio"))
    }

    @Test
    fun `sustantivo aislado descartado`() {
        assertNull(analyze("las analíticas"))
    }

    // ---- Pin envolvente (byte-equivalente, candado c.613) ----

    @Test
    fun `envolvente sigue task`() {
        val i = analyze("recuérdame hacerme las analíticas mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Hacerme las analíticas", i.title)
        assertNotNull(i.dueAt)
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

    @Test
    fun `regresion sonido c889`() {
        val i = analyze("hacerme la prueba de sonido mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerme la prueba de sonido", i.title)
    }
}
