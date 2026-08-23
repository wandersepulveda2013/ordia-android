package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Piso c.907 — lateral enclítica del piso c.900: «traerle <objeto> a
 * <persona/lugar>» (dativo pegado al infinitivo). Registrada "a medir" en
 * c.900 (UNA forma por ciclo, doctrina anti-overreach c.615) y medida
 * NULL en la sonda PRE persistida `tools/probe/TraerleObjetoProbe.kt`
 * (5/5 candidatas NULL — declarativa, prefijo temporal, acuse, plural
 * «traerles» —; 7/7 guards NULL; 6/6 regresiones HIT), ejecutada con el
 * motor real vía `tools/run_probe.sh` contra el HEAD c.905.
 *
 * Causa raíz: el piso c.900 exige el verbo desnudo «traer\s» — el
 * pronombre dativo pegado («traerle/traerles») no casa; y la keyword
 * «traer» (subcadena, lección c.751) suma solo ~0.22 < umbral sin piso.
 * El enclítico «le» solo anticipa el destinatario que la ancla dativa
 * «a <destino>» confirma (hermano del «llevarle su cuaderno» c.854 y del
 * «darle las gracias» c.903).
 *
 * Lockstep TRES puntos (lección c.616/c.751):
 * (1) piso `ERRAND_BRING_FLOOR` — verbo «traer(?:les?)?» ADITIVO
 *     (cero reescritura: la forma desnuda casa exactamente igual);
 *     ancla-objeto/datativa y lookahead anti-figurado IDÉNTICOS;
 * (2) keyword-verbo «traer» YA existía en [ContextIntentKind.TASK] y
 *     cubre «traerle/traerles» por subcadena (matching `contains`,
 *     hermana de c.860/c.862/c.888) — CERO cambios en ContextIntent.kt;
 * (3) plantilla de título en [ContextIntentEngine.extractTitle]: verbo
 *     CAPTURADO del match para conservar el enclítico («Traerle el
 *     cargador a Ana»); la forma no enclítica produce el MISMO título
 *     de c.900 (hermano del «medir(?:me)?» c.843, del «dar(?:le)?»
 *     c.903).
 * Cinturón y tirantes: cláusula de negación c.900 en
 * [ContextIntentEngine.imperativeIsNegated] extendida a «no traerle…»
 * (precedente c.854 — el dativo pegado no casa la cláusula genérica).
 *
 * Acotado deliberado (UNA forma por ciclo): la lateral «traerle
 * <objeto>» sin dativo explícito sigue FUERA (medida NULL, GUARD-7).
 */
class ContextIntentEngineTraerleObjetoFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ─── Capturas (RED exacto: el piso + la plantilla de título) ────

    @Test
    fun `traerle objeto a persona captura ERRAND con titulo limpio`() {
        val r1 = analyze("traerle el cargador a Ana mañana")
        assertNotNull("«traerle el cargador a Ana mañana» debe capturar (NULL hasta c.907)", r1)
        assertEquals(ContextIntentKind.ERRAND, r1!!.kind)
        assertEquals("Traerle el cargador a Ana", r1.title)
        assertNotNull("«mañana» debe anclar dueAt", r1.dueAt)

        val r2 = analyze("traerle el libro a Marta el viernes")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.ERRAND, r2!!.kind)
        assertEquals("Traerle el libro a Marta", r2.title)
        assertNotNull("«el viernes» debe anclar dueAt", r2.dueAt)

        // Plural del dativo: «traerles» (a los abuelos).
        val r3 = analyze("traerles las fotos a los abuelos el sábado")
        assertNotNull(r3)
        assertEquals(ContextIntentKind.ERRAND, r3!!.kind)
        assertEquals("Traerles las fotos a los abuelos", r3.title)
        assertNotNull("«el sábado» debe anclar dueAt", r3.dueAt)
    }

    @Test
    fun `prefijo temporal y acuse no ensucian el titulo`() {
        val r1 = analyze("mañana traerle el cuaderno a Irene")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.ERRAND, r1!!.kind)
        assertEquals("Traerle el cuaderno a Irene", r1.title)
        assertNotNull("«mañana» debe anclar dueAt", r1.dueAt)

        val r2 = analyze("vale, traerle las llaves a papá hoy")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.ERRAND, r2!!.kind)
        assertEquals("Traerle las llaves a papá", r2.title)
        assertNotNull("«hoy» debe anclar dueAt", r2.dueAt)
    }

    // ─── Lockstep keyword (verde desde RED: «traer» preexistía) ─────

    @Test
    fun `keyword verbo traer cubre el enclitico por subcadena (lockstep c751)`() {
        assertTrue(
            "keyword-verbo «traer» (preexistente en TASK) cubre «traerle» por subcadena",
            ContextIntentKind.TRIGGER_WORDS.contains("traer")
        )
    }

    // ─── Guards (NULL deseado) — verdes desde RED ───────────────────

    @Test
    fun `negacion y formas no imperativas quedan fuera`() {
        assertNull("«no traerle el cargador a Ana» (negación; cinturón y tirantes c.907)",
            analyze("no traerle el cargador a Ana"))
        assertNull("«quizá le traiga…» (subjuntivo no imperativo)",
            analyze("quizá le traiga el cargador a Ana"))
        assertNull("«le traje… ayer» (pasado; nota de hecho, no compromiso)",
            analyze("le traje el cargador a Ana ayer"))
    }

    @Test
    fun `figurados de traerle quedan fuera (anti-overreach bivalente)`() {
        assertNull("«traerle suerte a la casa» (figurado; lookahead heredado del piso c.900)",
            analyze("traerle suerte a la casa"))
        assertNull("«traerle consecuencias a la empresa» (figurado)",
            analyze("eso puede traerle consecuencias a la empresa"))
        assertNull("«traerle alegría a la familia» (figurado)",
            analyze("traerle alegría a la familia"))
    }

    @Test
    fun `lateral enclitico sin dativo sigue fuera (acotado deliberado c906)`() {
        assertNull("«traerle el cargador mañana» sin dativo explícito (UNA forma por ciclo)",
            analyze("traerle el cargador mañana"))
    }

    // ─── Regresiones (verdes desde RED) ─────────────────────────────

    @Test
    fun `rutas hermanas siguen intactas`() {
        val r1 = analyze("traer el cargador a Ana mañana")
        assertNotNull(r1)
        assertEquals(ContextIntentKind.ERRAND, r1!!.kind)
        assertEquals("Traer el cargador a Ana", r1.title)

        val r2 = analyze("llevarle su cuaderno a Ana")
        assertNotNull(r2)
        assertEquals(ContextIntentKind.ERRAND, r2!!.kind)

        val r3 = analyze("devolver el libro a la biblioteca")
        assertNotNull(r3)
        assertEquals(ContextIntentKind.ERRAND, r3!!.kind)

        val r4 = analyze("llamar a Ana mañana")
        assertNotNull(r4)
        assertEquals(ContextIntentKind.CALL, r4!!.kind)
    }

    @Test
    fun `envolvente recuerdame traerle sigue ganando TASK (guard c652)`() {
        val r = analyze("recuérdame traerle el cargador a Ana mañana")
        assertNotNull(r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertNotNull("«mañana» debe anclar dueAt", r.dueAt)
    }
}
