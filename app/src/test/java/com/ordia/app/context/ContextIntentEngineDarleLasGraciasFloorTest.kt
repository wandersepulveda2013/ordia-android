package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Piso c.903 — lateral enclítica del piso c.901: «darle las gracias a
 * <persona> (por <objeto>)». Medida NULL en la sonda hermana persistida
 * `tools/probe/DarLasGraciasProbe.kt` c.901 (2/2 laterales NULL) y
 * registrada en BACKLOG (UNA forma por ciclo, doctrina anti-overreach);
 * NULL PRE re-medido en este ciclo con la sonda NUEVA persistida
 * `tools/probe/DarleLasGraciasProbe.kt` (5/5 candidatas NULL, 4/4
 * guards NULL, 1/1 lateral NULL, 7/7 regresiones HIT) sobre HEAD c.901
 * (suite base 5968 OK).
 *
 * Causa raíz: el piso c.901 se ancla a la forma EXACTA «dar las
 * gracias» y el enclítico «darle» rompe la cadena («darle las gracias»
 * no contiene «dar las gracias»): ni el piso ni la keyword-frase casan
 * (lección c.751) y la forma se DESCARTABA. El enclítico dativo
 * «darle» es la forma MÁS cotidiana del habla («darle las gracias a
 * Ana por el regalo»), hermana de «llevarle su cuaderno a Ana» c.854 —
 * el usuario capturaba el agradecimiento pendiente en su forma natural
 * y Ordía lo olvidaba. La envolvente («recuérdame darle las gracias a
 * Ana…») ya capturaba vía el piso TASK c.613 (rendija pasiva↔manual,
 * misma clase que c.583/c.893/c.900/c.901).
 *
 * Decisión de dominio heredada de c.901 (sin redeliberar): TASK —
 * comunicación de gratitud pendiente, gestión interpersonal SIN
 * desplazamiento físico (la doctrina ERRAND c.842/c.862 gobierna solo
 * el desplazamiento). Mismo verbo-frase cuasi-monosemántico; el
 * enclítico «le» solo anticipa el destinatario que la ancla dativa
 * «a <destino>» confirma.
 *
 * Extensión ADITIVA del lockstep hermano c.901 (cero reescritura,
 * lección c.616/c.751):
 * (1) piso c.901 ampliado — verbo «dar(?:le)?» (enclítico opcional)
 *     + ancla dativa «a <destino>» + guard `(?<!no )` idéntico;
 * (2) keyword-FRASE «darle las gracias» en [ContextIntentKind.TASK]
 *     (lección c.751: «darle las gracias» NO contiene «dar las
 *     gracias», sin la frase la notificación ni llega al análisis;
 *     0.12 sola inerte < umbral y el piso exige la dativa);
 * (3) plantilla de título c.901 ampliada — el verbo se captura del
 *     match y se preserva la forma del usuario: «Darle las gracias a
 *     Ana…» (grafía preservada, doctrina c.653; hermano del
 *     «medir(?:me)?» c.843, que capitaliza el verbo DESDE el match).
 * La negada la cubre el guard `(?<!no )` del piso («no darle las
 * gracias a Ana»); la keyword 0.12 + bono temporal 0.1 = 0.22 <
 * umbral: no hace falta cláusula dedicada en
 * [ContextIntentEngine.imperativeIsNegated] (mismo argumento medido
 * que c.895b/c.895c/c.901).
 *
 * Acotado deliberado (UNA forma por ciclo, doctrina anti-overreach
 * c.615): la lateral sin artículo «dar gracias a…» medida NULL en la
 * sonda — queda registrada para ciclos futuros.
 */
class ContextIntentEngineDarleLasGraciasFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ─── Capturas (RED exacto: el piso + la plantilla de título) ────

    @Test
    fun `darle las gracias a persona captura TASK con titulo limpio`() {
        val r1 = analyze("darle las gracias a Ana por el regalo")
        assertNotNull("«darle las gracias a Ana por el regalo» debe capturar (NULL hasta c.903)", r1)
        assertEquals(ContextIntentKind.TASK, r1!!.kind)
        assertEquals("Darle las gracias a Ana por el regalo", r1.title)

        val r2 = analyze("darle las gracias a Marta mañana")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.TASK, r2!!.kind)
        assertEquals("Darle las gracias a Marta", r2.title)
        assertNotNull("«mañana» debe anclar dueAt", r2.dueAt)

        // Plural: el destinatario colectivo se preserva (doctrina c.653).
        val r3 = analyze("darle las gracias a los vecinos el viernes")
        assertNotNull(r3)
        assertEquals(ContextIntentKind.TASK, r3!!.kind)
        assertEquals("Darle las gracias a los vecinos", r3.title)
        assertNotNull("«el viernes» debe anclar dueAt", r3.dueAt)
    }

    @Test
    fun `prefijo temporal y acuse no ensucian el titulo`() {
        // Prefijo temporal: el match arranca en el verbo (lección c.616).
        val r1 = analyze("mañana darle las gracias a Irene")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.TASK, r1!!.kind)
        assertEquals("Darle las gracias a Irene", r1.title)
        assertNotNull("«mañana» debe anclar dueAt", r1.dueAt)

        // Acuse despojado del título + residuo temporal de cola depurado.
        val r2 = analyze("vale, darle las gracias a papá por el favor hoy")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.TASK, r2!!.kind)
        assertEquals("Darle las gracias a papá por el favor", r2.title)
        assertNotNull("«hoy» debe anclar dueAt", r2.dueAt)
    }

    // ─── Lockstep keyword (RED: la frase no existía) ────────────────

    @Test
    fun `keyword frase darle las gracias llega a TRIGGER_WORDS (lockstep c751)`() {
        assertTrue(
            "keyword-frase «darle las gracias» (lockstep c.903 vía c.751 — el enclítico rompe la cadena «dar las gracias» c.901)",
            ContextIntentKind.TRIGGER_WORDS.contains("darle las gracias")
        )
    }

    // ─── Guards (NULL deseado) — verdes desde RED ───────────────────

    @Test
    fun `negacion y formas no imperativas quedan fuera`() {
        assertNull("«no darle las gracias a Ana» (negación; guard del piso)",
            analyze("no darle las gracias a Ana"))
        assertNull("«quizá le dé las gracias a Ana» (subjuntivo no imperativo)",
            analyze("quizá le dé las gracias a Ana"))
        assertNull("«le di las gracias a Ana ayer» (pasado; nota de hecho, no compromiso)",
            analyze("le di las gracias a Ana ayer"))
        assertNull("«darle las gracias» suelto (sin destino; ancla dativa exigida)",
            analyze("darle las gracias"))
    }

    // ─── Lateral a medir (NULL deliberado, NO de este ciclo) ────────

    @Test
    fun `lateral sin articulo queda a medir (anti-overreach)`() {
        assertNull("«dar gracias a Ana…» (sin artículo; lateral registrada)",
            analyze("dar gracias a Ana por el regalo"))
    }

    // ─── Regresiones (verdes desde RED) ─────────────────────────────

    @Test
    fun `forma no enclitica c901 y pisos vecinos siguen intactos`() {
        val r1 = analyze("dar las gracias a Ana por el regalo")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.TASK, r1!!.kind)
        assertEquals("Dar las gracias a Ana por el regalo", r1.title)

        val r2 = analyze("avisar a mamá de la cita mañana")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.TASK, r2!!.kind)

        val r3 = analyze("llamar a Ana mañana")
        assertNotNull(r3)
        assertEquals(ContextIntentKind.CALL, r3!!.kind)

        val r4 = analyze("dar de baja el gimnasio")
        assertNotNull(r4)
        assertEquals(ContextIntentKind.TASK, r4!!.kind)

        val r5 = analyze("llevarle su cuaderno a Ana")
        assertNotNull(r5)
        assertEquals(ContextIntentKind.ERRAND, r5!!.kind)
    }

    @Test
    fun `envolvente recuerdame sigue ganando TASK (guard c613)`() {
        // El imperativo envolvente gobierna: el verbo subordinado es
        // CONTENIDO del recordatorio, no una acción autónoma.
        val r = analyze("recuérdame darle las gracias a Ana mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertNotNull("«mañana» debe anclar dueAt", r.dueAt)
    }
}
