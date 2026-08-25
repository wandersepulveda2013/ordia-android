package com.ordia.app.assistant

import com.ordia.app.data.local.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// c.1089: lateral ABIERTA (1) de la auditoría c.1085 — acción marca-hecha
// «termina/finaliza/tacha/completa <tarea>» caía al MENÚ (medido PRE con
// sonda efímera: 4 GAPs). Verbos de cierre inequívocos sobre el ítem;
// «haz la tarea» (bivalente) queda FUERA por diseño. Coincidencia EXACTA
// única hermana del área markDone (tokens significativos ⊆ título).
class AssistantEngineTerminaFinalizaTachaCompletaTest {

    private val base = listOf(
        TaskEntity(id = 1, title = "Llamar al banco"),
        TaskEntity(id = 2, title = "Pagar la luz"),
        TaskEntity(id = 3, title = "Revisión médica"),
        TaskEntity(id = 4, title = "Pagar la luz", completed = true)
    )

    private fun answer(request: String, tasks: List<TaskEntity> = base) =
        AssistantEngine.answer(request, tasks, emptyList(), emptyList())

    // --- Capturas: coincidencia única → COMPLETE_TASK + confirmación ---

    @Test fun termina_imperativoCaptura() {
        val a = answer("termina llamar al banco")
        assertEquals(AssistantAction.COMPLETE_TASK, a.action)
        assertEquals("1", a.actionPayload)
        assertEquals(listOf(1L), a.relatedTaskIds)
    }

    @Test fun finaliza_imperativoCaptura() {
        val a = answer("finaliza pagar la luz")
        assertEquals(AssistantAction.COMPLETE_TASK, a.action)
        assertEquals("2", a.actionPayload)
    }

    @Test fun tacha_imperativoCaptura() {
        val a = answer("tacha revisión médica")
        assertEquals(AssistantAction.COMPLETE_TASK, a.action)
        assertEquals("3", a.actionPayload)
    }

    @Test fun completa_imperativoCaptura() {
        val a = answer("completa la de llamar al banco")
        assertEquals(AssistantAction.COMPLETE_TASK, a.action)
        assertEquals("1", a.actionPayload)
    }

    @Test fun completadasNuncaSeOfrecen() {
        // «termina» una completada: solo la pendiente coincide.
        val a = answer("termina pagar la luz")
        assertEquals(AssistantAction.COMPLETE_TASK, a.action)
        assertEquals("2", a.actionPayload)
    }

    // --- Guards: nunca capturar ---

    @Test fun hazLaTarea_bivalenteQuedaFuera() {
        assertEquals(AssistantAction.NONE, answer("haz la tarea").action)
    }

    @Test fun terminLaTareaPelada_guiaSinAccion() {
        val a = answer("termina la tarea")
        assertEquals(AssistantAction.NONE, a.action)
        assertTrue(a.text.contains("¿Qué tarea"))
    }

    @Test fun negativo_noTermina_menuHonesto() {
        assertEquals(AssistantAction.NONE, answer("no termina la luz").action)
    }

    @Test fun terminaSinCoincidencia_guiaHonestaSinAccion() {
        val a = answer("termina limpiar la cocina")
        assertEquals(AssistantAction.NONE, a.action)
        assertTrue(a.text.contains("No encuentro ninguna tarea pendiente"))
    }

    // --- Regresiones hermanas del área (c.997/c.998) intactas ---

    @Test fun regresionMarcaComoHecha() {
        val a = answer("marca como hecha llamar al banco")
        assertEquals(AssistantAction.COMPLETE_TASK, a.action)

        val b = answer("completé de llamar al banco")
        assertEquals(AssistantAction.COMPLETE_TASK, b.action)
    }
}
