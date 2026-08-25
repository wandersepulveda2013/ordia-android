package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1055 [renumerado c.1054→c.1055 por colisión cycle-ID con SU
 * c.1054 gatito 6c5e4e3, fijada en re-fetch pre-push]: lateral ABIERTA
 * «cortar(m/s/n) los pelos» (plural) del piso peluquería
 * [ContextIntentEngine.ERRAND_HAIRCUT_FLOOR] — documentada como FUERA
 * en c.842/c.1013 («Acotado deliberado: plural «los pelos» queda
 * FUERA»). «Los pelos» es LA forma coloquial mayoritaria del español
 * caribe/latino (Wander: Santo Domingo) — la cita de peluquería dicha
 * como se habla se perdía en silencio (olvido silencioso P1, hermana
 * de la asimetría dialectal «móvil» c.851 y coloquial «cole» c.850).
 * NULL PRE medido con sonda efímera `/tmp/probe1054/
 * CortarLosPelosPreProbe.kt` (motor real, run_probe.sh): 6/6 formas
 * NULL + envolvente NULL (el candado [imperativeIsWrapped] se alimenta
 * de [ERRAND_FLOORS], fuente única — el plural ni llegaba al análisis
 * por esa ruta) + guards NULL correctos + 3/3 regresiones HIT
 * intactas. Fix lockstep TRES puntos (lección c.616/c.717): piso +
 * cláusula de negación de [imperativeIsNegated] + plantilla de título
 * de [extractTitle], objeto `(?:pelo|cabello)` → `(?:pelos?|cabello)`.
 * CERO keywords nuevas (lección c.751 no aplica: la keyword-OBJETO
 * «pelo» c.842 ya casa «pelos» por substring `contains`, cobertura
 * preexistente medida — mismo argumento que «gato/s» c.1052).
 * Aceptación medida y pineada: «cortarle los pelos al perro» (grooming
 * mascota) captura ERRAND — hermano simétrico del dativo humano
 * «cortarle el pelo al niño» c.1006 (la peluquería del perro es el
 * mismo recado real; PRE era NULL, se pinnea HIT con comentario).
 */
class ContextIntentEngineCortarLosPelosPluralTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Capturas directas (piso, plural) ----

    @Test
    fun `captura reflexiva me plural con fecha`() {
        val i = analyze("cortarme los pelos el sábado")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Cortarme los pelos", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura forma desnuda plural con fecha`() {
        val i = analyze("cortar los pelos mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Cortar los pelos", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura tercera persona se plural`() {
        val i = analyze("cortarse los pelos el viernes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Cortarse los pelos", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura dativo le plural destinatario`() {
        val i = analyze("cortarle los pelos al niño el sábado")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Cortarle los pelos al niño", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura acuse plural`() {
        val i = analyze("vale, cortarme los pelos hoy")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Cortarme los pelos", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura grooming mascota plural aceptado`() {
        // PRE NULL medido; el dativo mascota («cortarle los pelos al
        // perro») es el mismo recado real que el dativo humano
        // «cortarle el pelo al niño» (c.1006) — aceptación pineada.
        val i = analyze("cortarle los pelos al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Cortarle los pelos al perro", i.title)
    }

    @Test
    fun `captura envolvente plural enruta TASK`() {
        // Envolvente ACENTUADA ya enrutaba TASK PRE (RED-pass
        // medido: candado genérico de recordatorio «recuérdame»);
        // la sonda efímera midió NULL la variante SIN tilde
        // («recuerdame…»), que sigue fuera. Pin de regresión.
        val i = analyze("recuérdame cortarme los pelos mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertNotNull(i.dueAt)
    }

    // ---- Guards (deben seguir NULL) ----

    @Test
    fun `guard negacion inmediata plural`() {
        assertNull(analyze("no cortarme los pelos"))
    }

    @Test
    fun `guard pasado plural`() {
        assertNull(analyze("me corté los pelos"))
    }

    @Test
    fun `guard hedge plural`() {
        assertNull(analyze("quizá cortarme los pelos"))
    }

    @Test
    fun `guard nominal plural`() {
        assertNull(analyze("el corte de los pelos"))
    }

    @Test
    fun `guard plan negado plural`() {
        assertNull(analyze("no voy a cortarme los pelos"))
    }

    @Test
    fun `guard objeto comunicacion fuera`() {
        assertNull(analyze("cortar la comunicación"))
    }

    // ---- Regresiones (deben seguir HIT) ----

    @Test
    fun `regresion singular pelo c842`() {
        val i = analyze("cortarme el pelo")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Cortarme el pelo", i.title)
    }

    @Test
    fun `regresion cabello c1013`() {
        val i = analyze("cortar el cabello")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Cortar el cabello", i.title)
    }

    @Test
    fun `regresion dativo singular pelo c1006`() {
        val i = analyze("cortarle el pelo al niño")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Cortarle el pelo al niño", i.title)
    }
}
