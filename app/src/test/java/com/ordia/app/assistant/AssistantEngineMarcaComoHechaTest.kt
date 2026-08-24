package com.ordia.app.assistant

import com.ordia.app.data.local.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// c.997: «marca como hecha/completada <tarea>» → COMPLETE_TASK con confirmación.
// Sonda c.997: la forma caía al menú genérico (mentira por omisión) pese a que
// completar una tarea por voz/texto elimina pasos reales (buscar, abrir, marcar).
// Anti-overreach: coincidencia EXACTAMENTE única sobre tareas pendientes
// (tokens significativos del contenido ⊆ tokens del título); cero → guía
// honesta; varias → lista honesta SIN acción; completadas/archivadas NUNCA
// se ofrecen; el botón confirma (nada se completa en silencio).
class AssistantEngineMarcaComoHechaTest {

    private val base = listOf(
        TaskEntity(id = 1, title = "Llamar al banco"),
        TaskEntity(id = 2, title = "Pagar la luz"),
        TaskEntity(id = 3, title = "Revisión médica")
    )

    private fun answer(request: String, tasks: List<TaskEntity> = base) =
        AssistantEngine.answer(request, tasks, emptyList(), emptyList())

    // --- Captura con coincidencia única → COMPLETE_TASK + confirmación ---

    @Test fun marcaComoHecha_coincidenciaUnicaCompletaTarea() {
        val a = answer("marca como hecha llamar al banco")
        assertEquals(AssistantAction.COMPLETE_TASK, a.action)
        assertEquals("1", a.actionPayload)
        assertEquals(listOf(1L), a.relatedTaskIds)
        assertTrue(a.text.contains("Llamar al banco"))
    }

    @Test fun marcalaComoCompletada_conDosPuntosCaptura() {
        val a = answer("márcala como completada: pagar la luz")
        assertEquals(AssistantAction.COMPLETE_TASK, a.action)
        assertEquals("2", a.actionPayload)
    }

    @Test fun marcaComoHecha_insensibleTildesCoincide() {
        // "revision medica" (sin tildes) debe coincidir con «Revisión médica».
        val a = answer("marca como hecha revision medica")
        assertEquals(AssistantAction.COMPLETE_TASK, a.action)
        assertEquals("3", a.actionPayload)
    }

    @Test fun marcaComoTerminada_ignoraStopwords() {
        val tasks = listOf(TaskEntity(id = 7, title = "Compra del súper"))
        val a = answer("marca como terminada la compra del super", tasks)
        assertEquals(AssistantAction.COMPLETE_TASK, a.action)
        assertEquals("7", a.actionPayload)
    }

    // --- Honestidad sin acción ---

    @Test fun marcaComoHechaPelada_guiaHonestaSinAccion() {
        val a = answer("marca como hecha")
        assertEquals(AssistantAction.NONE, a.action)
        assertTrue(a.actionPayload.isEmpty())
        assertTrue(a.text.lowercase().contains("qué tarea"))
    }

    @Test fun marcaComoHechaSinCoincidencia_guiaHonestaSinAccion() {
        val a = answer("marca como hecha lavar el coche")
        assertEquals(AssistantAction.NONE, a.action)
        assertTrue(a.actionPayload.isEmpty())
        assertTrue(a.text.lowercase().contains("no encuentro"))
    }

    @Test fun marcaComoHechaMultiplesCoincidencias_listaHonestaSinAccion() {
        val tasks = listOf(
            TaskEntity(id = 1, title = "Llamar a Ana"),
            TaskEntity(id = 2, title = "Llamar a Luis")
        )
        val a = answer("marca como hecha llamar", tasks)
        assertEquals(AssistantAction.NONE, a.action)
        assertTrue(a.actionPayload.isEmpty())
        assertEquals(listOf(1L, 2L), a.relatedTaskIds)
        assertTrue(a.text.lowercase().contains("varias"))
    }

    @Test fun marcaComoHechaSoloCoincideCompletada_noLaOfrece() {
        val tasks = listOf(TaskEntity(id = 1, title = "Llamar al banco", completed = true))
        val a = answer("marca como hecha llamar al banco", tasks)
        assertEquals(AssistantAction.NONE, a.action)
        assertTrue(a.actionPayload.isEmpty())
        assertTrue(a.text.lowercase().contains("no encuentro"))
    }

    // --- Guards anti-overreach (pasan ya) ---

    @Test fun noMarques_negacionNoCaptura() {
        val a = answer("no marques nada como hecha")
        assertEquals(AssistantAction.NONE, a.action)
        assertTrue(a.actionPayload.isEmpty())
    }

    @Test fun yaLaMarque_pasadoNoCaptura() {
        val a = answer("ya la marqué como hecha")
        assertEquals(AssistantAction.NONE, a.action)
        assertTrue(a.actionPayload.isEmpty())
    }

    // --- Regresiones (pasan ya) ---

    @Test fun recuerdame_regresionIntacta() {
        val a = answer("recuérdame llamar al banco mañana")
        assertEquals(AssistantAction.CREATE_TASK, a.action)
        assertEquals("llamar al banco mañana", a.actionPayload)
    }

    @Test fun apuntameNota_regresionIntacta() {
        val a = answer("apúntame comprar pan")
        assertEquals(AssistantAction.CREATE_NOTE, a.action)
        assertEquals("comprar pan", a.actionPayload)
    }
}
