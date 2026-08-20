package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.721b (P1 olvido silencioso en captura pasiva — TERCERA clase de formas
 * cotidianas, forma 2/19: "completar <objeto>" descubierto por sonda
 * `tools/probe/ThirdClassVerbDiscoveryProbe.kt` c.721; UNA forma por ciclo,
 * doctrina anti-overreach): "completar el formulario el lunes" se DESCARTABA
 * (analyze → NULL) aunque "completar" ya era keyword de TASK — la base baja
 * tras la penalización de ambigüedad no alcanzaba [MINIMUM_CONFIDENCE] sin
 * piso. Fix: piso "completar" en [hasStrongTaskImperative] (ancla inicio/
 * acuse/`TASK_FLOOR_TEMPORAL`, `(?<!no )`, `\s+\w` exige objeto) + plantilla
 * de título "(completar) X"→"Completar X" (patrón c.691…c.721; lección
 * c.616: el match arranca en el verbo). Kind decidido: TASK, en deliberación
 * contra DEADLINE — "completar" es la acción de cerrar/terminar el objeto
 * (formulario/tarea/proyecto), no un marcador de fecha tope (c.654).
 * Anti-overreach: objeto requerido, negada/duda/sustantivo "completitud"/
 * pasado "completé…"/suelto "completar" NULL; envolvente c.613 gobierna
 * TASK. Determinista (regex), sin random, sin IA fingida.
 */
class ContextIntentEngineCompletarFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: "completar <objeto>" es una gestión clara ---

    @Test
    fun completarElFormularioElLunes_capturesTaskWithDueAt() {
        val intent = analyze("completar el formulario el lunes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Completar el formulario", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun completarLaTarea_capturesTaskWithoutDueAt() {
        val intent = analyze("completar la tarea")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Completar la tarea", intent.title)
    }

    @Test
    fun completarTrasAcuse_capturesTask() {
        val intent = analyze("vale, completar el formulario hoy")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Completar el formulario", intent.title)
    }

    @Test
    fun completarTrasPrefijoTemporal_capturesTask() {
        val intent = analyze("mañana completar el informe")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Completar el informe", intent.title)
    }

    // --- Controles anti-overreach (deben permanecer NULL) ---

    @Test
    fun noCompletarElFormulario_negatedStaysNull() {
        assertNull(analyze("no completar el formulario"))
    }

    @Test
    fun quizasCompletarElFormulario_hedgeStaysNull() {
        assertNull(analyze("quizá completar el formulario mañana"))
    }

    @Test
    fun sustantivoCompletitud_nounStaysNull() {
        assertNull(analyze("la completitud del formulario fue ayer"))
    }

    @Test
    fun completeElFormulario_pastStaysNull() {
        assertNull(analyze("completé el formulario ayer"))
    }

    @Test
    fun completarSinObjeto_bareVerbStaysNull() {
        assertNull(analyze("completar"))
    }

    // --- Regresión: la envolvente c.613 ("recuérdame…") sigue gobernando ---

    @Test
    fun recuerdameCompletarElFormulario_wrapperWinsTask() {
        val intent = analyze("recuérdame completar el formulario")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}
