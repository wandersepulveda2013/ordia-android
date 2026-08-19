package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.694 (P1 olvido silencioso en captura pasiva, ítem BACKLOG OPEN
 * descubierto c.693 — UN cambio de CLASE, no formas sueltas): la forma
 * con PREFIJO temporal de los verbos de piso TASK (c.691/c.692/c.693)
 * se DESCARTABA (analyze → NULL) o se clasificaba mal: el ancla del
 * piso sólo admitía inicio/acuse y, sin keyword TASK para esos verbos,
 * el bono temporal no alcanza MINIMUM_CONFIDENCE. Sonda JVM fuente real
 * PRE-fix: "hoy entregar el informe", "mañana enviar el informe",
 * "hoy revisar el informe", "mañana entregar la tarea",
 * "pasado mañana entregar el informe", "esta tarde revisar el informe",
 * "el viernes enviar el informe" → NULL; "el lunes entregar la tarea"
 * → DEADLINE con título íntegro sucio ('El lunes entregar la tarea').
 * Fix: el ancla del piso y de las 3 plantillas de título gana el
 * PREFIJO temporal duro (hoy|mañana|esta <parte>|el <weekday>);
 * "pasado mañana" queda cubierto por "mañana" (substring). El título
 * no cambia de forma: la plantilla arranca en el verbo, así el prefijo
 * queda fuera del match (mismo mecanismo que despoja el acuse c.651).
 * Anti-overreach: `\s+\w` exige objeto, `(?<!no )` bloquea la negada
 * ("hoy no entregar…"), c.649 mantiene "quizá mañana…"→NULL.
 * Determinista (regex), sin random, sin IA fingida.
 */
class ContextIntentEngineTemporalPrefixFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: prefijo temporal + verbo de piso = la misma tarea ---

    @Test
    fun hoyEntregarElInforme_capturesTaskWithDueAt() {
        val intent = analyze("hoy entregar el informe")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Entregar el informe", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun mananaEntregarLaTarea_capturesTaskWithDueAt() {
        val intent = analyze("mañana entregar la tarea")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Entregar la tarea", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun mananaEnviarElInforme_capturesTaskWithDueAt() {
        val intent = analyze("mañana enviar el informe")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Enviar el informe", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun hoyRevisarElInforme_capturesTaskWithDueAt() {
        val intent = analyze("hoy revisar el informe")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Revisar el informe", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun pasadoMananaEntregarElInforme_capturesTaskWithDueAt() {
        val intent = analyze("pasado mañana entregar el informe")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Entregar el informe", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun estaTardeRevisarElInforme_capturesTaskWithDueAt() {
        val intent = analyze("esta tarde revisar el informe")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Revisar el informe", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun elLunesEntregarLaTarea_capturesTaskCleanTitle() {
        // PRE-fix capturaba DEADLINE con título íntegro sucio; el piso
        // normaliza al mismo kind/título de la forma sufija (c.693).
        val intent = analyze("el lunes entregar la tarea")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Entregar la tarea", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun elViernesEnviarElInforme_capturesTaskWithDueAt() {
        val intent = analyze("el viernes enviar el informe")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Enviar el informe", intent.title)
        assertNotNull(intent.dueAt)
    }

    // --- Controles anti-overreach ---

    @Test
    fun quizáPrefijoTemporalSigueDescartado() {
        assertNull(analyze("quizá mañana entregar el informe"))
    }

    @Test
    fun negadaConPrefijoTemporalSigueDescartada() {
        assertNull(analyze("hoy no entregar el informe"))
    }

    @Test
    fun verboSinObjetoSigueDescartado() {
        assertNull(analyze("mañana entregar"))
    }

    // --- Regresiones ---

    @Test
    fun formaSufijaSigueCapturando() {
        val intent = analyze("entregar el informe mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Entregar el informe", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun regarLasPlantasSigueHousehold() {
        val intent = analyze("regar las plantas")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Regar las plantas", intent.title)
    }
}
