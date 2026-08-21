package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.824 (P1 olvido silencioso en captura pasiva — verbo de encargos/
 * comisiones sin piso; una forma por ciclo, doctrina anti-overreach):
 * "encargar <objeto>" ("encargar el pastel mañana") se DESCARTABA
 * (analyze → NULL). Sonda JVM fuente real PRE-fix (metodología de
 * `tools/probe/CaptureCoverageProbe.kt`, c.822; pool de dispersión por
 * epoch-day elige una forma por ciclo): "encargar el pastel mañana",
 * "encargar las flores el viernes", desnuda "encargar el pastel", con
 * acuse "vale, …", con prefijo temporal "hoy …" y "encargar las flores
 * el lunes" → NULL; en cambio "tengo que encargar el pastel mañana" ya
 * capturaba por la envolvente (TASK 0.45, título limpio "Encargar el
 * pastel"). Fix: piso de TASK (ancla inicio/acuse/prefijo temporal —
 * patrón c.691…c.823, hermano de "pedir" c.712) + plantilla de título
 * "encargar X"→"Encargar X" que despoja el acuse y el prefijo temporal
 * (lección c.616, match arranca en el verbo). Kind decidido en este
 * ciclo: TASK — "encargar" gobierna el OBJETO (pastel/flores) como
 * acción de gestión; hermano semántico de "pedir"(c.712)/"mandar"
 * (c.823), no ERRAND (sin desplazamiento a destino físico).
 * Anti-overreach: `\s+\w` exige objeto, `(?<!no )` bloquea la negada,
 * c.649 mantiene "quizá…"→NULL, el sustantivo "encargo" no casa, el
 * participio "encargado", la 1ª persona "te encargo" y el pasado
 * "encargó" no casan, el verbo suelto no casa. Determinista (regex),
 * sin random, sin IA fingida.
 */
class ContextIntentEngineEncargarFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: "encargar <objeto>" es una tarea clara ---

    @Test
    fun encargarPastelManana_capturesTaskWithDueAt() {
        val intent = analyze("encargar el pastel mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Encargar el pastel", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun encargarFloresElViernes_capturesTaskWithDueAt() {
        val intent = analyze("encargar las flores el viernes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Encargar las flores", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun encargarFloresElLunes_capturesTaskWithDueAt() {
        val intent = analyze("encargar las flores el lunes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Encargar las flores", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun encargarPastelSinFecha_capturesTaskWithoutDueAt() {
        val intent = analyze("encargar el pastel")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Encargar el pastel", intent.title)
    }

    @Test
    fun encargarTrasPrefijoDeAcuse_capturesTask() {
        val intent = analyze("vale, encargar el pastel mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Encargar el pastel", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun encargarTrasPrefijoTemporal_capturesTask() {
        val intent = analyze("hoy encargar el pastel")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Encargar el pastel", intent.title)
        assertNotNull(intent.dueAt)
    }

    // --- Controles anti-overreach (deben permanecer NULL; verificados en
    // sonda PRE-fix) ---

    @Test
    fun elPastelYaEstaEncargado_participleStaysNull() {
        assertNull(analyze("el pastel ya está encargado"))
    }

    @Test
    fun noEncargarElPastel_negatedStaysNull() {
        assertNull(analyze("no encargar el pastel mañana"))
    }

    @Test
    fun quizasEncargar_conditionalStaysNull() {
        assertNull(analyze("quizá encargar el pastel mañana"))
    }

    @Test
    fun elEncargoDelPastel_nounDoesNotMatch() {
        assertNull(analyze("el encargo del pastel"))
    }

    @Test
    fun encargoElPastelAyer_pastNarrativeStaysNull() {
        assertNull(analyze("encargó el pastel ayer"))
    }

    @Test
    fun encargarSinObjeto_bareVerbStaysNull() {
        assertNull(analyze("encargar"))
    }

    // --- Regresión: la envolvente c.613 sigue gobernando (PRE-fix:
    // TASK 0.45, título limpio) ---

    @Test
    fun tengoQueEncargar_wrapperStillWins() {
        val intent = analyze("tengo que encargar el pastel mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Encargar el pastel", intent.title)
        assertNotNull(intent.dueAt)
    }
}
