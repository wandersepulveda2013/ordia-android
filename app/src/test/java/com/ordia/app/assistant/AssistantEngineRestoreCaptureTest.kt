package com.ordia.app.assistant

import com.ordia.app.data.local.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * c.1004 — «recupera/desarchiva/restaura la tarea <nombre>» →
 * AssistantAction.RESTORE_TASK confirmable (hermana de REOPEN_TASK c.1003:
 * recuperar una archivada por error). Espejo del cierre honesto de ARCHIVAR:
 * si el asistente puede retirar una tarea de la vista, también debe poder
 * traerla de vuelta.
 *
 * PRE medido con sonda efímera /tmp/probe1004/RestoreProbe.kt (base 0e8faba):
 * las 6 variantes de recuperación caían al menú genérico — mentira por
 * omisión: la capacidad YA existía (OrdiaViewModel.restoreArchived restaura
 * la tarea y REARMA los recordatorios del subárbol, c.225) pero el asistente
 * ni siquiera RECIBÍA las archivadas (la pantalla solo pasa state.tasks, que
 * el DAO sirve con archived = 0). Doctrina hermana de c.1001/c.1002/c.1003:
 * NADA se recupera en silencio (el botón confirma); varias coincidencias →
 * lista honesta SIN acción; cero → guía honesta; pelada → guía honesta;
 * negación («no recuperes…»), pasado («ya recuperé…») y 2ª persona
 * («¿recuperaste…?») disjuntas por el ancla ^; la palabra «tarea» es
 * obligatoria (anti-overreach: «recupera mi cuenta» NUNCA entra). Solo se
 * ofrecen tareas ARCHIVADAS (una pendiente no necesita recuperarse).
 */
class AssistantEngineRestoreCaptureTest {

    private val archived = listOf(
        TaskEntity(id = 11, title = "Declaración de la renta", completed = true, archived = true),
        TaskEntity(id = 12, title = "Revisar el contrato del piso", archived = true),
        TaskEntity(id = 13, title = "Recoger el paquete del vecino", archived = true),
        TaskEntity(id = 14, title = "Revisar el contrato del garaje", archived = true)
    )

    private val active = listOf(
        TaskEntity(id = 21, title = "Compra del súper"),
        TaskEntity(id = 22, title = "Enviar el informe", completed = true, completedAt = 1_700_000_000_000L)
    )

    private fun ask(input: String) =
        AssistantEngine.answer(input, active, emptyList(), emptyList(), archivedTasks = archived)

    private fun assertCaptures(input: String, expectedId: String) {
        val ans = ask(input)
        assertEquals("«$input» debe proponer recuperar: ${ans.text}", AssistantAction.RESTORE_TASK, ans.action)
        assertEquals(expectedId, ans.actionPayload)
        // Confirmación obligatoria (NUNCA recuperación silenciosa): la pregunta
        // nombra la tarea.
        assertTrue("pregunta de confirmación: ${ans.text}", "¿" in ans.text)
    }

    @Test fun restore_recupera() = assertCaptures("recupera la tarea de la renta", "11")
    @Test fun restore_desarchiva() = assertCaptures("desarchiva la tarea de recoger el paquete del vecino", "13")
    @Test fun restore_restaura() = assertCaptures("restaura la tarea del contrato del piso", "12")
    @Test fun restore_recuperarInfinitivo() = assertCaptures("recuperar la tarea de la renta", "11")
    @Test fun restore_desarchivarConDosPuntos() = assertCaptures("desarchivar la tarea: declaración de la renta", "11")
    @Test fun restore_restaurarInfinitivo() = assertCaptures("restaurar la tarea de recoger el paquete", "13")

    @Test
    fun restore_pendienteNuncaOfrecida() {
        val ans = ask("recupera la tarea de la compra del súper")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.text.startsWith("No encuentro ninguna tarea archivada"))
    }

    @Test
    fun restore_completadaNoArchivadaNuncaOfrecida() {
        val ans = ask("recupera la tarea de enviar el informe")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.text.startsWith("No encuentro ninguna tarea archivada"))
    }

    @Test
    fun restore_pelada_guiaHonestaSinAccion() {
        val ans = ask("recupera la tarea")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.actionPayload.isEmpty())
        assertFalse("guía honesta, no menú: ${ans.text}", ans.text.startsWith("Puedo organizar"))
    }

    @Test
    fun restore_sinContenidoTrasDosPuntos_guiaHonestaSinAccion() {
        val ans = ask("restaura la tarea:")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.actionPayload.isEmpty())
        assertFalse("guía honesta, no menú: ${ans.text}", ans.text.startsWith("Puedo organizar"))
    }

    @Test
    fun restore_sinCoincidencia_guiaHonesta() {
        val ans = ask("desarchiva la tarea del dentista")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.text.startsWith("No encuentro ninguna tarea archivada"))
    }

    @Test
    fun restore_variasCoincidencias_listaHonestaSinAccion() {
        val ans = ask("recupera la tarea del contrato")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.actionPayload.isEmpty())
        assertEquals(listOf(12L, 14L), ans.relatedTaskIds)
        assertTrue("nombra opciones: ${ans.text}", "«" in ans.text)
    }

    @Test
    fun restore_negacion_nuncaCaptura() {
        val ans = ask("no recuperes la tarea de la renta")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.actionPayload.isEmpty())
    }

    @Test
    fun restore_pasado_nuncaCaptura() {
        val ans = ask("ya recuperé la tarea de la renta")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.actionPayload.isEmpty())
    }

    @Test
    fun restore_segundaPersona_nuncaCaptura() {
        val ans = ask("¿recuperaste la tarea de la renta?")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.actionPayload.isEmpty())
    }

    @Test
    fun restore_sinPalabraTarea_nuncaCaptura() {
        // Anti-overreach: «recupera mi cuenta» no es una orden sobre tareas.
        val ans = ask("recupera mi cuenta del banco")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.actionPayload.isEmpty())
    }

    @Test
    fun restore_global_nuncaRecuperacionMasiva() {
        val ans = ask("recupera la tarea de todo")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.actionPayload.isEmpty())
        assertTrue(ans.relatedTaskIds.isEmpty())
    }

    @Test
    fun restore_controlesHermanosIntactos() {
        assertEquals(AssistantAction.REOPEN_TASK, ask("reabre la tarea de enviar el informe").action)
        assertEquals(AssistantAction.CANCEL_TASK, ask("descarta la tarea de la compra del súper").action)
        assertEquals(AssistantAction.CREATE_TASK, ask("recuérdame llamar al banco").action)
    }
}
