package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Piso c.878 — «hacer la renta»: lateral c.863 (elipsis del objeto fiscal) que
 * en España es la forma coloquial de la declaración de la renta. Con
 * hermanos «hacer la declaración de la renta» (c.863), «presentar la
 * declaración de la renta» (c.875) y «declarar la renta» (c.877, hash
 * 84ef421) ya enrutados TASK, la forma elíptica desnuda seguía cayendo a
 * NULL (medida PRE 1/1 candidata límite NULL con sonda efímera
 * `/tmp/probe877/PreHacerRentaProbe.kt` sobre HEAD 4711f2d; envolturas
 * «tengo que/hay que hacer la renta…» ya medidas HIT por wrapper).
 * Consecuencia real: sanción fiscal.
 *
 * Guardados en el piso (medidos NULL en la sonda PRE): «hacer the rent»
 * (bivalencia inversa), «hacer el amor», «hacer mi horario de turnos».
 * El piso se ACOTA al objeto «renta» (doctrina anti-overreach: una forma
 * por ciclo). CERO cambios en ContextIntent.kt: keyword-VERBO «hacer» ya
 * enruta (hermana de c.863).
 */
class ContextIntentEngineHacerRentaFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    @Test
    fun `hacer la renta este mes captura TASK`() {
        val r = analyze("hacer la renta este mes")
        assertNotNull("«hacer la renta este mes» (era NULL en sonda PRE)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Hacer la renta este mes", r.title)
    }

    @Test
    fun `hacer mañana captura con dueAt`() {
        val r = analyze("hacer la renta mañana")
        assertNotNull("«hacer la renta mañana» (era NULL)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Hacer la renta", r.title)
        assertNotNull("«mañana» debe anclar dueAt", r.dueAt)
    }

    @Test
    fun `hacer mi renta captura con posesivo`() {
        val r = analyze("hacer mi renta esta tarde")
        assertNotNull("«hacer mi renta esta tarde» (era NULL)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Hacer mi renta", r.title)
    }

    // -- Guardas anti-sobre-alcance (esperado: NULL) --
    @Test
    fun `bivalencia inversa the rent descarta`() {
        assertNull(analyze("hacer the rent"))
    }

    @Test
    fun `hacer el amor bivalente fuera`() {
        assertNull(analyze("hacer el amor esta noche"))
    }

    @Test
    fun `objeto horario de turnos fuera`() {
        assertNull(analyze("hacer mi horario de turnos"))
    }

    @Test
    fun `negada descarta`() {
        assertNull(analyze("no hacer la renta mañana"))
    }

    // -- Regresiones hermanas (esperado: HIT propio) --
    @Test
    fun `regresión hermano c863 hacer declaración captura`() {
        val r = analyze("hacer la declaración de la renta este mes")
        assertNotNull("hermano c.863 debe seguir HIT", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }

    @Test
    fun `regresión hermano c877 declarar captura`() {
        val r = analyze("declarar la renta mañana")
        assertNotNull("hermano c.877 debe seguir HIT", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }

    @Test
    fun `regresión pagar la renta captura PAYMENT`() {
        val r = analyze("pagar la renta mañana")
        assertNotNull("«pagar la renta» (alquiler) debe seguir HIT", r)
        assertEquals(ContextIntentKind.PAYMENT, r!!.kind)
    }
}
