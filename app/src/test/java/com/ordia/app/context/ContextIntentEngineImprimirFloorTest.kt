package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.708 (P1 olvido silencioso en captura pasiva, ítem BACKLOG OPEN
 * clase-verbos c.692 — forma 6/8; una forma por ciclo, doctrina
 * anti-overreach): "imprimir <objeto>" ("imprimir las entradas el
 * viernes") se DESCARTABA (analyze → NULL). Sonda JVM fuente real PRE-fix
 * (`tools/probe/CommonVerbDiscoveryProbe.kt`, c.692, re-ejecutada en este
 * ciclo): "imprimir las entradas el viernes" → NULL. Fix: piso de TASK
 * (ancla inicio/acuse/prefijo temporal — patrón c.691…c.700) + plantilla
 * de título "imprimir X"→"Imprimir X" que despoja el acuse y el prefijo
 * temporal (lección c.616, match arranca en el verbo). Kind decidido en
 * este ciclo: TASK — "imprimir" es una acción de gestión sobre el objeto
 * (entradas/informe/contrato/billete), no un evento (MEETING) ni un
 * desplazamiento (ERRAND, anclado a destinos físicos). Anti-overreach:
 * `\s+\w` exige objeto, `(?<!no )` bloquea la negada, c.649 mantiene
 * "quizá…"→NULL, el sustantivo "impresión" no casa.
 * Determinista (regex), sin random, sin IA fingida.
 */
class ContextIntentEngineImprimirFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: "imprimir <objeto>" es una tarea clara ---

    @Test
    fun imprimirEntradasElViernes_capturesTaskWithDueAt() {
        val intent = analyze("imprimir las entradas el viernes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Imprimir las entradas", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun imprimirInformeManana_capturesTaskWithDueAt() {
        val intent = analyze("imprimir el informe mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Imprimir el informe", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun imprimirContratoElJueves_capturesTaskWithDueAt() {
        val intent = analyze("imprimir el contrato el jueves")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Imprimir el contrato", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun imprimirBilleteSinFecha_capturesTaskWithoutDueAt() {
        val intent = analyze("imprimir el billete")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Imprimir el billete", intent.title)
    }

    @Test
    fun imprimirTrasPrefijoDeAcuse_capturesTask() {
        val intent = analyze("vale, imprimir las entradas mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Imprimir las entradas", intent.title)
    }

    @Test
    fun imprimirTrasPrefijoTemporal_capturesTask() {
        val intent = analyze("hoy imprimir el informe")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Imprimir el informe", intent.title)
        assertNotNull(intent.dueAt)
    }

    // --- Controles anti-overreach (deben permanecer NULL) ---

    @Test
    fun noImprimirInforme_negatedStaysNull() {
        assertNull(analyze("no imprimir el informe"))
    }

    @Test
    fun quizasImprimir_conditionalStaysNull() {
        assertNull(analyze("quizá imprimir el informe mañana"))
    }

    @Test
    fun impresionDelInforme_nounDoesNotMatch() {
        assertNull(analyze("la impresión del informe fue ayer"))
    }

    @Test
    fun imprimirSinObjeto_bareVerbStaysNull() {
        assertNull(analyze("imprimir"))
    }

    // --- Regresión: la envolvente c.613 sigue gobernando ---

    @Test
    fun tengoQueImprimir_wrapperStillWins() {
        val intent = analyze("tengo que imprimir el informe")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Imprimir el informe", intent.title)
    }
}
