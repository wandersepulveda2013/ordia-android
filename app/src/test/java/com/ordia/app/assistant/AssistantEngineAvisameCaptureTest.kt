package com.ordia.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// c.994: lateral (b1) de la sonda persistente de creación de tareas —
// «avísame…» (recordatorio declarativo). Medido con sonda efímera
// /tmp/probe994/AvisameProbe.kt (PRE, base 2f7c94c): 4/4 capturas al
// menú genérico (action=NONE) — mentira por omisión: el usuario pide
// que le avisen y el asistente recita el menú. Fix: rama
// avisaMeCapture hermana de remindMeCapture (mismo contrato: guía
// honesta pelada, NUNCA tarea vacía; negación → menú honesto). El
// temporal intercalado («avísame MAÑANA DE llamar…») se reordena al
// final del payload para que NaturalTaskParser ancle la fecha con
// título limpio; el «de» preposicional se despoja. Guards:
// «avísame CUANDO llegue Ana» (evento condicional no programable,
// anti-overreach) y «no me avises…» NUNCA capturan.
class AssistantEngineAvisameCaptureTest {

    private fun ask(q: String) = AssistantEngine.answer(q, emptyList(), emptyList(), emptyList())

    // ---------- capturas ----------

    @Test fun avisameMananaDe_capturaConTemporalReordenado() {
        val answer = ask("avísame mañana de llamar al banco")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("llamar al banco mañana", answer.actionPayload)
    }

    @Test fun avisameSinTilde_captura() {
        val answer = ask("avisame mañana de llamar al banco")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("llamar al banco mañana", answer.actionPayload)
    }

    @Test fun avisameDe_capturaSinTemporal() {
        val answer = ask("avísame de pagar la luz")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("pagar la luz", answer.actionPayload)
    }

    @Test fun avisameDosPuntos_captura() {
        val answer = ask("avísame: llamar al banco")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("llamar al banco", answer.actionPayload)
    }

    // ---------- pelada: guía honesta, NUNCA tarea vacía ----------

    @Test fun avisamePelada_guiaHonestaSinAccion() {
        val answer = ask("avísame")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
        assertTrue(answer.text.contains("avísame"))
    }

    // ---------- guards: NUNCA capturan ----------

    @Test fun avisameCuando_eventoCondicionalNoCaptura() {
        val answer = ask("avísame cuando llegue Ana")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
    }

    @Test fun noMeAvises_negacionPreviaNoCaptura() {
        val answer = ask("no me avises de nada")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
    }

    @Test fun avisameDeNo_negacionTrasDeNoCaptura() {
        val answer = ask("avísame de no llamar al banco")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
    }

    // ---------- regresiones hermanas c.986/c.993 ----------

    @Test fun recuerdame_regresionIntacta() {
        val answer = ask("recuérdame llamar al banco mañana")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("llamar al banco mañana", answer.actionPayload)
    }

    @Test fun recuerdameNo_regresionIntacta() {
        val answer = ask("recuérdame no llamar al banco")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
    }
}
