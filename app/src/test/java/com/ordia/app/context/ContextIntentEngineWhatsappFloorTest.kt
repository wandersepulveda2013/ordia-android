package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Objeto «whatsapp» c.1177 — candidata (a) FUERTE de la auditoría c.1173
 * (clase DECIMONOVENA, comunicaciones pendientes): «contestar el WhatsApp
 * de <persona> (esta noche)» medida NULL por asimetría de keyword — la
 * hermana «contestar el correo» SÍ captura (c.873). Consecuencia real:
 * el WhatsApp sin contestar es el olvido social canónico moderno.
 *
 * Lockstep TRES puntos (lección c.616/c.751, patrón c.880 «carta»):
 * (1) piso con objeto `whatsapps?` en `hasStrongTaskImperative`;
 * (2) plantilla `extractTitle` (grafía preservada, doctrina c.653);
 * (3) keyword-OBJETO «whatsapp» en `ContextIntent.kt` (sin ella la frase
 * ni llega al análisis: «contestar el whatsapp» no contiene «contestar a»
 * ni «contestarle» ni «correo»).
 *
 * Sonda persistida `tools/probe/NineteenthClassCommsProbe.kt`: PRE candidata
 * C3 «contestar el WhatsApp de Marta esta noche» → NULL (medida sobre
 * 9220964 y re-medida post-integración). Hermanas NO implementadas en este
 * ciclo (doctrina UNA candidata por ciclo): «responder el whatsapp…» y las
 * grafías coloquiales «wasap»/«wassap» quedan como laterales ABIERTAS.
 */
class ContextIntentEngineWhatsappFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    @Test
    fun `contestar el whatsapp singular captura`() {
        val r = analyze("contestar el whatsapp de marta esta noche")
        assertNotNull("«contestar el whatsapp de marta esta noche» (era NULL en sonda PRE)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Contestar el whatsapp de marta", r.title)
        assertNotNull("«esta noche» debe anclar dueAt", r.dueAt)
    }

    @Test
    fun `contestar el whatsapp preserva grafía original`() {
        val r = analyze("Contestar el WhatsApp de Marta")
        assertNotNull("«Contestar el WhatsApp de Marta» (era NULL)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Contestar el WhatsApp de Marta", r.title)
    }

    @Test
    fun `contestar los whatsapps plural captura`() {
        val r = analyze("contestar los whatsapps del grupo")
        assertNotNull("«contestar los whatsapps del grupo» (era NULL)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Contestar los whatsapps del grupo", r.title)
    }

    @Test
    fun `contestar mi whatsapp posesivo captura`() {
        val r = analyze("contestar mi whatsapp esta tarde")
        assertNotNull("«contestar mi whatsapp esta tarde» (era NULL)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Contestar mi whatsapp", r.title)
        assertNotNull("«esta tarde» debe anclar dueAt", r.dueAt)
    }

    @Test
    fun `contestarle el whatsapp dativo captura`() {
        val r = analyze("contestarle el whatsapp a pedro mañana")
        assertNotNull("«contestarle el whatsapp a pedro mañana» (era NULL)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Contestarle el whatsapp a pedro", r.title)
    }

    // -- Guardas (esperado: NULL) --
    @Test
    fun `negada descarta`() {
        assertNull(analyze("no contestar el whatsapp"))
    }

    @Test
    fun `pasada descarta`() {
        assertNull(analyze("ya contesté el whatsapp de ana"))
    }

    @Test
    fun `estado recibido no es compromiso propio`() {
        assertNull(analyze("me llegó un whatsapp de ana"))
    }

    @Test
    fun `estado sonado no es compromiso propio`() {
        assertNull(analyze("el whatsapp sonó dos veces"))
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
    fun `regresión hermano c861 contestar a persona captura`() {
        val r = analyze("contestar a juan esta tarde")
        assertNotNull("hermano c.861 debe seguir HIT", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }

    @Test
    fun `regresión guard bivalente examen sigue en STUDY`() {
        // El guard «al examen» lo saca del piso contestar, pero la keyword
        // «examen» lo enruta a STUDY (comportamiento heredado; medido en RED:
        // STUDY 0.47 «Estudio»). El fix NO debe cambiarlo.
        val r = analyze("contestar al examen mañana")
        assertNotNull("«contestar al examen» sigue enrutado a STUDY", r)
        assertEquals(ContextIntentKind.STUDY, r!!.kind)
    }
}
