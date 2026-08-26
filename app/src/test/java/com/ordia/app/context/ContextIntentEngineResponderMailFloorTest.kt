package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Keyword sinónima «mail» c.1182 — candidata (b) de la auditoría c.1173
 * (clase DECIMONOVENA, comunicaciones pendientes): «responder el mail (de
 * <ámbito>) (esta noche)» medida NULL mientras la hermana «responder el
 * correo» SÍ captura (c.860). «mail» es el anglicismo dominante en el
 * español hablado real para el correo electrónico; el correo sin
 * responder es el olvido laboral canónico.
 *
 * Lockstep TRES puntos (lección c.616/c.751, precedente c.867 «email»):
 * (1) keyword-OBJETO «mail» en `ContextIntent.kt` ERRAND (sin ella la
 * frase ni llega al análisis; subcadena, cubre plural; 0.12 sola inerte
 * < umbral); (2) objeto `mails?` en el piso «responder…» de
 * `hasStrongTaskImperative`; (3) MISMO objeto en la plantilla
 * `matchResponderCorreo` de `extractTitle` (grafía preservada c.653).
 *
 * Sonda persistida `tools/probe/NineteenthClassCommsProbe.kt`: PRE
 * candidata C13 «responder el mail de trabajo esta noche» → NULL
 * (re-medida sobre HEAD post-integración c.1177). Hermanas NO
 * implementadas en este ciclo (doctrina UNA candidata por ciclo, mismo
 * criterio que c.1177): «contestar el mail…» queda lateral ABIERTA.
 */
class ContextIntentEngineResponderMailFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    @Test
    fun `responder el mail singular captura`() {
        val r = analyze("responder el mail de trabajo esta noche")
        assertNotNull("«responder el mail de trabajo esta noche» (era NULL en sonda PRE C13)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Responder el mail de trabajo", r.title)
        assertNotNull("«esta noche» debe anclar dueAt", r.dueAt)
    }

    @Test
    fun `responder los mails plural captura`() {
        val r = analyze("responder los mails del cliente")
        assertNotNull("«responder los mails del cliente» (era NULL)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Responder los mails del cliente", r.title)
    }

    @Test
    fun `responder mi mail posesivo captura`() {
        val r = analyze("responder mi mail mañana")
        assertNotNull("«responder mi mail mañana» (era NULL)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Responder mi mail", r.title)
        assertNotNull("«mañana» debe anclar dueAt", r.dueAt)
    }

    @Test
    fun `responder el mail con prefijo de asentimiento captura`() {
        val r = analyze("vale, responder el mail del banco hoy")
        assertNotNull("«vale, responder el mail del banco hoy» (era NULL)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Responder el mail del banco", r.title)
    }

    @Test
    fun `responder el mail con prefijo temporal captura`() {
        val r = analyze("esta noche responder el mail de recursos humanos")
        assertNotNull("«esta noche responder el mail de recursos humanos» (era NULL)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Responder el mail de recursos humanos", r.title)
        assertNotNull("«esta noche» debe anclar dueAt", r.dueAt)
    }

    // -- Guardas (esperado: NULL) --
    @Test
    fun `negada descarta`() {
        assertNull(analyze("no respondas el mail del jefe"))
    }

    @Test
    fun `pasada descarta`() {
        assertNull(analyze("ayer respondí el mail de ana"))
    }

    @Test
    fun `estado recibido no es compromiso propio`() {
        assertNull(analyze("me llegó un mail del banco"))
    }

    @Test
    fun `keyword mail sola inerte sin piso`() {
        assertNull(analyze("el mail llegó vacío"))
    }

    // -- Regresiones hermanas (esperado: HIT propio) --
    @Test
    fun `regresión hermano c860 responder correo captura`() {
        val r = analyze("responder el correo de ana hoy")
        assertNotNull("hermano c.860 debe seguir HIT", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }

    @Test
    fun `regresión hermano c867 responder email captura`() {
        val r = analyze("responder el email del seguro mañana")
        assertNotNull("hermano c.867 debe seguir HIT", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }

    @Test
    fun `regresión c1177 contestar whatsapp captura`() {
        val r = analyze("contestar el WhatsApp de Marta esta noche")
        assertNotNull("mi c.1177 debe seguir HIT", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }
}
