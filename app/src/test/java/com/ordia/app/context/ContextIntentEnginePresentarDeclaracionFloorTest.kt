package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Piso c.875 — «presentar la declaración de la renta»: lateral registrada
 * en c.863 por la sonda `tools/probe/EighthClassAdminProbe.kt` (octava
 * clase: gestiones de la vida adulta — gestión fiscal). El piso acotado
 * «hacer la declaración de la renta» (c.863) cubre sólo el verbo «hacer»;
 * «presentar» es bivalente (presentar una solicitud, a una persona…),
 * medido NULL en la sonda PRE de este ciclo: se DESCARTABA silenciosamente.
 * Consecuencia real: no presentar la declaración a tiempo → sanción fiscal.
 *
 * El piso vive en [ContextIntentEngine.hasStrongTaskImperative] y exige:
 * ancla de inicio/acuse «vale,»/prefijo temporal, guard anti-negación
 * `(?<!no )` y objeto fiscal acotado «la declaración de la renta».
 * Objetos distintos («presentar la solicitud», «presentar la documentación
 * de Ana») medidos NULL en la sonda PRE; quedan FUERA (doctrina
 * anti-overreach: una forma por ciclo).
 *
 * Kind decidido en este ciclo: TASK — convergente con hermanos c.863
 * («hacer la declaración de la renta» → TASK) y «declarar la renta»
 * (lateral aún NULL). Gerundio/duda/pasado descartados.
 *
 * Lockstep lección c.751: keyword-VERBO «presentar» en
 * [ContextIntentKind.TASK] — sin ella la frase no alcanza el análisis vía
 * [ContextIntent.TRIGGER_WORDS]. Bivalente: «presentar la solicitud» o
 * «presentar la documentación» quedan bajo el umbral moral (piso anclado a
 * «la declaración de la renta» excluye cualquier otro objeto).
 */
class ContextIntentEnginePresentarDeclaracionFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    @Test
    fun `presentar la declaracion este mes captura TASK`() {
        val r = analyze("presentar la declaración de la renta este mes")
        assertNotNull("«presentar la declaración de la renta este mes» (era NULL en sonda PRE)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals(
            "Presentar la declaración de la renta este mes",
            r.title
        )
    }

    @Test
    fun `presentar mañana captura con dueAt`() {
        val r = analyze("presentar la declaración de la renta mañana")
        assertNotNull("«presentar la declaración de la renta mañana» (era NULL)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Presentar la declaración de la renta", r.title)
        assertNotNull("«mañana» debe anclar dueAt", r.dueAt)
    }

    @Test
    fun `acuse vale captura`() {
        val r = analyze("vale, presentar la declaración de la renta")
        assertNotNull("«vale, …» (era NULL)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Presentar la declaración de la renta", r.title)
    }

    // -- Guardas anti-sobre-alcance (esperado: NULL) --
    @Test
    fun `negada quizí descarta`() {
        assertNull(analyze("quizá presentar la declaración de la renta"))
    }

    @Test
    fun `pasado presenté descarta`() {
        assertNull(analyze("presenté la declaración de la renta ayer"))
    }

    // Re-pin legítimo c.1134 (precedente c.1035/c.1041/c.1094): los
    // objetos «solicitud» y «documentación» seguían fuera del piso
    // específico c.875 cuando este test se escribió; el piso hermano
    // acotado c.1134 «presentar <trámite>» los captura ahora de forma
    // deliberada (misma familia burocrática, kind TASK, título limpio).
    @Test
    fun `objeto solicitud captura via piso hermano c1134`() {
        val r = analyze("presentar la solicitud mañana")
        assertNotNull("captura vía piso c.1134 (era pin NULL de c.875)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Presentar la solicitud", r.title)
        assertNotNull(r.dueAt)
    }

    @Test
    fun `objeto documentación captura via piso hermano c1134`() {
        val r = analyze("presentar la documentación de Ana")
        assertNotNull("captura vía piso c.1134 (era pin NULL de c.875)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Presentar la documentación de Ana", r.title)
    }

    // -- Regresiones hermanas (esperado: HIT propio) --
    @Test
    fun `regresión hermano c863 hacer captura`() {
        val r = analyze("hacer la declaración de la renta este mes")
        assertNotNull("hermano c.863 debe seguir HIT", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }

    @Test
    fun `regresión pagar la renta captura PAYMENT`() {
        val r = analyze("pagar la renta este mes")
        assertNotNull("«pagar la renta» debe seguir HIT", r)
    }
}
