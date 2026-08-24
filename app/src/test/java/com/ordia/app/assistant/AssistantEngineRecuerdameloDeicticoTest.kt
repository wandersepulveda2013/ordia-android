package com.ordia.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// c.996: lateral (d) de la sonda persistente de creación de tareas —
// «recuérdamelo» deíctico. Medido con sonda efímera
// /tmp/probe996/RecuerdameloProbe.kt (PRE, base de4a39d): 4/4 formas
// deícticas al menú genérico (action=NONE) — mentira por omisión: el
// usuario da una orden clara de recordar y el asistente recita el menú.
// El motor no tiene contexto conversacional para resolver «lo», así que
// lo honesto (y útil) es reconocer la forma y pedir el contenido
// explícito: guía honesta SIN acción (NUNCA tarea basura «lo»).
// Guards: «no me lo recuerdes» (negación) y «me lo recordó ayer»
// (pasado, otra persona) NUNCA capturan.
class AssistantEngineRecuerdameloDeicticoTest {

    private fun ask(q: String) = AssistantEngine.answer(q, emptyList(), emptyList(), emptyList())

    // ---------- deícticos: guía honesta, NUNCA tarea basura «lo» ----------

    @Test fun recuerdamelo_guiaHonestaSinAccion() {
        val answer = ask("recuérdamelo")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
        assertTrue(answer.text.contains("«lo»"))
    }

    @Test fun recuerdameloSinTilde_guiaHonestaSinAccion() {
        val answer = ask("recuerdamelo")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
        assertTrue(answer.text.contains("«lo»"))
    }

    @Test fun recuerdameloConTemporal_guiaHonestaSinAccion() {
        val answer = ask("recuérdamelo mañana")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
        assertTrue(answer.text.contains("«lo»"))
    }

    @Test fun recuerdameloMayuscula_guiaHonestaSinAccion() {
        val answer = ask("Recuérdamelo")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
        assertTrue(answer.text.contains("«lo»"))
    }

    // ---------- guards: NUNCA capturan ----------

    @Test fun noMeLoRecuerdes_negacionNoCaptura() {
        val answer = ask("no me lo recuerdes")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
    }

    @Test fun meLoRecordoAyer_pasadoNoCaptura() {
        val answer = ask("me lo recordó ayer")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
    }

    // ---------- regresiones hermanas c.986/c.995 + pelada c.986 ----------

    @Test fun recuerdame_regresionIntacta() {
        val answer = ask("recuérdame llamar al banco mañana")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("llamar al banco mañana", answer.actionPayload)
    }

    @Test fun recuerdamePelada_guiaHonestaIntacta() {
        val answer = ask("recuérdame")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
        assertTrue(answer.text.contains("recuérdame"))
    }

    @Test fun quieroQueMeRecuerdes_regresionIntacta() {
        val answer = ask("quiero que me recuerdes pagar la luz")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("pagar la luz", answer.actionPayload)
    }
}
