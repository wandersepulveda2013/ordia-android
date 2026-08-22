package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Objeto «carta» c.880 — lateral c.873 («contestar <correo/email/mensaje>»):
 * en español la carta física sigue siendo correo real, así el imperativo
 * «contestar la carta» se quedaba sin dibuja. Consecuencia real: correo
 * físico sin responder olvidado.
 *
 * Lockstep TRES puntos (lección c.616/c.751): (1) piso con objeto `cartas?`
 * en `hasStrongTaskImperative`; (2) plantilla `extractTitle` (grafía
 * preservada, doctrina c.653); (3) keyword-OBJETO «carta» en
 * `ContextIntent.kt` (bivalente medido: «la carta del restaurante/menú»
 * inerte a 0.12 < umbral; guard en sonda PRE/POST).
 *
 * Sonda efímera `/tmp/probe878/PreContestarCartaProbe.kt` sobre HEAD 44f136a:
 * PRE 3/3 candidatas NULL, 2/2 guards NULL, 2/2 regresiones HIT; POST 7/7 PASS.
 */
class ContextIntentEngineContestarCartaFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    @Test
    fun `contestar la carta singular captura`() {
        val r = analyze("contestar la carta mañana")
        assertNotNull("«contestar la carta mañana» (era NULL en sonda PRE)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Contestar la carta", r.title)
        assertNotNull("«mañana» debe anclar dueAt", r.dueAt)
    }

    @Test
    fun `contestar las cartas plural captura`() {
        val r = analyze("contestar las cartas")
        assertNotNull("«contestar las cartas» (era NULL)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Contestar las cartas", r.title)
    }

    @Test
    fun `contestar la carta con complemento captura`() {
        val r = analyze("contestar la carta de la seguridad social")
        assertNotNull("«contestar la carta de…» (era NULL)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Contestar la carta de la seguridad social", r.title)
    }

    // -- Guardas (esperado: NULL) --
    @Test
    fun `negada descarta`() {
        assertNull(analyze("no contestar la carta"))
    }

    @Test
    fun `bivalente restaurante inerte`() {
        assertNull(analyze("la carta del restaurante está cerrada"))
    }

    // -- Regresiones hermanas (esperado: HIT propio) --
    @Test
    fun `regresión hermano c873 correo captura`() {
        val r = analyze("contestar el correo de ana hoy")
        assertNotNull("hermano c.873 debe seguir HIT", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }

    @Test
    fun `regresión hermano c860 responder correo captura`() {
        val r = analyze("responder el correo hoy")
        assertNotNull("hermano c.860 debe seguir HIT", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }

    @Test
    fun `regresión hermano c879 dativo captura`() {
        val r = analyze("contestarle a juan mañana")
        assertNotNull("hermano c.879 debe seguir HIT", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }
}
