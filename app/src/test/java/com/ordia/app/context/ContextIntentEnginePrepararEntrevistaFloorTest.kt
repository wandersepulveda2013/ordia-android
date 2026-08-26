package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * c.1155: candidata (d) FUERTE de la clase DECIMOSÉPTIMA (vida laboral
 * dicho-como-se-habla), auditoría persistida c.1147 en
 * `tools/probe/SeventeenthClassWorkProbe.kt` C4 — «preparar la entrevista
 * (de trabajo)» medida NULL 4/4 en PRE sobre HEAD e8f4acd con sonda
 * efímera (keyword EVENT «entrevista» 0.12 + bono temporal 0.1 = 0.22 <
 * umbral). Una entrevista perdida es una oportunidad perdida (coste
 * hermano del check-in c.1140).
 *
 * Fix: piso acotado «preparar (det)? entrevistas?» en
 * `hasStrongTaskImperative` (adyacente al piso maleta c.827, cluster
 * temático «preparar») + plantilla `matchPrepararEntrevista` en
 * `extractTitle`. CERO keywords nuevas («entrevista» ya es keyword
 * EVENT). Kind TASK (preparación previa con plazo, hermana de
 * «preparar la maleta» c.827 — el EVENT es el evento en sí).
 *
 * Guards pineados NULL (medidos PRE): negación, pretérito «preparé»,
 * subjuntivo-duda «quizá prepare», nominal, copulativa pretérito y
 * presente habitual «preparo».
 */
class ContextIntentEnginePrepararEntrevistaFloorTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---------- captura (4/4 NULL en PRE) ----------

    @Test
    fun `preparar la entrevista de mañana captura TASK resolviendo el genitivo a dia siguiente`() {
        val r = analyze("preparar la entrevista de mañana")!!
        assertEquals(ContextIntentKind.TASK, r.kind)
        assertTrue(r.confidence >= 0.45f)
        // c.1188: el genitivo-temporal «de mañana» ya se resuelve a día
        // siguiente (antes el «de» desnudo de mananaSuffix lo suprimía y
        // nacía SIN dueAt — olvido silencioso P1, sonda
        // `tools/probe/GenitivoDeMananaEngineProbe.kt`) y el título se
        // despoja del residuo en lockstep (excepción del guard c.690).
        assertTrue(r.dueAt != null)
        assertEquals("Preparar la entrevista", r.title)
    }

    @Test
    fun `preparar la entrevista del lunes captura TASK con dueAt`() {
        val r = analyze("preparar la entrevista del lunes")!!
        assertEquals(ContextIntentKind.TASK, r.kind)
        assertTrue(r.dueAt != null)
        assertEquals("Preparar la entrevista", r.title)
    }

    @Test
    fun `preparar la entrevista de trabajo captura TASK preservando el objeto completo`() {
        val r = analyze("preparar la entrevista de trabajo del martes")!!
        assertEquals(ContextIntentKind.TASK, r.kind)
        assertTrue(r.dueAt != null)
        assertEquals("Preparar la entrevista de trabajo", r.title)
    }

    @Test
    fun `preparar una entrevista captura TASK con articulo indefinido preservado`() {
        val r = analyze("preparar una entrevista esta semana")!!
        assertEquals(ContextIntentKind.TASK, r.kind)
        // «esta semana» no es residuo temporal depurable ni parsea a
        // dueAt (familia conocida de colas); el título lo conserva.
        assertNull(r.dueAt)
        assertEquals("Preparar una entrevista esta semana", r.title)
    }

    // ---------- guards NULL esperados (medidos en PRE, deben seguir NULL) ----------

    @Test
    fun `guard negada - no preparar la entrevista no captura`() {
        assertNull(analyze("no preparar la entrevista de mañana"))
    }

    @Test
    fun `guard preterito - preparé la entrevista no captura`() {
        assertNull(analyze("preparé la entrevista ayer"))
    }

    @Test
    fun `guard subjuntivo duda - quizá prepare la entrevista no captura`() {
        assertNull(analyze("quizá prepare la entrevista mañana"))
    }

    @Test
    fun `guard nominal - la entrevista es mañana no captura`() {
        assertNull(analyze("la entrevista es mañana"))
    }

    @Test
    fun `guard copulativa preterito - la entrevista fue ayer no captura`() {
        assertNull(analyze("la entrevista fue ayer"))
    }

    @Test
    fun `guard presente habitual - preparo la entrevista todas las semanas no captura`() {
        assertNull(analyze("preparo la entrevista todas las semanas"))
    }

    // ---------- regresiones HIT esperadas (pines byte-idénticos) ----------

    @Test
    fun `regresion - preparar la cena sigue HOUSEHOLD`() {
        val r = analyze("preparar la cena")!!
        assertEquals(ContextIntentKind.HOUSEHOLD, r.kind)
        assertEquals("Preparar la cena", r.title)
    }

    @Test
    fun `regresion - preparar la maleta sigue TASK (piso c827 intacto)`() {
        val r = analyze("preparar la maleta mañana")!!
        assertEquals(ContextIntentKind.TASK, r.kind)
        assertTrue(r.dueAt != null)
        assertEquals("Preparar la maleta", r.title)
    }

    @Test
    fun `regresion - preparar el examen sigue STUDY`() {
        val r = analyze("preparar el examen del lunes")!!
        assertEquals(ContextIntentKind.STUDY, r.kind)
        assertTrue(r.dueAt != null)
    }

    @Test
    fun `regresion - envolvente recordame preparar la entrevista sigue TASK`() {
        val r = analyze("recuérdame preparar la entrevista de mañana")!!
        assertEquals(ContextIntentKind.TASK, r.kind)
    }
}
