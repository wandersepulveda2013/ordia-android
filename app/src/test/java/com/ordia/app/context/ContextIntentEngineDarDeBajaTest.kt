package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.895c — familia (4/8) de la clase NOVENA dinero/banca (sonda c.892,
 * BACKLOG P1): «dar de baja el gimnasio/la suscripción» (membresías).
 * NULL PRE medido por la sonda persistida `tools/probe/DarDeBajaProbe.kt`
 * (4/4 NULL candidatas; 7/7 guards+laterales NULL; 4/4 regresiones HIT),
 * base `698c8ba` (c.895b), motor real vía `tools/run_probe.sh`.
 *
 * Decisión de dominio: TASK (gestión administrativa de membresía SIN
 * desplazamiento físico; hermana de «cobrar la nómina» TASK c.895b.
 * La doctrina ERRAND de la clase NOVENA c.842/c.862 gobierna solo el
 * desplazamiento físico).
 *
 * Lockstep TRES puntos (lección c.616/c.616/c.751): (1) piso en
 * [hasStrongTaskImperative] — verbo-frase «dar de baja» acotado al
 * objeto-ancla `gimnasio|suscripciones?` con guard `(?<!no )`;
 * (2) keyword-OBJETO «suscripción»/«suscripcion» en TASK (NO el verbo
 * «dar», extremadamente polivalente — «dar la vuelta/paseo/las gracias»);
 * (3) plantilla de título en [extractTitle] rama TASK «Dar de baja …»
 * (verbo-frase preservado, doctrina c.653).
 *
 * Acotado deliberado: laterales «la línea telefónica» (bivalente),
 * «la baja maternal/del coche» (sustantivo), «dar de alta» (opuesto)
 * permanecen NULL. «ir al gimnasio» sigue EXERCISE (keyword existente;
 * NO se añade «gimnasio» a TASK para no interferir con EXERCISE).
 */
class ContextIntentEngineDarDeBajaTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ─── Capturas (familia 4/8 membresías) ────────────────────────

    @Test
    fun `dar de baja el gimnasio captura TASK con titulo limpio`() {
        val intent = analyze("dar de baja el gimnasio mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Dar de baja el gimnasio", intent.title)
    }

    @Test
    fun `dar de baja la suscripcion captura TASK`() {
        val intent = analyze("dar de baja la suscripción mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Dar de baja la suscripción", intent.title)
    }

    @Test
    fun `dar de baja con dia de semana captura TASK`() {
        val intent = analyze("dar de baja el gimnasio el viernes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `dar de baja la suscripcion de netflix captura TASK`() {
        val intent = analyze("dar de baja la suscripción de netflix esta tarde")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `envolvente recuerdame sigue TASK por candado c613`() {
        val intent = analyze("recuérdame dar de baja el gimnasio mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Dar de baja el gimnasio", intent.title)
    }

    // ─── Negación (guard del piso) ────────────────────────────────

    @Test
    fun `negada no dar de baja no captura`() {
        assertNull(analyze("no dar de baja el gimnasio mañana"))
    }

    @Test
    fun `duda quizas dar de baja no captura`() {
        assertNull(analyze("quizá dar de baja la suscripción mañana"))
    }

    @Test
    fun `pasado di de baja no captura`() {
        assertNull(analyze("ayer di de baja el gimnasio"))
    }

    // ─── Laterales deliberados NULL ───────────────────────────────

    @Test
    fun `dar de alta accion opuesta no captura`() {
        assertNull(analyze("dar de alta el gimnasio mañana"))
    }

    @Test
    fun `sustantivo baja maternal no captura`() {
        assertNull(analyze("la baja maternal me la dieron ayer"))
    }

    @Test
    fun `baja del coche sustantivo no captura`() {
        assertNull(analyze("la baja del coche está lista"))
    }

    @Test
    fun `objeto bivalente linea telefonica no captura`() {
        assertNull(analyze("dar de baja la línea telefónica mañana"))
    }

    // ─── Regresiones ──────────────────────────────────────────────

    @Test
    fun `ir al gimnasio sigue EXERCISE`() {
        val intent = analyze("ir al gimnasio mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
    }

    @Test
    fun `cobrar la nomina sigue TASK c895b`() {
        val intent = analyze("cobrar la nómina mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `pagar la tarjeta sigue PAYMENT`() {
        val intent = analyze("pagar la tarjeta mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
    }
}
