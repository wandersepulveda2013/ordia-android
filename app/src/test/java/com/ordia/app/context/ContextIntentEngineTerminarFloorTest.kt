package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.721 (P1 olvido silencioso en captura pasiva — TERCERA clase de formas
 * cotidianas, forma 1/19: "terminar <objeto>" descubierto por sonda
 * `tools/probe/ThirdClassVerbDiscoveryProbe.kt`; UNA forma por ciclo,
 * doctrina anti-overreach): "terminar el informe mañana" se DESCARTABA
 * (analyze → NULL) aun que "terminar" ya era keyword de TASK — la base baja
 * tras la penalización de ambigüedad no alcanzaba [MINIMUM_CONFIDENCE] sin
 * piso. Fix: piso "terminar" en [hasStrongTaskImperative] (ancla inicio/
 * acuse/`TASK_FLOOR_TEMPORAL`, `(?<!no )`, `\s+\w` exige objeto) + plantilla
 * de título "(terminar) X"→"Terminar X" (patrón c.691…c.720; lección c.616:
 * el match arranca en el verbo). Kind decidido: TASK, en deliberación
 * contra DEADLINE — "terminar" es la acción de cerrar/completar el objeto
 * (informe/tarea/proyecto/formulario), no un marcador de fecha tope
 * ("deadline"/"fecha límite" c.654). Anti-overreach: objeto requerido,
 * negada/duda/sustantivo "término"/pasado "terminé…"/suelto "terminar" NULL;
 * envolvente c.613 gobierna TASK. Determinista (regex), sin random, sin IA
 * fingida.
 */
class ContextIntentEngineTerminarFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: "terminar <objeto>" es una gestión clara ---

    @Test
    fun terminarElInformeManana_capturesTaskWithDueAt() {
        val intent = analyze("terminar el informe mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Terminar el informe", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun terminarLaTarea_capturesTaskWithoutDueAt() {
        val intent = analyze("terminar la tarea")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Terminar la tarea", intent.title)
    }

    @Test
    fun terminarTrasAcuse_capturesTask() {
        val intent = analyze("vale, terminar el informe hoy")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Terminar el informe", intent.title)
    }

    @Test
    fun terminarTrasPrefijoTemporal_capturesTask() {
        val intent = analyze("mañana terminar el informe")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Terminar el informe", intent.title)
    }

    // --- Controles anti-overreach (deben permanecer NULL) ---

    @Test
    fun noTerminarElInforme_negatedStaysNull() {
        assertNull(analyze("no terminar el informe"))
    }

    @Test
    fun quizasTerminarElInforme_hedgeStaysNull() {
        assertNull(analyze("quizá terminar el informe mañana"))
    }

    @Test
    fun sustantivoTermino_nounStaysNull() {
        assertNull(analyze("el término del informe fue ayer"))
    }

    @Test
    fun terminadoElInforme_pastStaysNull() {
        assertNull(analyze("terminé el informe ayer"))
    }

    @Test
    fun terminarSinObjeto_bareVerbStaysNull() {
        assertNull(analyze("terminar"))
    }

    // --- Regresión: la envolvente c.613 ("recuérdame…") sigue gobernando ---

    @Test
    fun recuerdameTerminarElInforme_wrapperWinsTask() {
        val intent = analyze("recuérdame terminar el informe")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}
