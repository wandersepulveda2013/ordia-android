package com.ordia.app.assistant

import com.ordia.app.data.local.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * c.1021 — «archiva/archivar la tarea <nombre>» → AssistantAction.ARCHIVE_TASK
 * confirmable. Espejo del cierre honesto de RECUPERAR (c.1004): si el
 * asistente puede traer una tarea de vuelta del Archivo, también debe poder
 * enviarla allí — el ciclo de vida archivar ↔ recuperar queda cerrado por
 * voz/texto. Lateral assistant documentada ABIERTA (fila c.1001-c.1004).
 *
 * PRE medido con sonda efímera /tmp/probe1019/ArchiveProbe.kt (motor real
 * vía tools/run_probe.sh, base 171e448): las 6 variantes de archivo caían
 * al menú genérico — mentira por omisión: la capacidad YA existía
 * (OrdiaViewModel.deleteTask archiva el subárbol y cancela TODOS los
 * recordatorios, c.225) y la hermana «desarchiva/recupera» (c.1004) sí
 * capturaba. Doctrina hermana de c.1001/c.1002/c.1003/c.1004: NADA se
 * archiva en silencio (el botón confirma); varias coincidencias → lista
 * honesta SIN acción; cero → guía honesta; pelada → guía honesta; negación
 * («no archives…»), pasado («ya archivé…») y 2ª persona («¿archivaste…?»)
 * disjuntas por el ancla ^; la palabra «tarea» es obligatoria
 * (anti-overreach: «archiva mi cuenta» NUNCA entra); «desarchiva…» sigue
 * yendo a RESTORE (prefijos disjuntos). Solo se ofrecen tareas NO
 * archivadas (paridad deleteCapture: una completada es justo el caso de
 * limpieza; una ya archivada no necesita archivarse).
 */
class AssistantEngineArchiveCaptureTest {

    private val active = listOf(
        TaskEntity(id = 21, title = "Compra del súper"),
        TaskEntity(id = 22, title = "Enviar el informe", completed = true, completedAt = 1_700_000_000_000L),
        TaskEntity(id = 23, title = "Revisar el contrato del piso"),
        TaskEntity(id = 24, title = "Revisar el contrato del garaje")
    )

    private val archived = listOf(
        TaskEntity(id = 11, title = "Declaración de la renta", completed = true, archived = true)
    )

    private fun ask(input: String) =
        AssistantEngine.answer(input, active, emptyList(), emptyList(), archivedTasks = archived)

    private fun assertCaptures(input: String, expectedId: String) {
        val ans = ask(input)
        assertEquals("«$input» debe proponer archivar: ${ans.text}", AssistantAction.ARCHIVE_TASK, ans.action)
        assertEquals(expectedId, ans.actionPayload)
        // Confirmación obligatoria (NUNCA archivo silencioso): la pregunta
        // nombra la tarea y la consecuencia honesta (recuperable).
        assertTrue("pregunta de confirmación: ${ans.text}", "¿" in ans.text)
        assertTrue("menciona que es recuperable: ${ans.text}", "Archivo" in ans.text)
    }

    @Test fun archive_archivaPendiente() = assertCaptures("archiva la tarea de la compra del súper", "21")
    @Test fun archive_archivarCompletada() = assertCaptures("archivar la tarea de enviar el informe", "22")
    @Test fun archive_archivaConDosPuntos() = assertCaptures("archiva la tarea: revisar el contrato del piso", "23")
    @Test fun archive_archivarParcial() = assertCaptures("archivar la tarea de la compra", "21")
    @Test fun archive_archivaContratoGaraje() = assertCaptures("archiva la tarea del contrato del garaje", "24")
    @Test fun archive_archivaInforme() = assertCaptures("archiva la tarea de enviar el informe", "22")

    @Test
    fun archive_pelada_guiaHonestaSinAccion() {
        val ans = ask("archiva la tarea")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.actionPayload.isEmpty())
        assertTrue(ans.text.startsWith("¿Qué tarea archivo?"))
    }

    @Test
    fun archive_sinCoincidencia_guiaHonestaSinAccion() {
        val ans = ask("archiva la tarea de pagar el impuesto")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.text.startsWith("No encuentro ninguna tarea"))
    }

    @Test
    fun archive_variasCoincidencias_listaHonestaSinAccion() {
        val ans = ask("archiva la tarea del contrato")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.text.startsWith("Hay varias tareas que coinciden"))
        assertTrue(ans.relatedTaskIds.containsAll(listOf(23L, 24L)))
    }

    @Test
    fun archive_yaArchivadaNuncaOfrecida() {
        val ans = ask("archiva la tarea de la renta")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.text.startsWith("No encuentro ninguna tarea"))
    }

    @Test
    fun archive_negacionDisjunta() {
        val ans = ask("no archives la tarea de la compra")
        assertEquals(AssistantAction.NONE, ans.action)
    }

    @Test
    fun archive_pasadoDisjunto() {
        val ans = ask("ya archivé la tarea de la compra")
        assertEquals(AssistantAction.NONE, ans.action)
    }

    @Test
    fun archive_segundaPersonaDisjunta() {
        val ans = ask("¿archivaste la tarea de la compra?")
        assertEquals(AssistantAction.NONE, ans.action)
    }

    @Test
    fun archive_antiOverreachSinPalabraTarea() {
        // «archiva mi cuenta» (u otro objeto) NUNCA entra: la palabra
        // «tarea» es obligatoria (misma doctrina que las hermanas).
        val ans = ask("archiva mi cuenta")
        assertEquals(AssistantAction.NONE, ans.action)
    }

    @Test
    fun archive_desarchivaSigueSiendoRestore() {
        // Pin cruzado: «desarchiva…» pertenece a la rama RESTORE (c.1004);
        // los prefijos anclados son disjuntos en ambos sentidos.
        val ans = ask("desarchiva la tarea de la renta")
        assertEquals(AssistantAction.RESTORE_TASK, ans.action)
        assertEquals("11", ans.actionPayload)
    }

    @Test
    fun archive_regresionBorra() {
        val ans = ask("borra la tarea de la compra del súper")
        assertEquals(AssistantAction.DELETE_TASK, ans.action)
        assertEquals("21", ans.actionPayload)
    }

    @Test
    fun archive_regresionDescarta() {
        val ans = ask("descarta la tarea de la compra del súper")
        assertEquals(AssistantAction.CANCEL_TASK, ans.action)
        assertEquals("21", ans.actionPayload)
    }

    @Test
    fun archive_regresionRecupera() {
        val ans = ask("recupera la tarea de la renta")
        assertEquals(AssistantAction.RESTORE_TASK, ans.action)
        assertEquals("11", ans.actionPayload)
    }
}
