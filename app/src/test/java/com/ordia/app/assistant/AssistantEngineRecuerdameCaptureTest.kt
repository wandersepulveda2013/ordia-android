package com.ordia.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// c.986: captura «recuérdame <contenido>» — la hermana de TAREAS de la
// familia de notas (c.969…c.985 «-melo» AGOTADA). Sonda PERSISTENTE PRE
// `tools/probe/AssistantTaskCreationProbe.kt` (HEAD 173a74b): 9/10
// candidatas de creación de tarea/recordatorio caían al MENÚ GENÉRICO —
// mentira por omisión: la frase más cotidiana de un asistente personal se
// perdía pese a que la capacidad YA existe (la UI ejecuta
// `vm.addSmartTask(payload)` → NaturalTaskParser, la misma captura rápida).
// Doctrina anti-overreach (UNA forma por ciclo): sólo «recuérdame …».
// Laterales documentadas en la sonda/BACKLOG: «crea/añade/agrega una
// tarea», «avísame…», «quiero que me recuerdes…», «recuérdame que…»
// (subordinada), «recuérdamelo» (deíctico), «ponme un recordatorio…»
// (robo de la rama de consulta c.808 — bug de routing aparte).
// NUNCA tarea vacía: pelada responde guía honesta SIN acción (hermana de
// la nota pelada c.969). Anti-overreach: contenido negado («recuérdame NO
// llamar…») NO captura — crear la tarea sería lo contrario de la intención.
class AssistantEngineRecuerdameCaptureTest {

    private fun ask(q: String) = AssistantEngine.answer(q, emptyList(), emptyList(), emptyList())

    // ---------- capturas ----------

    @Test fun recuerdameDirecto_creaTarea() {
        val answer = ask("recuérdame llamar al banco mañana")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("llamar al banco mañana", answer.actionPayload)
    }

    @Test fun recuerdameConFecha_creaTarea() {
        val answer = ask("recuérdame pagar la luz el viernes")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("pagar la luz el viernes", answer.actionPayload)
    }

    @Test fun recuerdameSinFecha_creaTarea() {
        val answer = ask("recuérdame comprar leche")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("comprar leche", answer.actionPayload)
    }

    @Test fun recuerdameSinTilde_creaTarea() {
        val answer = ask("recuerdame llamar a Ana")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("llamar a Ana", answer.actionPayload)
    }

    @Test fun recuerdameConDosPuntos_creaTarea() {
        val answer = ask("recuérdame: sacar al perro")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("sacar al perro", answer.actionPayload)
    }

    // ---------- guards ----------

    @Test fun recuerdamePelado_guiaHonestaSinAccion() {
        val answer = ask("recuérdame")
        assertEquals(AssistantAction.NONE, answer.action)
        assertEquals("", answer.actionPayload)
        assertTrue(answer.text.contains("recuérdame"))
    }

    @Test fun negacionPrevia_noCaptura() {
        assertNotEquals(AssistantAction.CREATE_TASK, ask("no me recuerdes nada").action)
    }

    @Test fun afirmacionPasada_noCaptura() {
        assertNotEquals(AssistantAction.CREATE_TASK, ask("recuerdo la tarea de ayer").action)
    }

    @Test fun sustantivoRecuerdo_noCaptura() {
        assertNotEquals(AssistantAction.CREATE_TASK, ask("el recuerdo llegó ayer").action)
    }

    @Test fun contenidoNegado_noCaptura() {
        assertNotEquals(AssistantAction.CREATE_TASK, ask("recuérdame no llamar al banco").action)
    }

    // ---------- regresiones hermanas ----------

    @Test fun notaMelo_sigueSiendoNota() {
        val answer = ask("apúntamelo: comprar pan")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("comprar pan", answer.actionPayload)
    }

    @Test fun consultaRecordatorios_sigueRutando() {
        val answer = ask("qué recordatorios tengo")
        assertNotEquals(AssistantAction.CREATE_TASK, answer.action)
        assertTrue(answer.text.contains("recordatorio", ignoreCase = true))
    }
}
