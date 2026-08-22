package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Piso c.876 — «declarar la renta»: lateral registrada en c.863 (objeto
 * fiscal con elipsis de «declaración»; comentario del piso c.863 la
 * descartaba explícita como «verbo distinto», medida NULL). Es la forma
 * idiomática más corta del trámite fiscal en España («tengo que declarar
 * la renta»); con hermanos «hacer la declaración de la renta» (c.863) y
 * «presentar la declaración de la renta» (c.875) ya enrutado TASK, la
 * forma elíptica seguía cayendo a NULL (3/3 candidatas NULL medidas PRE
 * con sonda efímera `/tmp/p876/PreProbe876.kt` sobre HEAD b01e44a).
 * Consecuencia real: sanción fiscal.
 *
 * El verbo «declarar» es bivalente (declarar el amor, declarar en el
 * juicio, declarar bienes) — medidos NULL en la sonda PRE y guardados
 * aquí: el piso se ACOTA al objeto «la renta» (doctrina anti-overreach:
 * una forma por ciclo).
 *
 * Lockstep lección c.751: keyword-VERBO «declarar» en
 * [ContextIntentKind.TASK] — sin ella la frase no alcanza el análisis
 * vía [ContextIntent.TRIGGER_WORDS]. 0.12 sola < umbral y el piso exige
 * el objeto: «declarar el amor» / «declarar en el juicio» quedan FUERA.
 *
 * Kind decidido: TASK — convergente con c.863/c.875 (mismos hermanos
 * fiscales, deliberación registrada allí) y con la envolvente candado
 * c.613 («recuérdame declarar la renta mañana» ya TASK 0.45+).
 */
class ContextIntentEngineDeclararRentaFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    @Test
    fun `declarar la renta este mes captura TASK`() {
        val r = analyze("declarar la renta este mes")
        assertNotNull("«declarar la renta este mes» (era NULL en sonda PRE)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Declarar la renta este mes", r.title)
    }

    @Test
    fun `declarar mañana captura con dueAt`() {
        val r = analyze("declarar la renta mañana")
        assertNotNull("«declarar la renta mañana» (era NULL)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Declarar la renta", r.title)
        assertNotNull("«mañana» debe anclar dueAt", r.dueAt)
    }

    @Test
    fun `acuse vale captura`() {
        val r = analyze("vale, declarar la renta esta tarde")
        assertNotNull("«vale, …» (era NULL)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Declarar la renta", r.title)
    }

    // -- Guardas anti-sobre-alcance (esperado: NULL) --
    @Test
    fun `negada descarta`() {
        assertNull(analyze("no declarar la renta mañana"))
    }

    @Test
    fun `duda quizá descarta`() {
        assertNull(analyze("quizá declarar la renta mañana"))
    }

    @Test
    fun `pasado declaré descarta`() {
        assertNull(analyze("declaré la renta ayer"))
    }

    @Test
    fun `objeto amor bivalente fuera`() {
        assertNull(analyze("declarar el amor a Ana mañana"))
    }

    @Test
    fun `objeto juicio bivalente fuera`() {
        assertNull(analyze("declarar en el juicio del lunes"))
    }

    // -- Regresiones hermanas (esperado: HIT propio) --
    @Test
    fun `regresión hermano c863 hacer captura`() {
        val r = analyze("hacer la declaración de la renta este mes")
        assertNotNull("hermano c.863 debe seguir HIT", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }

    @Test
    fun `regresión hermano c875 presentar captura`() {
        val r = analyze("presentar la declaración de la renta mañana")
        assertNotNull("hermano c.875 debe seguir HIT", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }

    @Test
    fun `regresión pagar la renta captura PAYMENT`() {
        val r = analyze("pagar la renta mañana")
        assertNotNull("«pagar la renta» (alquiler) debe seguir HIT", r)
        assertEquals(ContextIntentKind.PAYMENT, r!!.kind)
    }
}
