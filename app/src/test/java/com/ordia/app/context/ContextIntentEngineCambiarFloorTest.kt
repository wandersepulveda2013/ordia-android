package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.710 (P1 olvido silencioso en captura pasiva, ítem BACKLOG OPEN
 * clase-verbos c.692 — forma 8/8, ÚLTIMA OPEN; una forma por ciclo, doctrina
 * anti-overreach): "cambiar <objeto>" ("cambiar las sábanas el domingo") se
 * DESCARTABA (analyze → NULL). Sonda JVM fuente real PRE-fix
 * (`tools/probe/CommonVerbDiscoveryProbe.kt`, c.692, re-ejecutada en este
 * ciclo): "cambiar las sábanas el domingo" → NULL. Fix: piso de TASK
 * (ancla inicio/acuse/prefijo temporal — patrón c.691…c.709) + plantilla
 * de título "cambiar X"→"Cambiar X" (lección c.616: el match arranca en
 * el verbo). Kind decidido en este ciclo: TASK (en deliberación contra
 * HOUSEHOLD — "cambiar" es un verbo genérico y un piso de posición libre
 * capturaría "cambiar de opinión/tema" como hogar: overreach): "cambiar"
 * gobierna el OBJETO (sábanas/toallas/cerradura/pilas) como acción de
 * gestión, igual que las 7 formas previas. Anti-overreach: `\s+\w` exige
 * objeto, `(?<!no )` bloquea la negada, c.649 mantiene "quizá…"→NULL, el
 * sustantivo "cambio" no casa, suelto "cambiar" no casa; el envolvente
 * c.613 ("tengo que…") sigue gobernando por su plantilla genérica.
 * Determinista (regex), sin random, sin IA fingida.
 */
class ContextIntentEngineCambiarFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: "cambiar <objeto>" es una tarea clara ---

    @Test
    fun cambiarSabanasElDomingo_capturesTaskWithDueAt() {
        val intent = analyze("cambiar las sábanas el domingo")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cambiar las sábanas", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun cambiarPilasManana_capturesTaskWithDueAt() {
        val intent = analyze("cambiar las pilas mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cambiar las pilas", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun cambiarToallasElViernes_capturesTaskWithDueAt() {
        val intent = analyze("cambiar las toallas el viernes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cambiar las toallas", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun cambiarCerraduraSinFecha_capturesTaskWithoutDueAt() {
        val intent = analyze("cambiar la cerradura")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cambiar la cerradura", intent.title)
    }

    @Test
    fun cambiarTrasPrefijoDeAcuse_capturesTask() {
        val intent = analyze("vale, cambiar las sábanas mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cambiar las sábanas", intent.title)
    }

    @Test
    fun cambiarTrasPrefijoTemporal_capturesTask() {
        val intent = analyze("hoy cambiar las toallas")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cambiar las toallas", intent.title)
        assertNotNull(intent.dueAt)
    }

    // --- Controles anti-overreach (deben permanecer NULL) ---

    @Test
    fun noCambiarSabanas_negatedStaysNull() {
        assertNull(analyze("no cambiar las sábanas"))
    }

    @Test
    fun quizasCambiar_conditionalStaysNull() {
        assertNull(analyze("quizá cambiar las sábanas mañana"))
    }

    @Test
    fun cambioDeAceite_nounDoesNotMatch() {
        assertNull(analyze("el cambio de aceite es mañana"))
    }

    @Test
    fun cambiarSinObjeto_bareVerbStaysNull() {
        assertNull(analyze("cambiar"))
    }

    // --- Regresión: la envolvente c.613 sigue gobernando ---

    @Test
    fun tengoQueCambiar_wrapperStillWins() {
        val intent = analyze("tengo que cambiar las sábanas")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cambiar las sábanas", intent.title)
    }
}
