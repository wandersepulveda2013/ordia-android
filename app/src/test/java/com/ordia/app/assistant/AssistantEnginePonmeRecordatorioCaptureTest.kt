package com.ordia.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// c.991: lateral (e) de la sonda persistente
// `tools/probe/AssistantTaskCreationProbe.kt` — «ponme un recordatorio …»
// sufría ROBO DE RAMA: caía en la consulta de recordatorios c.808
// (`isRemindersQuery`: "recordatorio" in query) y respondía la MENTIRA
// «No tienes recordatorios programados.» cuando el usuario pedía CREAR
// uno (bug P1 de routing crear-vs-consultar). Sonda PRE efímera
// /tmp/probe990/PonmeRecordatorioProbe.kt (HEAD c8ab66a): 5/5 capturas
// robadas por la consulta (fixture vacío: mentira a la vista); 2/2
// peladas también robadas; 4/4 guards correctos; 3/3 controles intactos.
// Fix mínimo hermano de c.986 («recuérdame…»): rama `setReminderCapture`
// ANTES de la consulta (el imperativo de creación gana al listado), con
// despoje del conector «para» («ponme un recordatorio PARA mañana
// llamar al banco» → payload «mañana llamar al banco» — medido con
// /tmp/probe990/ParserPayloadProbe.kt: el parser extrae la fecha pero
// dejaba el «para» de residuo en el título). Pelada → guía honesta SIN
// acción (NUNCA tarea vacía, doctrina c.969). La consulta c.808 queda
// intacta para las formas interrogativas/posesivas.
class AssistantEnginePonmeRecordatorioCaptureTest {

    private fun ask(q: String) = AssistantEngine.answer(q, emptyList(), emptyList(), emptyList())

    // ---------- capturas ----------

    @Test fun ponmeRecordatorioParaFecha_creaTarea() {
        val answer = ask("ponme un recordatorio para mañana llamar al banco")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("mañana llamar al banco", answer.actionPayload)
    }

    @Test fun ponmeRecordatorioConDosPuntos_creaTarea() {
        val answer = ask("ponme un recordatorio: llamar al banco")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("llamar al banco", answer.actionPayload)
    }

    @Test fun ponmeRecordatorioDirecto_creaTarea() {
        val answer = ask("ponme un recordatorio llamar al banco mañana")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("llamar al banco mañana", answer.actionPayload)
    }

    @Test fun ponRecordatorioSinMe_creaTarea() {
        val answer = ask("pon un recordatorio para mañana llamar al banco")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("mañana llamar al banco", answer.actionPayload)
    }

    @Test fun ponmeRecordatorioSinUn_creaTarea() {
        val answer = ask("ponme recordatorio para el lunes pagar la luz")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("el lunes pagar la luz", answer.actionPayload)
    }

    // ---------- peladas ----------

    @Test fun ponmeRecordatorioPelado_guiaHonestaSinAccion() {
        val answer = ask("ponme un recordatorio")
        assertEquals(AssistantAction.NONE, answer.action)
        assertEquals("", answer.actionPayload)
        assertTrue(answer.text.contains("Escríbelo tras"))
    }

    @Test fun ponRecordatorioPelado_guiaHonestaSinAccion() {
        val answer = ask("pon un recordatorio")
        assertEquals(AssistantAction.NONE, answer.action)
        assertEquals("", answer.actionPayload)
        assertTrue(answer.text.contains("Escríbelo tras"))
    }

    // ---------- guards (la consulta c.808 sigue siendo consulta) ----------

    @Test fun consultaRecordatorios_sigueSiendoConsulta() {
        val answer = ask("qué recordatorios tengo")
        assertNotEquals(AssistantAction.CREATE_TASK, answer.action)
        assertTrue(answer.text.contains("recordatorio", ignoreCase = true))
    }

    @Test fun misRecordatorios_sigueSiendoConsulta() {
        assertNotEquals(AssistantAction.CREATE_TASK, ask("mis recordatorios").action)
    }

    @Test fun negacion_noCaptura() {
        assertNotEquals(AssistantAction.CREATE_TASK, ask("no me pongas recordatorios").action)
    }

    @Test fun sustantivoPasado_noCaptura() {
        assertNotEquals(AssistantAction.CREATE_TASK, ask("el recordatorio sonó ayer").action)
    }

    // ---------- regresiones hermanas ----------

    @Test fun recuerdame_sigueCreandoTarea() {
        val answer = ask("recuérdame llamar al banco mañana")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("llamar al banco mañana", answer.actionPayload)
    }

    @Test fun agenda_sigueRutando() {
        val answer = ask("qué tengo hoy")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.text.contains("hoy", ignoreCase = true))
    }
}
