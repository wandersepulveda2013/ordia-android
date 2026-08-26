package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1152 (P1 olvido silencioso en captura pasiva; renumerada
 * c.1151→c.1152 por carrera de marcador: el hermano fijó c.1151
 * «sacar el visado» primero en el remoto, primer-marcador-gana,
 * lección c.1077): candidata (c) FUERTE de la clase DECIMOSÉPTIMA
 * (vida laboral, sonda persistida
 * `tools/probe/SeventeenthClassWorkProbe.kt` c.1147, C16 — NULL
 * medido). «hacer el curso de prevención antes del día 30» se
 * DESCARTABA (analyze → NULL): «hacer» es keyword TASK (0.12) y
 * «curso» keyword EVENT/STUDY (0.12) — la frase llega al análisis
 * (gate c.751 satisfecho) pero todo queda < 0.45. Olvido real: la
 * formación obligatoria con plazo (prevención de riesgos, manipulador
 * de alimentos, etc.) caduca y cuesta la habilitación/renovación.
 *
 * PRE medido con sonda efímera (motor real vía `tools/run_probe.sh`,
 * HEAD base `39417de`): 6/6 capturas NULL (pelada/indefinido/online/
 * plural/acuse/prefijo temporal), guards 9/9 NULL (negación, pasado,
 * futuro, subjuntivo-duda, hedge, nominal, presente, tercera persona),
 * pines 5/5 HIT estables (envolvente c.613 TASK 0.52, check-in c.1140,
 * deberes STUDY c.898, compra SHOPPING, maleta c.715).
 *
 * Fix lockstep DOS puntos (lección c.616/c.751 — CERO keywords
 * nuevas), hermano EXACTO de c.1140 «hacer el check-in del vuelo»:
 * (1) piso acotado «hacer (det)? cursos?» en hasStrongTaskImperative
 * (ancla ^|acuse|temporal + lookbehind «no » heredados; objeto
 * «curso» EXIGIDO — monosemántico-formación, sin acepción bivalente
 * frecuente como objeto de «hacer»); (2) plantilla hermana
 * matchHacerCurso en extractTitle (grafía preservada, doctrina c.653;
 * residuo temporal lo depura sanitizeTitle).
 *
 * Kind TASK (gestión administrativa con plazo, hermana de
 * sellar-paro c.1143 y check-in c.1140; STUDY es el dominio del
 * estudio continuo, no la obligación puntual — criterio c.704).
 * Determinista (regex), sin random, sin IA fingida. Alcance: SOLO
 * infinitivo «hacer»; presente «hago», futuro «haré» y subjuntivo
 * «haga» quedan laterales (UNA forma por ciclo, doctrina
 * anti-overreach).
 */
class ContextIntentEngineHacerCursoFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: «hacer (el)? curso» es una tarea clara ---

    // Títulos/dueAt pineados tras medir POST con sonda efímera (motor
    // real, tools/run_probe.sh): «antes del día 30» se conserva en el
    // título Y resuelve dueAt (igual que la envolvente c.613);
    // «esta semana»/«en octubre» no resuelven fecha; «el mes que
    // viene» sí y se depura del título vía sanitizeTitle.

    @Test
    fun hacerCursoPrevencion_capturesTaskWithDueAt() {
        val intent = analyze("hacer el curso de prevención antes del día 30")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer el curso de prevención antes del día 30", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun hacerUnCursoCocina_capturesTask() {
        val intent = analyze("hacer un curso de cocina en octubre")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer un curso de cocina en octubre", intent.title)
    }

    @Test
    fun hacerCursoOnlineEstaSemana_capturesTask() {
        // «esta semana» no resuelve a fecha (igual que en c.1149: sin pin de dueAt)
        val intent = analyze("hacer el curso online esta semana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer el curso online esta semana", intent.title)
    }

    @Test
    fun hacerCursosFormacion_capturesTaskPlural() {
        val intent = analyze("hacer cursos de formación el mes que viene")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer cursos de formación", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun siHacerCursoPrevencion_capturesTaskAckPrefix() {
        val intent = analyze("sí, hacer el curso de prevención")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer el curso de prevención", intent.title)
    }

    @Test
    fun mananaHacerCursoPrevencion_capturesTaskTemporalPrefix() {
        val intent = analyze("mañana hacer el curso de prevención")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer el curso de prevención", intent.title)
        assertNotNull(intent.dueAt)
    }

    // --- Envolvente: candado c.613 (pin byte-idéntico, ya capturaba) ---

    @Test
    fun recuerdameHacerCurso_remainsTaskWrapper() {
        val intent = analyze("recuérdame hacer el curso de prevención antes del día 30")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer el curso de prevención antes del día 30", intent.title)
        assertNotNull(intent.dueAt)
    }

    // --- Guards (deben quedarse NULL) ---

    @Test
    fun negadaNoHagasCurso_isNull() {
        assertNull(analyze("no hagas el curso de prevención"))
    }

    @Test
    fun negadaNoHacerCurso_isNull() {
        assertNull(analyze("no hacer el curso de prevención"))
    }

    @Test
    fun pasadoHiceCurso_isNull() {
        assertNull(analyze("hice el curso de prevención ayer"))
    }

    @Test
    fun futuroHareCurso_isCaptured() {
        // Re-pin legítimo documentado (c.1196 estelado): el hermano
        // amplía (hacer|hago|haré) en piso+plantilla con medición
        // PRE NULL → POST captura. Válido con ´grafía preservada´.
        val intent = analyze("haré el curso de prevención mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun dudaSubjuntivoHagaCurso_isNull() {
        assertNull(analyze("quizá haga el curso de prevención"))
    }

    @Test
    fun hedgeNoSeSiHacerCurso_isNull() {
        assertNull(analyze("no sé si hacer el curso de prevención"))
    }

    @Test
    fun nominalCursoPrevencion_isNull() {
        assertNull(analyze("el curso de prevención es obligatorio"))
    }

    // c.1188: el presente 1ª persona «hago el curso» CAPTURA ahora
    // (lateral habilitada deliberadamente por MI ciclo c.1188; este
    // pin NULL era correcto cuando se fijó — re-pin legítimo,
    // precedente c.1035/c.1041/c.1094/c.1171; la captura y sus pines
    // viven en ContextIntentEngineHagoCursoFloorTest). Residuo
    // «esta semana» pin medido (familia conocida c.845/c.852).
    @Test
    fun presenteHagoCurso_capturesTaskResidue() {
        val intent = analyze("hago el curso de prevención esta semana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hago el curso de prevención esta semana", intent.title)
    }

    @Test
    fun terceraPersonaHaceCurso_isNull() {
        assertNull(analyze("mi hermano hace el curso de prevención"))
    }

    // --- Regresiones (formas que YA capturan — pin byte-idéntico) ---

    @Test
    fun hacerCheckInVuelo_remainsTask() {
        val intent = analyze("hacer el check-in del vuelo online")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun hacerDeberes_remainsStudy() {
        val intent = analyze("hacer deberes mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.STUDY, intent!!.kind)
    }

    @Test
    fun hacerLaCompra_remainsShopping() {
        val intent = analyze("hacer la compra mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.SHOPPING, intent!!.kind)
    }

    @Test
    fun hacerLaMaleta_remainsTask() {
        val intent = analyze("hacer la maleta esta noche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}
