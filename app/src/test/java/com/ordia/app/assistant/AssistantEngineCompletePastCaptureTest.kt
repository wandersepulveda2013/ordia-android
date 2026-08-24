package com.ordia.app.assistant

import com.ordia.app.data.local.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// c.998: «completé/terminé <tarea>» (pasado declarativo) → AssistantAction.COMPLETE_TASK
// confirmable. Hermano de «marca como hecha…» (c.997): la persona declara que ya hizo
// algo; el asistente localiza la tarea pendiente y ofrece marcarla (el botón confirma;
// NADA se completa en silencio). Anti-overreach: negación, presente y segunda persona
// NUNCA capturan (ancla ^).
class AssistantEngineCompletePastCaptureTest {

    private val fixture = listOf(
        TaskEntity(id = 1, title = "Enviar el informe"),
        TaskEntity(id = 2, title = "Preparar la presentación"),
        TaskEntity(id = 3, title = "Compra del súper"),
        TaskEntity(id = 4, title = "Llamar al banco", completed = true)
    )

    private fun ask(text: String) = AssistantEngine.answer(text, fixture, emptyList(), emptyList())

    @Test
    fun completePast_completéInforme_ofreceCompletar() {
        val r = ask("completé el informe")
        assertEquals(AssistantAction.COMPLETE_TASK, r.action)
        assertEquals("1", r.actionPayload)
        assertTrue(r.text.contains("Enviar el informe"))
    }

    @Test
    fun completePast_terminéPresentación_ofreceCompletar() {
        val r = ask("terminé la presentación")
        assertEquals(AssistantAction.COMPLETE_TASK, r.action)
        assertEquals("2", r.actionPayload)
    }

    @Test
    fun completePast_yaCompletéConContexto_masDeUnToken() {
        val r = ask("ya completé la compra del súper")
        assertEquals(AssistantAction.COMPLETE_TASK, r.action)
        assertEquals("3", r.actionPayload)
    }

    @Test
    fun completePast_acabéInforme_ofreceCompletar() {
        val r = ask("acabé el informe")
        assertEquals(AssistantAction.COMPLETE_TASK, r.action)
        assertEquals("1", r.actionPayload)
    }

    @Test
    fun completePast_terminéDePagar_despojaConector() {
        val r = ask("terminé de preparar la presentación")
        assertEquals(AssistantAction.COMPLETE_TASK, r.action)
        assertEquals("2", r.actionPayload)
    }

    @Test
    fun completePast_pelada_guiaHonestaSinAccion() {
        val r = ask("completé")
        assertEquals(AssistantAction.NONE, r.action)
        assertEquals("", r.actionPayload)
        assertFalse(r.text.startsWith("Puedo organizar"))
    }

    @Test
    fun completePast_negacion_noCaptura() {
        val r = ask("no completé el informe")
        assertEquals(AssistantAction.NONE, r.action)
        assertEquals("", r.actionPayload)
        assertTrue(r.text.startsWith("Puedo organizar"))
    }

    @Test
    fun completePast_presenteCasiTermino_noCaptura() {
        val r = ask("casi termino la presentación")
        assertEquals(AssistantAction.NONE, r.action)
        assertEquals("", r.actionPayload)
        assertTrue(r.text.startsWith("Puedo organizar"))
    }

    @Test
    fun completePast_segundaPersona_noCaptura() {
        val r = ask("¿completaste el informe?")
        assertEquals(AssistantAction.NONE, r.action)
        assertEquals("", r.actionPayload)
        assertTrue(r.text.startsWith("Puedo organizar"))
    }

    @Test
    fun completePast_sinCoincidencia_guiaHonesta() {
        val r = ask("completé llamar al banco")
        assertEquals(AssistantAction.NONE, r.action)
        assertEquals("", r.actionPayload)
        assertTrue(r.text.startsWith("No encuentro ninguna tarea pendiente"))
    }

    @Test
    fun completePast_controlesHermanos_noCambian() {
        assertEquals(AssistantAction.COMPLETE_TASK, ask("marca como hecha enviar el informe").action)
        assertEquals(AssistantAction.CREATE_TASK, ask("recuérdame llamar al banco").action)
        assertEquals(AssistantAction.CREATE_NOTE, ask("apúntame comprar pan").action)
    }
}
