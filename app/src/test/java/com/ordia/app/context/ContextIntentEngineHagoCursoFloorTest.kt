package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1188 (P1 olvido silencioso en captura pasiva): lateral ABIERTA
 * del piso c.1152 (documentada en su propio KDoc: «hago» no casa;
 * laterales, UNA forma por ciclo) — presente 1ª persona «hago el
 * curso (de prevención)», hermana del infinitivo «hacer el curso»
 * (c.1152, clase DECIMOSÉPTIMA vida-laboral) y espejo de c.1171
 * «hago la mudanza». PRE medido con sonda efímera
 * `/tmp/probe1183/Probe.kt` (motor real vía `tools/run_probe.sh`,
 * HEAD `38cd726c`): C7 «hago el curso de prevención mañana» NULL,
 * C8 «haré el curso…» NULL (futuro queda FUERA — lateral
 * siguiente); C1..C6 infinitivo HIT (c.1152 vigente); sensibles
 * S1..S3 NULL correctos (keyword sola inerte, gate c.751); guards
 * G1..G6 NULL correctos; regresiones R1..R3 HIT. Olvido real: el
 * presente de compromiso («el sábado hago el curso») es la forma
 * cotidiana de fijar el plan; la formación obligatoria perdida
 * cuesta la habilitación (prevención de riesgos, manipulador de
 * alimentos…).
 *
 * Fix lockstep DOS puntos (lección c.616/c.751 — CERO keywords
 * nuevas; el piso c.613 eleva con maxOf(score, MINIMUM_CONFIDENCE)
 * SIN exigir keyword — gate c.751 intacto): (1) verbo «hacer» →
 * «(hacer|hago)» en el piso curso de hasStrongTaskImperative (ancla
 * ^|acuse|temporal + lookbehind «no » heredados del piso c.1152);
 * (2) MISMA extensión con verbo CAPTURADO en la plantilla
 * matchHacerCurso de extractTitle (grafía preservada, doctrina
 * c.653; precedente c.1171 matchHacerMudanza / c.903: solo
 * capitalización inicial; residuo temporal lo depura sanitizeTitle).
 *
 * Kind TASK (gestión administrativa con plazo, hermana del
 * infinitivo c.1152; STUDY gobierna el estudio continuo — criterio
 * c.704). Determinista (regex), sin random, sin IA fingida.
 * Alcance: SOLO presente 1ª persona «hago»; futuro «haré…» y
 * subjuntivo «haga…» quedan laterales documentadas (UNA forma
 * por ciclo, doctrina anti-overreach).
 */
class ContextIntentEngineHagoCursoFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: «hago (el)? curso» es un plan comprometido ---
    // Títulos/dueAt pineados tras medir POST con sonda persistida
    // (motor real, tools/run_probe.sh).

    @Test
    fun hagoElCursoDePrevencionManana_capturesTaskWithDueAt() {
        val intent = analyze("hago el curso de prevención mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hago el curso de prevención", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun hagoMiCursoDeInglesJueves_capturesTaskWithDueAt() {
        val intent = analyze("hago mi curso de inglés el jueves")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hago mi curso de inglés", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun hagoUnCursoDeCocinaEstaNoche_capturesTask() {
        val intent = analyze("hago un curso de cocina esta noche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hago un curso de cocina", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun hagoElCurso_capturesTaskBare() {
        val intent = analyze("hago el curso")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hago el curso", intent.title)
    }

    @Test
    fun elSabadoHagoElCurso_capturesTaskTemporalPrefix() {
        // Pin medido POST (sonda persistida, motor real): la ruta de
        // extractTitle conserva el prefijo temporal en el título
        // (dueAt sí resuelve), hermano de «el lunes hago la mudanza»
        // c.1171. Captura NUEVA gracias a este piso (PRE: NULL).
        val intent = analyze("el sábado hago el curso")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("El sábado hago el curso", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun valeHagoElCurso_capturesTaskAckPrefix() {
        val intent = analyze("vale, hago el curso de prevención")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hago el curso de prevención", intent.title)
    }

    // --- Envolvente: candado c.613 (pin byte-idéntico, ya capturaba) ---

    @Test
    fun tengoQueHacerElCurso_remainsTaskWrapper() {
        val intent = analyze("tengo que hacer el curso el lunes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer el curso", intent.title)
    }

    // --- Guards (deben quedarse NULL) ---

    @Test
    fun negadaNoHagoElCurso_isNull() {
        assertNull(analyze("no hago el curso mañana"))
    }

    @Test
    fun pasadoHiceElCurso_isNull() {
        assertNull(analyze("hice el curso ayer"))
    }

    @Test
    fun dudaSubjuntivoHagaElCurso_isNull() {
        assertNull(analyze("quizá haga el curso"))
    }

    @Test
    fun subjuntivoHagaElCurso_isNull() {
        assertNull(analyze("haga el curso"))
    }

    @Test
    fun terceraPersonaHaceElCurso_isNull() {
        assertNull(analyze("él hace el curso mañana"))
    }

    @Test
    fun condicionalHariaElCurso_isNull() {
        assertNull(analyze("haría el curso si tuviera tiempo"))
    }

    @Test
    fun nominalElCursoDePrevencion_isNull() {
        assertNull(analyze("el curso de prevención"))
    }

    @Test
    fun futuroHareElCurso_isNull() {
        // Futuro 1ª persona «haré…»: lateral SIGUIENTE documentada
        // (UNA forma por ciclo, anti-overreach; medida NULL PRE C8).
        assertNull(analyze("haré el curso de prevención la semana que viene"))
    }

    @Test
    fun hedgeNoSeSiHagoElCurso_isNull() {
        // Pin medido POST: HEDGE_PENALTY (0.45 − 0.3 → bajo umbral),
        // hermano de «no sé si hacer el curso» c.1152.
        assertNull(analyze("no sé si hago el curso"))
    }

    @Test
    fun hagoSolo_isNull() {
        assertNull(analyze("hago"))
    }

    // --- Regresiones (formas que YA capturan — pin byte-idéntico) ---

    @Test
    fun hacerElCursoDePrevencion_remainsTask() {
        // Piso c.1152 (infinitivo) intacto.
        val intent = analyze("hacer el curso de prevención mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer el curso de prevención", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun hacerLaMudanza_remainsTask() {
        val intent = analyze("hacer la mudanza el sábado")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun hagoLaMudanza_remainsTask() {
        // Lateral hermana c.1171 intacta (verbo capturado en su
        // plantilla propia).
        val intent = analyze("hago la mudanza el sábado")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hago la mudanza", intent.title)
    }

    @Test
    fun hacerElCheckInDelVuelo_remainsTask() {
        val intent = analyze("hacer el check-in del vuelo mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun estudiarElExamen_remainsStudy() {
        val intent = analyze("estudiar el examen mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.STUDY, intent!!.kind)
    }
}
