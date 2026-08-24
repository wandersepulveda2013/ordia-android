package com.ordia.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// c.995: lateral (b2) de la sonda persistente de creación de tareas —
// «quiero que me recuerdes…» (recordatorio envuelto). Medido con sonda
// efímera /tmp/probe995/QuieroQueProbe.kt (PRE, base d952d77): 4/4
// capturas al menú genérico (action=NONE) — mentira por omisión: el
// usuario pide que le recuerden algo y el asistente recita el menú.
// Fix: rama quieroQueRecuerdesCapture hermana de remindMeCapture
// (mismo contrato: guía honesta pelada, NUNCA tarea vacía; extractor
// ([^:].*) simétrico a c.992 — NUNCA tarea basura «:»; negación → menú
// honesto). Guards: «no quiero que me recuerdes nada» (negación
// previa), «quiero que me recuerdes NO llamar…» (contenido negado) y
// «quería que me recordaras…» (pasado, otra persona) NUNCA capturan.
class AssistantEngineQuieroQueRecuerdesCaptureTest {

    private fun ask(q: String) = AssistantEngine.answer(q, emptyList(), emptyList(), emptyList())

    // ---------- capturas ----------

    @Test fun quieroQueMeRecuerdes_captura() {
        val answer = ask("quiero que me recuerdes pagar la luz")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("pagar la luz", answer.actionPayload)
    }

    @Test fun quieroQueMeRecuerdesConTemporal_captura() {
        val answer = ask("quiero que me recuerdes llamar al banco mañana")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("llamar al banco mañana", answer.actionPayload)
    }

    @Test fun quieroQueMeRecuerdesDosPuntos_capturaSinResiduo() {
        val answer = ask("quiero que me recuerdes: sacar al perro")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("sacar al perro", answer.actionPayload)
    }

    @Test fun quieroQueMeRecuerdesMayuscula_captura() {
        val answer = ask("Quiero que me recuerdes comprar leche")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("comprar leche", answer.actionPayload)
    }

    // ---------- pelada: guía honesta, NUNCA tarea vacía ----------

    @Test fun quieroQueMeRecuerdesPelada_guiaHonestaSinAccion() {
        val answer = ask("quiero que me recuerdes")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
        assertTrue(answer.text.contains("quiero que me recuerdes"))
    }

    // ---------- guards: NUNCA capturan ----------

    @Test fun noQuieroQueMeRecuerdes_negacionPreviaNoCaptura() {
        val answer = ask("no quiero que me recuerdes nada")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
    }

    @Test fun quieroQueMeRecuerdesNo_contenidoNegadoNoCaptura() {
        val answer = ask("quiero que me recuerdes no llamar al banco")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
    }

    @Test fun queriaQueMeRecordaras_pasadoNoCaptura() {
        val answer = ask("quería que me recordaras la cita")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
    }

    // ---------- regresiones hermanas c.986/c.994 ----------

    @Test fun recuerdame_regresionIntacta() {
        val answer = ask("recuérdame llamar al banco mañana")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("llamar al banco mañana", answer.actionPayload)
    }

    @Test fun avisame_regresionIntacta() {
        val answer = ask("avísame mañana de llamar al banco")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("llamar al banco mañana", answer.actionPayload)
    }
}
