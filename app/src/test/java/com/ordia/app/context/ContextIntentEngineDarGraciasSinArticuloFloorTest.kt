package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Piso c.904 — lateral sin artículo del piso c.901: «dar gracias a
 * <persona> (por <objeto>)» (SIN «las»). Medida NULL en la sonda
 * hermana persistida `tools/probe/DarLasGraciasProbe.kt` c.901 (2/2
 * laterales NULL) y registrada en BACKLOG (UNA forma por ciclo,
 * doctrina anti-overreach); NULL PRE re-medido en este ciclo con la
 * sonda NUEVA persistida `tools/probe/DarGraciasSinArticuloProbe.kt`
 * (4/4 candidatas NULL, 1/1 lateral enclítico-sin-artículo NULL a
 * medir, 6/6 guards NULL, 6/6 regresiones HIT) sobre HEAD c.903
 * (suite base 5976 OK).
 *
 * Causa raíz: el piso c.901 exige el artículo «las» («dar las
 * gracias»); la forma pelada «dar gracias a Ana por el regalo» — tan
 * cotidiana como la hermana — rompe la cadena (ni el piso ni la
 * keyword-frase casan, lección c.751) y se DESCARTABA. La envolvente
 * («recuérdame dar gracias a Ana…») ya capturaba vía el piso TASK
 * c.613 (rendija pasiva↔manual, misma clase que c.583/c.893/c.900/
 * c.901/c.903).
 *
 * Bivalencia MEDIDA (decisión conservadora): la forma sin artículo
 * es la habitual de las figuradas/religiosas («dar gracias a Dios»,
 * «dar gracias a la vida», «dar gracias al cielo») — el piso lleva
 * guard anti-figurado `(?!dios|la vida|vida|cielo)` SOLO en la rama
 * sin artículo; la rama «las» (c.901/c.902/c.903) NO cambia (cero
 * reescritura).
 *
 * Extensión ADITIVA del lockstep hermano c.901/c.903 (lección
 * c.616/c.751):
 * (1) piso — segunda rama «gracias\s+a(?:l)?\s+(?!figurado)\w»
 *     (artículo opcional; guard `(?<!no )` compartido);
 * (2) keyword-FRASE «dar gracias» en [ContextIntentKind.TASK]
 *     (0.12 sola inerte < umbral: «dar gracias» suelto sin destino
 *     sigue NULL — el piso exige la dativa «a <destino>»);
 * (3) plantilla de título — el artículo se CAPTURA del match
 *     («las\s+» opcional) y se preserva la forma del usuario:
 *     «Dar gracias a Ana…» / «Dar las gracias a Ana…» (grafía
 *     preservada, doctrina c.653).
 *
 * Acotado deliberado (UNA forma por ciclo, doctrina anti-overreach
 * c.615): la lateral enclítico-sin-artículo «darle gracias a…»
 * medida NULL en la sonda (LAT-1) — RESUELTA en c.905 (su guarda NULL
 * de esta clase pasa a captura intencional, precedente c.882/c.893/
 * c.903); con ella la matriz enclítico × artículo queda AGOTADA.
 */
class ContextIntentEngineDarGraciasSinArticuloFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ─── Capturas (rojas en RED) ────────────────────────────────────

    @Test
    fun `captura forma sin articulo con objeto`() {
        val r = analyze("dar gracias a Ana por el regalo")
        assertNotNull("«dar gracias a Ana por el regalo» (lateral medida NULL PRE)",
            r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Dar gracias a Ana por el regalo", r.title)
        assertNull("sin marcador temporal no hay dueAt", r.dueAt)
    }

    @Test
    fun `prefijo temporal y titulo limpio`() {
        val r = analyze("mañana dar gracias a Irene")
        assertNotNull("prefijo temporal anclado por el piso", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Dar gracias a Irene", r.title)
        assertNotNull("«mañana» debe anclar dueAt", r.dueAt)
    }

    @Test
    fun `keyword frase dar gracias llega a TRIGGER_WORDS (lockstep c751)`() {
        // Lección c.751: sin la keyword-frase la notificación ni llega
        // al análisis. 0.12 sola inerte < umbral (el piso exige la
        // dativa «a <destino>»).
        assertTrue(
            "keyword-frase «dar gracias» (lockstep c.904 vía c.751 — la forma pelada no contiene «dar las gracias» c.901)",
            ContextIntentKind.TRIGGER_WORDS.contains("dar gracias")
        )
    }

    // ─── Guards (verdes desde RED) ──────────────────────────────────

    @Test
    fun `negacion y formas no imperativas quedan fuera`() {
        assertNull("«no dar gracias a Ana» (negación; guard del piso)",
            analyze("no dar gracias a Ana"))
        assertNull("«quizá dé gracias a Ana» (subjuntivo no imperativo)",
            analyze("quizá dé gracias a Ana"))
        assertNull("«di gracias a Ana ayer» (pasado; nota de hecho, no compromiso)",
            analyze("di gracias a Ana ayer"))
        assertNull("«dar gracias» suelto (sin destino; ancla dativa exigida)",
            analyze("dar gracias"))
    }

    @Test
    fun `figuradas quedan fuera (decision conservadora medida)`() {
        assertNull("«dar gracias a Dios» (figurada/religiosa; guard anti-figurado)",
            analyze("dar gracias a Dios"))
        assertNull("«dar gracias a la vida por todo» (figurada; guard anti-figurado)",
            analyze("dar gracias a la vida por todo"))
        assertNull("«dar gracias al cielo» (figurada; guard anti-figurado)",
            analyze("dar gracias al cielo"))
    }

    // ─── Lateral resuelta en c.905 (captura intencional; antes guarda
    // NULL de ESTA clase, actualizada deliberadamente — precedente
    // c.882/c.893/c.903) ─────────────────────────────────────────────

    @Test
    fun `lateral enclitico sin articulo captura desde c905`() {
        val r = analyze("darle gracias a Ana por el regalo")
        assertNotNull("«darle gracias a Ana…» (enclítico sin artículo; resuelta c.905)",
            r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Darle gracias a Ana por el regalo", r.title)
    }

    // ─── Regresiones (verdes desde RED) ─────────────────────────────

    @Test
    fun `pisos hermanos c901 c902 c903 y vecinos siguen intactos`() {
        val r1 = analyze("dar las gracias a Ana por el regalo")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.TASK, r1!!.kind)
        assertEquals("Dar las gracias a Ana por el regalo", r1.title)

        val r2 = analyze("darle las gracias a Ana por el regalo")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.TASK, r2!!.kind)
        assertEquals("Darle las gracias a Ana por el regalo", r2.title)

        val r3 = analyze("dar las gracias al jefe por el ascenso hoy")
        assertNotNull(r3)
        assertEquals(ContextIntentKind.TASK, r3!!.kind)
        assertNotNull(r3.dueAt)

        val r4 = analyze("avisar a mamá de la cita mañana")
        assertNotNull(r4)
        assertEquals(ContextIntentKind.TASK, r4!!.kind)

        val r5 = analyze("llamar a Ana mañana")
        assertNotNull(r5)
        assertEquals(ContextIntentKind.CALL, r5!!.kind)
    }

    @Test
    fun `envolvente recuerdame sigue ganando TASK (guard c613)`() {
        // El imperativo envolvente gobierna: el verbo subordinado es
        // CONTENIDO del recordatorio, no una acción autónoma.
        val r = analyze("recuérdame dar gracias a Ana mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertNotNull("«mañana» debe anclar dueAt", r.dueAt)
    }
}
