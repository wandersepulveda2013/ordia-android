package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.711 (P1 olvido silencioso en captura pasiva — segunda clase de verbos
 * cotidianos de gestión, forma 1: "avisar <a <persona>/<objeto>" descubierto
 * por sonda `tools/probe/ManagementVerbDiscoveryProbe.kt` c.711; una forma
 * por ciclo, doctrina anti-overreach): "avisar a mamá de la cita mañana" se
 * DESCARTABA (analyze → NULL) — una gestión cotidiana con fecha explícita se
 * perdía silenciosamente (P1). Fix: piso de TASK (ancla inicio/acuse/prefijo
 * temporal — patrón c.691…c.710) + plantilla de título "avisar X"→"Avisar X"
 * (lección c.616: el match arranca en el verbo). Kind decidido en este
 * ciclo: TASK (en deliberación contra CALL — "avisar" es notificar, no una
 * llamada específica) — gobierna el objeto (mamá/jefe/cita/entrega) como
 * acción de gestión. Anti-overreach: `\s+\w` exige objeto, `(?<!no )` bloquea
 * la negada, c.649 mantiene "quizá…"→NULL, el sustantivo "aviso" no casa,
 * suelto "avisar" no casa; el envolvente c.613 ("recuérdame…") sigue
 * gobernando por su plantilla genérica. Determinista (regex), sin random,
 * sin IA fingida.
 */
class ContextIntentEngineAvisarFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: "avisar a <persona/objeto>" es una tarea clara ---

    @Test
    fun avisarAMamaDeCitaManana_capturesTaskWithDueAt() {
        val intent = analyze("avisar a mamá de la cita mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Avisar a mamá de la cita", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun avisarAlJefeDeEntregaHoy_capturesTaskWithDueAt() {
        val intent = analyze("avisar al jefe de la entrega hoy")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Avisar al jefe de la entrega", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun avisarAPapaDeReunionElViernes_capturesTaskWithDueAt() {
        val intent = analyze("avisar a papá de la reunión el viernes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Avisar a papá de la reunión", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun avisarAMariaDeCenaSinFecha_capturesTaskWithoutDueAt() {
        val intent = analyze("avisar a María de la cena")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Avisar a María de la cena", intent.title)
    }

    @Test
    fun avisarTrasPrefijoDeAcuse_capturesTask() {
        val intent = analyze("vale, avisar a mamá mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Avisar a mamá", intent.title)
    }

    @Test
    fun avisarTrasPrefijoTemporal_capturesTask() {
        val intent = analyze("hoy avisar al jefe")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Avisar al jefe", intent.title)
        assertNotNull(intent.dueAt)
    }

    // --- Controles anti-overreach (deben permanecer NULL) ---

    @Test
    fun noAvisarAMama_negatedStaysNull() {
        assertNull(analyze("no avisar a mamá mañana"))
    }

    @Test
    fun quizasAvisar_hedgeStaysNull() {
        assertNull(analyze("quizá avisar a mamá mañana"))
    }

    @Test
    fun elAvisoAMama_nounDoesNotMatch() {
        assertNull(analyze("el aviso a mamá era ayer"))
    }

    @Test
    fun avisarSinObjeto_bareVerbStaysNull() {
        assertNull(analyze("avisar"))
    }

    // --- Regresión: la envolvente c.613 ("recuérdame…") sigue gobernando ---

    @Test
    fun recuerdameAvisar_wrapperStillWins() {
        val intent = analyze("recuérdame avisar a mamá mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Avisar a mamá", intent.title)
    }
}
