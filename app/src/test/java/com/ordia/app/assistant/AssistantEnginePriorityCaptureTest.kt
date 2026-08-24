package com.ordia.app.assistant

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * c.1006 — «marca/pon la tarea <nombre> como importante|urgente|prioridad
 * alta|baja» → AssistantAction.SET_PRIORITY confirmable (hermana de
 * COMPLETE_TASK c.997 / POSTPONE_TASK c.999: acción puntual sobre la tarea
 * NOMBRADA). Lateral PRIORITY de la sonda de capturas (c.1002).
 *
 * PRE medido con sonda efímera /tmp/probe1006/PriorityProbe.kt (base
 * b0de96a): las 6 variantes de marcado caían en la CONSULTA de prioridad —
 * «marca la tarea del informe como importante» respondía «No tienes tareas
 * marcadas como importantes.» a una ORDEN de acción (robo de rama, mentira
 * por omisión: ni siquiera existía la tarea en ese nivel todavía). Doctrina
 * hermana de c.997…c.1004: NADA se marca en silencio (el botón confirma vía
 * vm.setTaskPriority, capacidad real que persiste y refresca el widget);
 * varias coincidencias → lista honesta SIN acción; cero → guía honesta;
 * pelada («marca como importante») → guía honesta SIN acción (NUNCA marcar
 * a ciegas, doctrina c.1000); negación («no marques…»), pasado («ya
 * marqué…») y 2ª persona («¿marcaste…?») disjuntas por el ancla ^. Solo se
 * ofrecen tareas PENDIENTES (una completada no necesita prioridad); si la
 * candidata ya tiene ese nivel se responde honesto sin acción. Mapeo paridad
 * con la consulta hermana (priorityIntent): importante → HIGH (nivel alto
 * no urgente), urgente → URGENT, «prioridad alta» → HIGH, «prioridad baja»
 * → LOW.
 */
class AssistantEnginePriorityCaptureTest {

    private val tasks = listOf(
        TaskEntity(id = 21, title = "Compra del súper"),
        TaskEntity(id = 22, title = "Enviar el informe", completed = true, completedAt = 1_700_000_000_000L),
        TaskEntity(id = 23, title = "Llamar al banco"),
        TaskEntity(id = 24, title = "Revisar el contrato del piso"),
        TaskEntity(id = 25, title = "Revisar el contrato del garaje"),
        TaskEntity(id = 26, title = "Pagar la luz", priority = TaskPriority.HIGH)
    )

    private fun ask(input: String) =
        AssistantEngine.answer(input, tasks, emptyList(), emptyList())

    private fun assertCaptures(input: String, expectedId: String, level: TaskPriority) {
        val ans = ask(input)
        assertEquals("«$input» debe proponer el cambio de prioridad: ${ans.text}", AssistantAction.SET_PRIORITY, ans.action)
        assertEquals("${expectedId}:${level.name}", ans.actionPayload)
        // Confirmación obligatoria (NUNCA marcado silencioso): la pregunta
        // nombra la tarea y el nivel.
        assertTrue("pregunta de confirmación: ${ans.text}", "¿" in ans.text)
    }

    @Test fun priority_marcaSuffixImportante() = assertCaptures("marca la tarea de llamar al banco como importante", "23", TaskPriority.HIGH)
    @Test fun priority_marcaPrefixImportante() = assertCaptures("marca como importante llamar al banco", "23", TaskPriority.HIGH)
    @Test fun priority_marcaUrgente() = assertCaptures("marca la tarea del contrato del piso como urgente", "24", TaskPriority.URGENT)
    @Test fun priority_ponPrioridadAltaATarea() = assertCaptures("pon prioridad alta a la tarea de la compra del súper", "21", TaskPriority.HIGH)
    @Test fun priority_ponTareaEnPrioridadAlta() = assertCaptures("pon la tarea de llamar al banco en prioridad alta", "23", TaskPriority.HIGH)
    @Test fun priority_ponPrioridadBaja() = assertCaptures("pon la tarea del contrato del garaje en prioridad baja", "25", TaskPriority.LOW)
    @Test fun priority_ponComoImportante() = assertCaptures("pon la tarea de la compra del súper como importante", "21", TaskPriority.HIGH)
    @Test fun priority_ponle() = assertCaptures("ponle prioridad alta a la tarea de llamar al banco", "23", TaskPriority.HIGH)

    @Test
    fun priority_pelada_guiaHonestaSinAccion() {
        val ans = ask("marca como importante")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.actionPayload.isEmpty())
        assertTrue(ans.text.startsWith("¿Qué tarea marco como importante?"))
    }

    @Test
    fun priority_stopwordsPuros_guiaHonestaSinAccion() {
        // Doctrina c.1000: contenido de stopwords puros («marca la tarea como
        // importante») NUNCA marca a ciegas.
        val ans = ask("marca la tarea como importante")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.actionPayload.isEmpty())
        assertTrue(ans.text.startsWith("¿Qué tarea marco como importante?"))
    }

    @Test
    fun priority_ponPelada_guiaHonestaSinAccion() {
        val ans = ask("pon prioridad alta a la tarea")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.actionPayload.isEmpty())
        assertTrue(ans.text.startsWith("¿Qué tarea pongo en prioridad alta?"))
    }

    @Test
    fun priority_sinCoincidencia_guiaHonesta() {
        val ans = ask("marca la tarea del presupuesto como importante")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.text.startsWith("No encuentro ninguna tarea pendiente que coincida"))
    }

    @Test
    fun priority_variasCoincidencias_listaHonestaSinAccion() {
        val ans = ask("marca la tarea del contrato como importante")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.actionPayload.isEmpty())
        assertTrue(ans.text.startsWith("Hay varias tareas pendientes que coinciden"))
        assertTrue(ans.relatedTaskIds.containsAll(listOf(24L, 25L)))
    }

    @Test
    fun priority_completadaNuncaOfrecida() {
        // «Enviar el informe» está completada: priorizarla no aporta nada.
        val ans = ask("marca la tarea de enviar el informe como importante")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.text.startsWith("No encuentro ninguna tarea pendiente que coincida"))
    }

    @Test
    fun priority_yaEnEseNivel_respuestaHonestaSinAccion() {
        val ans = ask("marca la tarea de pagar la luz como importante")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.text.startsWith("«Pagar la luz» ya está"))
    }

    @Test
    fun priority_negacionDisjunta() {
        val ans = ask("no marques la tarea de llamar al banco como importante")
        assertEquals(AssistantAction.NONE, ans.action)
    }

    @Test
    fun priority_pasadoDisjunto() {
        val ans = ask("ya marqué la tarea de llamar al banco como importante")
        assertEquals(AssistantAction.NONE, ans.action)
    }

    @Test
    fun priority_segundaPersonaDisjunta() {
        val ans = ask("¿marcaste la tarea de llamar al banco como importante?")
        assertEquals(AssistantAction.NONE, ans.action)
    }

    @Test
    fun priority_consultaHermanaIntacta() {
        // «qué tareas importantes tengo» sigue siendo la CONSULTA de prioridad
        // (c.783): lista las que YA están marcadas, sin acción.
        val ans = ask("¿qué tareas importantes tengo?")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue("Pagar la luz" in ans.text)
    }

    @Test
    fun priority_hermanaMarcaComoHechaIntacta() {
        val ans = ask("marca como hecha la compra del súper")
        assertEquals(AssistantAction.COMPLETE_TASK, ans.action)
        assertEquals("21", ans.actionPayload)
    }
}
