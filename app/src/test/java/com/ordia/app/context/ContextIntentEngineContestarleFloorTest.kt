package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Dativo enclítico c.879 — «contestarle a <persona>»: lateral c.861
 * (hermanos c.861/c.872/c.873 ya cubren «contestar a», «contestar al/a la»
 * y «contestar <objeto>»). El enclítico intercalado rompía la subcadena de
 * la keyword «contestar a» y el piso no dibuja `le`. Consecuencia real:
 * comunicación pendiente olvidada.
 *
 * Lockstep TRES puntos (lección c.616/c.751): (1) piso con `(?:les?)?`
 * opcional en `hasStrongTaskImperative`; (2) plantilla en `extractTitle`
 * (mayúscula inicial de la forma dativa — la grafía del usuario se preserva,
 * doctrina c.653); (3) keyword-FRASE «contestarle» en `ContextIntent.kt`
 * (cubre el plural por subcadena). Guardas bivalentes idénticas que c.861:
 * «al examen», «a la pregunta», «a tiempo» quedan FUERA, y las formas con
 * artículo bloqueado («a los vecinos») se tratan como guard-NULL, igual que
 * en la forma desnuda (precedente c.861).
 *
 * Sonda PRE/POST efímera `/tmp/probe878/PreContestarleProbe.kt` sobre HEAD
 * 55d9d8b: 4/4 candidatas NULL→HIT, 4/4 guards NULL, 3/3 regresiones HIT.
 */
class ContextIntentEngineContestarleFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    @Test
    fun `contestarle a juan captura`() {
        val r = analyze("contestarle a juan mañana")
        assertNotNull("«contestarle a juan mañana» (era NULL en sonda PRE)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Contestarle a juan", r.title)
        assertNotNull("«mañana» debe anclar dueAt", r.dueAt)
    }

    @Test
    fun `contestarle a la vecina captura`() {
        val r = analyze("contestarle a la vecina")
        assertNotNull("«contestarle a la vecina» (era NULL)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Contestarle a la vecina", r.title)
    }

    @Test
    fun `contestarle a poseído captura`() {
        val r = analyze("contestarle a tu jefe esta tarde")
        assertNotNull("«contestarle a tu jefe» (era NULL)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Contestarle a tu jefe", r.title)
    }

    // -- Guardas (esperado: NULL) --
    @Test
    fun `negada descarta`() {
        assertNull(analyze("no contestarle a juan"))
    }

    @Test
    fun `bivalente al examen fuera`() {
        assertNull(analyze("contestarle al examen"))
    }

    @Test
    fun `bivalente a la pregunta fuera`() {
        assertNull(analyze("contestarle a la pregunta"))
    }

    @Test
    fun `artículo bloqueado a los vecinos guard NULL`() {
        assertNull(analyze("contestarles a los vecinos el lunes"))
    }

    // -- Regresiones hermanas (esperado: HIT propio) --
    @Test
    fun `regresión hermano c861 desnuda captura`() {
        val r = analyze("contestar a juan mañana")
        assertNotNull("hermano c.861 debe seguir HIT", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }

    @Test
    fun `regresión hermano c873 objeto captura`() {
        val r = analyze("contestar el correo de ana hoy")
        assertNotNull("hermano c.873 debe seguir HIT", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }

    @Test
    fun `envolvente candado c613 intacta`() {
        val r = analyze("recuérdame contestarle a juan mañana")
        assertNotNull("envolvente con candado c.613 debe seguir HIT", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }
}
