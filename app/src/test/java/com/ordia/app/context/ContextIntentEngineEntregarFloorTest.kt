package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.693 (P1 olvido silencioso en captura pasiva, ítem BACKLOG OPEN
 * clase-verbos c.692 — forma 2/8; una forma por ciclo, doctrina
 * anti-overreach): "entregar <objeto>" ("entregar la tarea el lunes")
 * se DESCARTABA (analyze → NULL). Sonda JVM fuente real PRE-fix
 * (`tools/probe/CommonVerbDiscoveryProbe.kt`): "entregar la tarea el
 * lunes" → NULL; control condicional ya NULL. Fix: piso de TASK
 * (ancla inicio/acuse, patrón c.691/c.692) + plantilla de título
 * "entregar X"→"Entregar X" que despoja el acuse (lección c.616).
 * Anti-overreach: `\s+\w` exige objeto, `(?<!no )` bloquea la negada,
 * c.649 mantiene "quizá…"→NULL, el sustantivo "entrega" no casa.
 * Determinista (regex), sin random, sin IA fingida.
 */
class ContextIntentEngineEntregarFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: "entregar <objeto>" es una tarea clara ---

    @Test
    fun entregarTareaElLunes_capturesTaskWithDueAt() {
        val intent = analyze("entregar la tarea el lunes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Entregar la tarea", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun entregarProyectoManana_capturesTaskWithDueAt() {
        val intent = analyze("entregar el proyecto mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Entregar el proyecto", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun entregarInformeALas9_capturesTaskWithDueAt() {
        val intent = analyze("entregar el informe a las 9")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Entregar el informe", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun entregarLibrosSinFecha_capturesTaskWithoutDueAt() {
        val intent = analyze("entregar los libros")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Entregar los libros", intent.title)
    }

    @Test
    fun entregarTrasPrefijoDeAcuse_capturesTask() {
        val intent = analyze("vale, entregar la tarea el lunes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Entregar la tarea", intent.title)
    }

    // --- Controles anti-overreach (deben permanecer NULL) ---

    @Test
    fun noEntregarTarea_negatedStaysNull() {
        assertNull(analyze("no entregar la tarea"))
    }

    @Test
    fun quizasEntregar_conditionalStaysNull() {
        assertNull(analyze("quizá entregar la tarea mañana"))
    }

    @Test
    fun entregaDelPaquete_nounDoesNotMatch() {
        assertNull(analyze("la entrega del paquete es mañana"))
    }

    @Test
    fun entregarSinObjeto_bareVerbStaysNull() {
        assertNull(analyze("entregar"))
    }

    // --- Regresión: la envolvente c.613 sigue gobernando ---

    @Test
    fun tengoQueEntregar_wrapperStillWins() {
        val intent = analyze("tengo que entregar el informe")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Entregar el informe", intent.title)
    }

    // --- Regresión c.693: "regar" legítimo (hogar) no se rompe con el `\b` ---

    @Test
    fun regarLasPlantas_staysHousehold() {
        val intent = analyze("regar las plantas")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Regar las plantas", intent.title)
    }
}
