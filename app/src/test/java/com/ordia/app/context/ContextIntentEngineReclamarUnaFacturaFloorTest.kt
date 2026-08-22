package com.ordia.app.context

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Piso c.886 — «reclamar una factura»: lateral medida NULL en la sonda
 * c.865 que quedó como candidata propia (asimetría de artículo
 * indefinido, hermana de c.848 «una lavadora» y de la c.880 «una
 * carta»). La gestión financiera más cotidiana («tengo que reclamar una
 * factura del banco») se descartaba en silencio porque el determinante
 * opcional del piso c.865 se acotaba a definido/posesivo. Consecuencia
 * real P1: un cobro indebido que no se reclama a tiempo.
 *
 * El piso (lockstep DOS puntos con plantilla, lección c.616) admite la
 * alternancia `un|una` en el determinante opcional — los bivalentes
 * indefinidos («reclamar un premio…» / «reclamar un turno…») siguen
 * FUERA porque el objeto-ancla sigue siendo `facturas?`. CERO cambios
 * en ContextIntent.kt (keyword «factura» preexistente).
 */
class ContextIntentEngineReclamarUnaFacturaFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    @Test
    fun `reclamar una factura manana captura TASK`() {
        val r = analyze("reclamar una factura mañana")
        assertNotNull("«reclamar una factura mañana» debe capturar", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Reclamar una factura", r.title)
    }

    @Test
    fun `reclamar una factura esta tarde ancla fecha`() {
        val r = analyze("reclamar una factura esta tarde")
        assertNotNull("«reclamar una factura esta tarde» debe capturar", r)
        assertNotNull("dueAt debe anclarse", r!!.dueAt)
    }

    @Test
    fun `reclamar una factura del banco titulo conserva complemento`() {
        val r = analyze("reclamar una factura del banco mañana")
        assertNotNull(r)
        assertEquals("Reclamar una factura del banco", r!!.title)
    }

    @Test
    fun `vale reclamar una factura acuse captura`() {
        val r = analyze("vale, reclamar una factura mañana")
        assertNotNull("acuse «vale,» debe capturar", r)
    }

    // ─── Guards (NULL deseado) ─────────────────────────────────────

    @Test
    fun `reclamar un premio bivalente fuera`() {
        assertNull(analyze("reclamar un premio mañana"))
    }

    @Test
    fun `reclamar un turno bivalente fuera`() {
        assertNull(analyze("reclamar un turno mañana"))
    }

    @Test
    fun `reclamar una factura negada fuera`() {
        assertNull(analyze("no reclamar una factura mañana"))
    }

    @Test
    fun `reclamar una factura duda fuera`() {
        assertNull(analyze("quizá reclamar una factura mañana"))
    }

    @Test
    fun `reclame una factura pasado fuera`() {
        assertNull(analyze("reclamé una factura ayer"))
    }

    @Test
    fun `una factura verbo suelto fuera`() {
        assertNull(analyze("una factura pendiente"))
    }

    // ─── Regresiones (HIT esperado) ───────────────────────────────

    @Test
    fun `reclamar la factura c865 regresion`() {
        val r = analyze("reclamar la factura del banco mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }

    @Test
    fun `reclamar mi factura posesivo regresion`() {
        val r = analyze("reclamar mi factura mañana")
        assertNotNull(r)
    }

    @Test
    fun `reclamar las facturas plural definido regresion`() {
        val r = analyze("reclamar las facturas mañana")
        assertNotNull(r)
    }

    // ─── Envolvente c.613 ────────────────────────────────────────

    @Test
    fun `recuerdame reclamar una factura envolvente routa TASK`() {
        val r = analyze("recuérdame reclamar una factura mañana")
        assertNotNull("envolvente c.613 debe routar TASK", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }
}
