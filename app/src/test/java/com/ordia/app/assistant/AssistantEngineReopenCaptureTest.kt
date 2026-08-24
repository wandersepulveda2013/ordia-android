package com.ordia.app.assistant

import com.ordia.app.data.local.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * c.1003 — «reabre/reactiva/desmarca la tarea <nombre>» → AssistantAction.REOPEN_TASK
 * confirmable (hermana de COMPLETE_TASK c.997: reabrir una completada por error).
 *
 * PRE medido con sonda efímera /tmp/probe1002/StateTransitionProbe.kt (base
 * a9c2347): las 6 variantes de reapertura caían al menú genérico — mentira por
 * omisión: la capacidad de reabrir YA existía (OrdiaViewModel.toggleTask sobre
 * una completada la reabre, con reversión de la ocurrencia recurrente generada,
 * c.260) pero el asistente no la ofrecía. Doctrina hermana de c.997/c.1001:
 * NADA se reabre en silencio (el botón confirma); varias coincidencias → lista
 * honesta SIN acción; cero → guía honesta; pelada → guía honesta; negación
 * («no reabras…»), pasado («ya reabrí…») y 2ª persona («¿reabriste…?»)
 * disjuntas por el ancla ^; NUNCA reapertura masiva («reactiva todo» no
 * captura). Solo se ofrecen tareas COMPLETADAS no archivadas (una pendiente ya
 * está abierta; una archivada se recupera desde su superficie, no se reabre a
 * ciegas).
 */
class AssistantEngineReopenCaptureTest {

    private val tasks = listOf(
        TaskEntity(id = 1, title = "Enviar el informe", completed = true, completedAt = 1_700_000_000_000L),
        TaskEntity(id = 2, title = "Preparar la presentación", completed = true, completedAt = 1_700_000_000_000L),
        TaskEntity(id = 3, title = "Compra del súper", completed = true, completedAt = 1_700_000_000_000L),
        TaskEntity(id = 4, title = "Enviar el presupuesto", completed = true, completedAt = 1_700_000_000_000L),
        TaskEntity(id = 5, title = "Pasear al perro"),
        TaskEntity(id = 6, title = "Notas viejas del informe", completed = true, archived = true)
    )

    private fun ask(input: String) = AssistantEngine.answer(input, tasks, emptyList(), emptyList())

    private fun assertCaptures(input: String, expectedId: String) {
        val ans = ask(input)
        assertEquals("«$input» debe proponer reabrir: ${ans.text}", AssistantAction.REOPEN_TASK, ans.action)
        assertEquals(expectedId, ans.actionPayload)
        // Confirmación obligatoria (NUNCA reapertura silenciosa): la pregunta
        // nombra la tarea.
        assertTrue("pregunta de confirmación: ${ans.text}", "¿" in ans.text)
    }

    @Test fun reopen_reabre() = assertCaptures("reabre la tarea de la presentación", "2")
    @Test fun reopen_reactiva() = assertCaptures("reactiva la compra del súper", "3")
    @Test fun reopen_reabrirInfinitivo() = assertCaptures("reabrir la tarea de enviar el presupuesto", "4")
    @Test fun reopen_desmarca() = assertCaptures("desmarca la tarea de la presentación", "2")
    @Test fun reopen_marcaComoPendientePrefija() = assertCaptures("marca como pendiente enviar el presupuesto", "4")
    @Test fun reopen_marcaComoPendienteSufija() = assertCaptures("marca la tarea de la compra del súper como pendiente", "3")
    @Test fun reopen_vuelveAPonerPendiente() = assertCaptures("vuelve a poner pendiente la tarea de la presentación", "2")

    @Test
    fun reopen_pendienteNuncaOfrecida() {
        val ans = ask("reabre la tarea de pasear al perro")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.text.startsWith("No encuentro ninguna tarea completada"))
    }

    @Test
    fun reopen_archivadaNuncaOfrecida() {
        val ans = ask("reabre la tarea de notas viejas")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.text.startsWith("No encuentro ninguna tarea completada"))
    }

    @Test
    fun reopen_pelada_guiaHonestaSinAccion() {
        val ans = ask("reabre la tarea")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.actionPayload.isEmpty())
        assertFalse("guía honesta, no menú: ${ans.text}", ans.text.startsWith("Puedo organizar"))
    }

    @Test
    fun reopen_sinCoincidencia_guiaHonesta() {
        val ans = ask("reabre la tarea de declarar impuestos")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.text.startsWith("No encuentro ninguna tarea completada"))
    }

    @Test
    fun reopen_variasCoincidencias_listaHonestaSinAccion() {
        val ans = ask("reabre la tarea de enviar")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.actionPayload.isEmpty())
        assertEquals(listOf(1L, 4L), ans.relatedTaskIds)
        assertTrue("nombra opciones: ${ans.text}", "«" in ans.text)
    }

    @Test
    fun reopen_negacion_nuncaCaptura() {
        val ans = ask("no reabras la tarea de la presentación")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.actionPayload.isEmpty())
    }

    @Test
    fun reopen_pasado_nuncaCaptura() {
        val ans = ask("ya reabrí la tarea de la presentación")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.actionPayload.isEmpty())
    }

    @Test
    fun reopen_segundaPersona_nuncaCaptura() {
        val ans = ask("¿reabriste la tarea de la presentación?")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.actionPayload.isEmpty())
    }

    @Test
    fun reopen_global_nuncaReaperturaMasiva() {
        val ans = ask("reactiva todo")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.actionPayload.isEmpty())
        assertTrue(ans.relatedTaskIds.isEmpty())
    }

    @Test
    fun reopen_controlesHermanosIntactos() {
        assertEquals(AssistantAction.COMPLETE_TASK, ask("marca como hecha la tarea de pasear al perro").action)
        assertEquals(AssistantAction.DELETE_TASK, ask("borra la tarea de pasear al perro").action)
        assertEquals(AssistantAction.CREATE_TASK, ask("recuérdame llamar al banco").action)
    }
}
