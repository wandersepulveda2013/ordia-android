package com.ordia.app.assistant

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * c.1002 — «descarta/cancela la tarea <nombre>» → AssistantAction.CANCEL_TASK confirmable.
 *
 * PRE medido con sonda de descubrimiento /tmp/probe1002/DiscoveryProbe.kt
 * (base a9c2347, unión c.1000+c.1001): las 3 variantes de descarte caían al
 * menú genérico — mentira por omisión: la capacidad YA existía
 * (OrdiaViewModel.cancelTask, c.426 — tercer cierre honesto que NO borra y
 * NO falsea logros) pero el asistente no la ofrecía. Hermana de
 * DELETE_TASK (c.1001) pero NO destructiva: descartar conserva el registro
 * (status=CANCELLED) y no cuenta como logro. Doctrina anti-acción-a-ciegas:
 * NADA se descarta en silencio (el botón confirma vía vm.cancelTask);
 * varias coincidencias → lista honesta SIN acción; cero → guía honesta;
 * pelada → guía honesta; SOLO pendientes (TaskRules.isActive: completadas,
 * archivadas y ya-descartadas NUNCA se ofrecen — el botón «Descartar» del
 * detalle también solo se muestra si isActive); negación («no descartes…»)
 * y pasado («ya descarté…») disjuntas por el ancla ^; la palabra «tarea»
 * es obligatoria («cancela el recordatorio» NUNCA entra).
 */
class AssistantEngineCancelCaptureTest {

    private val tasks = listOf(
        TaskEntity(id = 1, title = "Enviar el informe", dueAt = 1_800_000_000_000L),
        TaskEntity(id = 2, title = "Preparar la presentación", dueAt = 1_800_000_000_000L),
        TaskEntity(id = 3, title = "Compra del súper", dueAt = 1_800_000_000_000L),
        TaskEntity(id = 4, title = "Enviar el presupuesto", completed = true),
        TaskEntity(id = 5, title = "Pasear al perro"),
        TaskEntity(id = 6, title = "Notas viejas del informe", archived = true),
        TaskEntity(id = 7, title = "Idea descartada", status = TaskStatus.CANCELLED),
        TaskEntity(id = 8, title = "Enviar la factura", dueAt = 1_800_000_000_000L)
    )

    private fun ask(input: String) = AssistantEngine.answer(input, tasks, emptyList(), emptyList())

    private fun assertCaptures(input: String, expectedId: String) {
        val ans = ask(input)
        assertEquals("«$input» debe proponer descartar: ${ans.text}", AssistantAction.CANCEL_TASK, ans.action)
        assertEquals(expectedId, ans.actionPayload)
        // Confirmación obligatoria (NUNCA descarte silencioso): la pregunta
        // nombra la tarea y avisa de que no contará como completada.
        assertTrue("pregunta de confirmación: ${ans.text}", "¿" in ans.text)
    }

    @Test fun cancel_descarta() = assertCaptures("descarta la tarea del informe", "1")
    @Test fun cancel_cancela() = assertCaptures("cancela la tarea de la compra del súper", "3")
    @Test fun cancel_descartarInfinitivo() = assertCaptures("descartar la tarea de la presentación", "2")
    @Test fun cancel_cancelarInfinitivo() = assertCaptures("cancelar la tarea de pasear al perro", "5")
    @Test fun cancel_sinLa() = assertCaptures("descarta tarea del informe", "1")

    @Test
    fun cancel_pelada_guiaHonestaSinAccion() {
        val ans = ask("descarta la tarea")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.actionPayload.isEmpty())
    }

    @Test
    fun cancel_sinCoincidencia_guiaHonesta() {
        val ans = ask("cancela la tarea de declarar impuestos")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.actionPayload.isEmpty())
    }

    @Test
    fun cancel_variasCoincidencias_listaHonestaSinAccion() {
        val ans = ask("descarta la tarea de enviar")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.relatedTaskIds.size > 1)
    }

    @Test
    fun cancel_completadaNuncaOfrecida() {
        val ans = ask("descarta la tarea de enviar el presupuesto")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.actionPayload.isEmpty())
    }

    @Test
    fun cancel_archivadaNuncaOfrecida() {
        val ans = ask("descarta la tarea de notas viejas del informe")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.actionPayload.isEmpty())
    }

    @Test
    fun cancel_yaDescartadaNuncaOfrecida() {
        val ans = ask("descarta la tarea de idea descartada")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.actionPayload.isEmpty())
    }

    @Test
    fun cancel_negacion_nuncaCaptura() {
        assertEquals(AssistantAction.NONE, ask("no descartes la tarea del informe").action)
    }

    @Test
    fun cancel_pasado_nuncaCaptura() {
        assertEquals(AssistantAction.NONE, ask("ya descarté la tarea del informe").action)
    }

    @Test
    fun cancel_sinPalabraTarea_nuncaCaptura() {
        // La palabra «tarea» es obligatoria: «cancela el recordatorio»
        // NUNCA entra (los recordatorios no son ámbito de esta rama).
        assertEquals(AssistantAction.NONE, ask("cancela el recordatorio").action)
    }

    @Test
    fun cancel_controlesHermanosIntactos() {
        assertEquals(AssistantAction.DELETE_TASK, ask("borra la tarea del informe").action)
        assertEquals(AssistantAction.COMPLETE_TASK, ask("marca como hecha enviar el informe").action)
        assertEquals(AssistantAction.POSTPONE_TASK, ask("pospón el informe").action)
        assertEquals(AssistantAction.CREATE_TASK, ask("recuérdame llamar al banco").action)
    }
}
