package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.862: piso ERRAND «hacerme/hacerte/hacerse un análisis (de sangre)» —
 * candidata 4/7 de la sonda persistida c.857
 * `tools/probe/EighthClassAdminProbe.kt` (OCTAVA clase, gestiones de
 * adulto — salud cotidiana). Medición PRE con sonda efímera
 * `/tmp/probe862/PreProbe.kt` sobre HEAD ee988d0: 6/6 declarativas NULL
 * (olvido silencioso P1: la analítica de sangre es de los compromisos
 * de salud cotidianos más frecuentes y olvidarla tiene consecuencia
 * real), 6/6 controles NULL correctos, 3/3 laterales NULL, 5/5
 * regresiones HIT (incluida la envolvente «recuérdame hacerme un
 * análisis de sangre el lunes» → TASK 0.54 vía candado c.613 —
 * asimetría de ruta hermana de c.765…c.861).
 * Hermana de «cortarme el pelo» (c.842): reflexiva con desplazamiento
 * (al laboratorio/centro de salud). Kind deliberado: ERRAND (misma
 * doctrina «la diligencia gobierna»; no APPOINTMENT: bonus-kind sin
 * pisos y la frase no afirma cita concertada — eso sería «pedir hora
 * para el análisis», otra forma; no TASK: la familia reflexiva de
 * desplazamiento vive en ERRAND). «hacer» es bivalente por excelencia
 * (un favor/un tatuaje/daño/la maleta), así el piso se ACOTA al objeto
 * `an[aá]lisis` (criterio c.684/c.717/c.731/c.842) y EXIGE el enclítico
 * reflexivo: la forma desnuda «hacer un análisis» es bivalente a su vez
 * («hacer un análisis de datos del informe» = estudio, no analítica
 * médica) — lateral documentada, una forma por ciclo (doctrina
 * anti-overreach). El enclítico `(?:me|te|se|nos)` sigue el precedente
 * amplio de equipaje c.836. Posición libre con `\b` (familia c.643/
 * c.647/c.842: admite acuse «vale, …» y prefijo temporal «mañana …»);
 * la negación inmediata se bloquea en la propia regex `(?<!no )` y la
 * duda por la penalización post-pisos [HEDGE_PENALTY] c.649
 * (0.45−0.3 → NULL). Lockstep en DOS puntos (lección c.616; a diferencia
 * de c.842 NO hace falta keyword — «hacerme» contiene la keyword TASK
 * «hacer» por subcadena y la frase ya llega al análisis en producción,
 * hermana de c.860): piso en [ERRAND_FLOORS] + plantilla de título en
 * [extractTitle] (match arranca en el verbo, pronombre enclítico
 * conservado — doctrina c.653 —, residuo temporal depurado por
 * [sanitizeTitle]). Sin cláusula dedicada en [imperativeIsNegated]:
 * keyword «hacer» 0.12 + bono temporal 0.1 = 0.22 < umbral (aritmética
 * c.859/c.860) y el piso lleva su propio lookbehind.
 */
class ContextIntentEngineAnalisisSangreFloorTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Capturas directas (piso) ----

    @Test
    fun `captura base con dia de semana`() {
        val i = analyze("hacerme un análisis de sangre el lunes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerme un análisis de sangre", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura sin complemento de sangre`() {
        val i = analyze("hacerme un análisis mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerme un análisis", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura enclitico te`() {
        val i = analyze("hacerte un análisis de sangre el lunes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerte un análisis de sangre", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura enclitico se`() {
        val i = analyze("hacerse un análisis de sangre mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerse un análisis de sangre", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura plural con acuse`() {
        val i = analyze("vale, hacerme unos análisis de sangre el martes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerme unos análisis de sangre", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val i = analyze("mañana hacerme un análisis de sangre")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Hacerme un análisis de sangre", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `envolvente gobierna task`() {
        val i = analyze("recuérdame hacerme un análisis de sangre el lunes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Hacerme un análisis de sangre", i.title)
    }

    // ---- Controles (siguen NULL) ----

    @Test
    fun `negada descartada`() {
        assertNull(analyze("no hacerme un análisis de sangre el lunes"))
    }

    @Test
    fun `duda descartada`() {
        assertNull(analyze("quizá hacerme un análisis de sangre"))
    }

    @Test
    fun `pasado descartado`() {
        assertNull(analyze("me hice un análisis ayer"))
    }

    @Test
    fun `verbo aislado descartado`() {
        assertNull(analyze("hacerme"))
    }

    @Test
    fun `bivalente favor descartada`() {
        assertNull(analyze("hacerme un favor mañana"))
    }

    @Test
    fun `bivalente tatuaje descartada`() {
        assertNull(analyze("hacerme un tatuaje el lunes"))
    }

    @Test
    fun `forma desnuda bivalente descartada`() {
        // «hacer un análisis» sin enclítico es bivalente (análisis de
        // datos/estudio): fuera de alcance este ciclo (lateral c.862).
        assertNull(analyze("hacer un análisis de sangre el lunes"))
    }

    @Test
    fun `forma desnuda no medica descartada`() {
        assertNull(analyze("hacer un análisis de datos mañana"))
    }

    // ---- Regresiones ----

    @Test
    fun `regresion cortarme el pelo c842`() {
        val i = analyze("cortarme el pelo mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Cortarme el pelo", i.title)
    }

    @Test
    fun `regresion tomar la medicacion c859`() {
        val i = analyze("tomar la medicación a las 8")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Tomar la medicación", i.title)
    }
}
