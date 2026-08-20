package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.722 (P1 olvido silencioso en captura pasiva — TERCERA clase de formas
 * cotidianas, forma 9/19: "actualizar <objeto>" descubierto por sonda
 * `tools/probe/ThirdClassVerbDiscoveryProbe.kt` c.721; UNA forma por ciclo,
 * doctrina anti-overreach): "actualizar el currículum mañana" se DESCARTABA
 * (analyze → NULL) por ausencia de piso + keyword. Fix: piso "actualizar" en
 * [hasStrongTaskImperative] (ancla inicio/acuse/`TASK_FLOOR_TEMPORAL`,
 * `(?<!no )`, `\s+\w` exige objeto) + keyword "actualizar" en TASK (paridad
 * lockstep piso+keyword, lección c.713) + plantilla "(actualizar) X"→"Actualizar X"
 * (patrón c.691…c.721h; lección c.616). Kind decidido: TASK, en deliberación
 * contra HABIT — acción puntual de poner al día el objeto
 * (currículum/documento/lista), no una rutina recurrente (criterio c.704).
 * Anti-overreach: objeto requerido, negada/duda/sustantivo "actualización"/
 * pasado "actualicé…"/suelto "actualizar" NULL; envolvente c.613 gobierna TASK.
 * Determinista (regex), sin random, sin IA fingida.
 */
class ContextIntentEngineActualizarFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: "actualizar <objeto>" es una gestión clara ---

    @Test
    fun actualizarElCurriculumManana_capturesTaskWithDueAt() {
        val intent = analyze("actualizar el currículum mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Actualizar el currículum", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun actualizarLaLista_capturesTaskWithoutDueAt() {
        val intent = analyze("actualizar la lista")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Actualizar la lista", intent.title)
    }

    @Test
    fun actualizarTrasAcuse_capturesTask() {
        val intent = analyze("ok, actualizar el currículum mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Actualizar el currículum", intent.title)
    }

    @Test
    fun actualizarTrasPrefijoTemporal_capturesTask() {
        val intent = analyze("mañana actualizar el documento")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Actualizar el documento", intent.title)
    }

    // --- Controles anti-overreach (deben permanecer NULL) ---

    @Test
    fun noActualizarElCurriculum_negatedStaysNull() {
        assertNull(analyze("no actualizar el currículum"))
    }

    @Test
    fun quizasActualizarElCurriculum_hedgeStaysNull() {
        assertNull(analyze("quizá actualizar el currículum mañana"))
    }

    @Test
    fun sustantivoActualizacion_nounStaysNull() {
        assertNull(analyze("la actualización del documento fue ayer"))
    }

    @Test
    fun actualiceElCurriculum_pastStaysNull() {
        assertNull(analyze("actualicé el currículum ayer"))
    }

    @Test
    fun actualizarSinObjeto_bareVerbStaysNull() {
        assertNull(analyze("actualizar"))
    }

    // --- Regresión: la envolvente c.613 ("recuérdame…") sigue gobernando ---

    @Test
    fun recuerdameActualizarElCurriculum_wrapperWinsTask() {
        val intent = analyze("recuérdame actualizar el currículum")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}
