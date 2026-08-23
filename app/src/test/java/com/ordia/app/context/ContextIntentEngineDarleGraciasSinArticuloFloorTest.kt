package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Piso c.905 — lateral FINAL de la familia «dar (las) gracias»:
 * «darle gracias a <persona> (por <objeto>)» (enclítico SIN artículo
 * «las»). Registrada a medir en c.901, medida NULL en las sondas
 * hermanas persistidas c.903 (`tools/probe/DarleLasGraciasProbe.kt`,
 * LAT) y c.904 (`tools/probe/DarGraciasSinArticuloProbe.kt`, LAT-1);
 * NULL PRE re-medido en este ciclo con la sonda NUEVA persistida
 * `tools/probe/DarleGraciasSinArticuloProbe.kt` (5/5 candidatas NULL,
 * 7/7 guards NULL, 6/6 regresiones HIT) sobre HEAD c.904 (suite base
 * 5984 OK). Es la ÚLTIMA combinación de la matriz enclítico × artículo
 * (c.901 articulada, c.902 «al», c.903 enclítica articulada, c.904 sin
 * artículo): con ella la familia queda AGOTADA.
 *
 * Causa raíz: el piso c.904 excluye deliberadamente esta forma con el
 * lookbehind `(?<!darle\s)` de la rama 2 (acotado anti-overreach de
 * UNA forma por ciclo); sin keyword-frase propia («darle gracias» no
 * contiene «dar gracias» ni «dar las gracias», lección c.751) la
 * notificación ni llegaba al análisis y se DESCARTABA. La envolvente
 * («recuérdame darle gracias a Ana…») ya capturaba vía el piso TASK
 * c.613 (rendija pasiva↔manual, misma clase que c.583/c.893/c.900/
 * c.901/c.903/c.904).
 *
 * Bivalencia MEDIDA heredada de c.904: la forma sin artículo (con o
 * sin enclítico) es la habitual de las figuradas/religiosas («darle
 * gracias a Dios», «darle gracias a la vida», «darle gracias al
 * cielo») — el guard anti-figurado `(?!dios|la vida|vida|cielo)` de la
 * rama 2 sigue protegiéndolas (medido en la sonda PRE: 7/7 NULL).
 *
 * Extensión ADITIVA del lockstep hermano c.901/c.903/c.904 (lección
 * c.616/c.751):
 * (1) piso — se retira el lookbehind `(?<!darle\s)` de la rama 2 (el
 *     enclítico solo anticipa el destinatario que la dativa confirma,
 *     hermano del «darle las gracias» c.903); la rama «las» y el guard
 *     anti-figurado casan exactamente igual (cero reescritura);
 * (2) keyword-FRASE «darle gracias» en [ContextIntentKind.TASK]
 *     (0.12 sola inerte < umbral: «darle gracias» suelto sin destino
 *     sigue NULL — el piso exige la dativa «a <destino>»);
 * (3) plantilla de título — se retira el mismo lookbehind para que el
 *     enclítico capturado en el grupo 1 se preserve: «Darle gracias a
 *     Ana…» (grafía preservada, doctrina c.653; la envolvente c.613 ya
 *     producía ese mismo título por la vía genérica).
 */
class ContextIntentEngineDarleGraciasSinArticuloFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ─── Capturas (rojas en RED) ────────────────────────────────────

    @Test
    fun `captura forma enclitica sin articulo con objeto`() {
        val r = analyze("darle gracias a Ana por el regalo")
        assertNotNull("«darle gracias a Ana por el regalo» (lateral medida NULL PRE)",
            r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Darle gracias a Ana por el regalo", r.title)
        assertNull("sin marcador temporal no hay dueAt", r.dueAt)
    }

    @Test
    fun `prefijo temporal y titulo limpio`() {
        val r = analyze("mañana darle gracias a Irene")
        assertNotNull("prefijo temporal anclado por el piso", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Darle gracias a Irene", r.title)
        assertNotNull("«mañana» debe anclar dueAt", r.dueAt)
    }

    @Test
    fun `contraccion al tambien ancla el destinatario`() {
        val r = analyze("darle gracias al jefe por el ascenso")
        assertNotNull("la contracción «al» ancla (hermana de c.902)", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Darle gracias al jefe por el ascenso", r.title)
    }

    @Test
    fun `keyword frase darle gracias llega a TRIGGER_WORDS (lockstep c751)`() {
        // Lección c.751: sin la keyword-frase la notificación ni llega
        // al análisis. 0.12 sola inerte < umbral (el piso exige la
        // dativa «a <destino>»).
        assertTrue(
            "keyword-frase «darle gracias» (lockstep c.905 vía c.751 — el enclítico rompe «dar gracias» c.904)",
            ContextIntentKind.TRIGGER_WORDS.contains("darle gracias")
        )
    }

    // ─── Guards (verdes desde RED; deben SEGUIR NULL en GREEN) ──────

    @Test
    fun `negacion y formas no imperativas quedan fuera`() {
        assertNull("«no darle gracias a Ana» (negación; guard del piso)",
            analyze("no darle gracias a Ana"))
        assertNull("«quizá déle gracias a Ana» (subjuntivo no imperativo)",
            analyze("quizá déle gracias a Ana"))
        assertNull("«le di gracias a Ana ayer» (pasado; nota de hecho, no compromiso)",
            analyze("le di gracias a Ana ayer"))
        assertNull("«darle gracias» suelto (sin destino; ancla dativa exigida)",
            analyze("darle gracias"))
    }

    @Test
    fun `figuradas quedan fuera (decision conservadora medida)`() {
        assertNull("«darle gracias a Dios» (figurada/religiosa; guard anti-figurado)",
            analyze("darle gracias a Dios"))
        assertNull("«darle gracias a la vida por todo» (figurada; guard anti-figurado)",
            analyze("darle gracias a la vida por todo"))
        assertNull("«darle gracias al cielo» (figurada; guard anti-figurado)",
            analyze("darle gracias al cielo"))
    }

    // ─── Regresiones (verdes desde RED) ─────────────────────────────

    @Test
    fun `pisos hermanos c901 c902 c903 c904 y vecinos siguen intactos`() {
        val r1 = analyze("dar las gracias a Ana por el regalo")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.TASK, r1!!.kind)
        assertEquals("Dar las gracias a Ana por el regalo", r1.title)

        val r2 = analyze("darle las gracias a Ana por el regalo")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.TASK, r2!!.kind)
        assertEquals("Darle las gracias a Ana por el regalo", r2.title)

        val r3 = analyze("dar gracias a Ana por el regalo")
        assertNotNull(r3)
        assertEquals(ContextIntentKind.TASK, r3!!.kind)
        assertEquals("Dar gracias a Ana por el regalo", r3.title)

        val r4 = analyze("dar las gracias al jefe por el ascenso hoy")
        assertNotNull(r4)
        assertEquals(ContextIntentKind.TASK, r4!!.kind)
        assertNotNull(r4.dueAt)

        val r5 = analyze("avisar a mamá de la cita mañana")
        assertNotNull(r5)
        assertEquals(ContextIntentKind.TASK, r5!!.kind)

        val r6 = analyze("llamar a Ana mañana")
        assertNotNull(r6)
        assertEquals(ContextIntentKind.CALL, r6!!.kind)
    }

    @Test
    fun `envolvente recuerdame sigue ganando TASK (guard c613)`() {
        // El imperativo envolvente gobierna: el verbo subordinado es
        // CONTENIDO del recordatorio, no una acción autónoma.
        val r = analyze("recuérdame darle gracias a Ana mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Darle gracias a Ana", r.title)
        assertNotNull("«mañana» debe anclar dueAt", r.dueAt)
    }
}
