package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * c.842: piso ERRAND «cortar/cortarme el pelo» (peluquería) — familia
 * DOBLE (la forma desnuda y la enclítica eran NULL; candidata 3/4 de
 * la sonda persistida c.834 `tools/probe/SixthClassEncliticProbe.kt`,
 * última candidata abierta de la clase sexta; NULL PRE verificado por
 * sonda efímera `/tmp/probe842/CortarPeloPreProbe.kt` sobre HEAD
 * 9495082: las 7 capturas directas daban NULL; la envolvente
 * «recuérdame cortarme el pelo mañana» ya enrutaba TASK 0.45 vía
 * candado de envolvente — asimetría de ruta hermana de c.765…c.840).
 * Olvido silencioso P1: la cita de peluquería/barbería es de los
 * compromisos cotidianos más frecuentes.
 * Kind deliberado: ERRAND (desplazamiento a la peluquería, hermano de
 * «echar gasolina» c.829 — misma doctrina «la diligencia gobierna»;
 * no APPOINTMENT: no exige cita concertada y APPOINTMENT es bonus-kind
 * sin pisos). «cortar» es bivalente (césped c.731/pan/comunicación),
 * así el piso se ACOTA al objeto `pelo` (criterio c.684/c.717/c.731).
 * El pronombre enclítico `(?:me|te|se|nos)?` sigue el precedente más
 * amplio de equipaje c.836 (todas las formas reflexivas/dativas de la
 * 1ª/2ª/3ª persona son compromisos reales: «cortarse el pelo» es tan
 * cotidiana como «cortarme el pelo»). El título CONSERVA el pronombre
 * («Cortarme el pelo», doctrina c.653). Lockstep: keyword-OBJETO
 * «pelo» en ERRAND (lección c.751) + cláusula de negación dedicada en
 * [imperativeIsNegated] (cinturón y tirantes, precedente c.829) +
 * plantilla de título (lección c.616). Acotado deliberado (una forma
 * por ciclo): plural «los pelos» (RESUELTA c.1055, re-pin en
 * `plural los pelos RESUELTO c1055`), dativo «cortarle el pelo al
 * niño» (c.1006) y objeto «cabello» (c.1013) — antes FUERA.
 */
class ContextIntentEngineCortarPeloFloorTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Capturas directas (piso) ----

    @Test
    fun `captura forma enclitica me con fecha`() {
        val i = analyze("cortarme el pelo el sábado")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Cortarme el pelo", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura forma desnuda con fecha`() {
        val i = analyze("cortar el pelo el sábado")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Cortar el pelo", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura forma enclitica se`() {
        val i = analyze("cortarse el pelo mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Cortarse el pelo", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura forma enclitica nos`() {
        val i = analyze("cortarnos el pelo el viernes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Cortarnos el pelo", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura sin fecha`() {
        val i = analyze("cortar el pelo")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Cortar el pelo", i.title)
    }

    @Test
    fun `captura con acuse vale`() {
        val i = analyze("vale, cortarme el pelo esta tarde")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Cortarme el pelo", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val i = analyze("hoy cortar el pelo")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Cortar el pelo", i.title)
        assertNotNull(i.dueAt)
    }

    // ---- Envolvente (ruta hermana intacta) ----

    @Test
    fun `envolvente recordame enruta TASK`() {
        val i = analyze("recuérdame cortarme el pelo mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Cortarme el pelo", i.title)
        assertNotNull(i.dueAt)
    }

    // ---- Guards: deben permanecer NULL ----

    @Test
    fun `negada no captura`() {
        assertNull(analyze("no cortarme el pelo"))
    }

    @Test
    fun `duda quizas no captura`() {
        assertNull(analyze("quizá cortarme el pelo mañana"))
    }

    @Test
    fun `narrativa pasado no captura`() {
        assertNull(analyze("me corté el pelo ayer"))
    }

    @Test
    fun `futuro conjugado no captura`() {
        assertNull(analyze("cortaré el pelo mañana"))
    }

    @Test
    fun `declarativo no captura`() {
        assertNull(analyze("el pelo está largo"))
    }

    @Test
    fun `verbo suelto no captura`() {
        assertNull(analyze("cortarme"))
    }

    @Test
    fun `objeto bivalente pan no captura`() {
        assertNull(analyze("cortar el pan para la cena"))
    }

    @Test
    fun `plural los pelos RESUELTO c1055`() {
        // Re-pin legítimo (precedente c.1019/c.1024/c.1046): la
        // candidata documentada se captura en c.1055 (lockstep 3
        // puntos, `pelos?`). No borrada — renombrada con evidencia.
        val i = analyze("cortarme los pelos mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Cortarme los pelos", i.title)
        assertNotNull(i.dueAt)
    }

    // ---- Regresiones (pisos hermanos intactos) ----

    @Test
    fun `regresion cortar el cesped sigue HOUSEHOLD`() {
        val i = analyze("cortar el césped mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `regresion echar gasolina sigue ERRAND`() {
        val i = analyze("echar gasolina mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertNotNull(i.dueAt)
    }

    // ---- Lockstep keyword (lección c.751) ----

    @Test
    fun `keyword pelo en trigger words`() {
        assertTrue(ContextIntentKind.TRIGGER_WORDS.contains("pelo"))
    }
}
