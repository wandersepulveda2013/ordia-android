package com.ordia.app.assistant

import com.ordia.app.data.local.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * c.1001 — «borra/elimina/quita la tarea <nombre>» → AssistantAction.DELETE_TASK confirmable.
 *
 * PRE medido con sonda efímera /tmp/probe1001/DeleteProbe.kt (base 61544ad):
 * las 5 variantes de borrado caían al menú genérico — mentira por omisión:
 * la capacidad de borrar YA existía (OrdiaViewModel.deleteTask, usada en 6
 * pantallas) pero el asistente no la ofrecía. DESTRUCTIVA: doctrina
 * anti-borrado-a-ciegas — NADA se borra en silencio (el botón confirma);
 * varias coincidencias → lista honesta SIN acción; cero → guía honesta;
 * pelada → guía honesta; negación («no borres…») y pasado («ya borré…»)
 * disjuntas por el ancla ^; NUNCA borrado masivo («borra todo» no captura).
 */
class AssistantEngineDeleteCaptureTest {

    private val tasks = listOf(
        TaskEntity(id = 1, title = "Enviar el informe", dueAt = 1_800_000_000_000L),
        TaskEntity(id = 2, title = "Preparar la presentación", dueAt = 1_800_000_000_000L),
        TaskEntity(id = 3, title = "Compra del súper", dueAt = 1_800_000_000_000L),
        TaskEntity(id = 4, title = "Enviar el presupuesto", completed = true),
        TaskEntity(id = 5, title = "Pasear al perro"),
        TaskEntity(id = 6, title = "Notas viejas del informe", archived = true)
    )

    private fun ask(input: String) = AssistantEngine.answer(input, tasks, emptyList(), emptyList())

    private fun assertCaptures(input: String, expectedId: String) {
        val ans = ask(input)
        assertEquals("«$input» debe proponer eliminar: ${ans.text}", AssistantAction.DELETE_TASK, ans.action)
        assertEquals(expectedId, ans.actionPayload)
        // Confirmación obligatoria (NUNCA borrado silencioso): la pregunta
        // nombra la tarea y advierte que se borrará.
        assertTrue("advierte que se borrará: ${ans.text}", "¿" in ans.text)
    }

    @Test fun delete_borra() = assertCaptures("borra la tarea del informe", "1")
    @Test fun delete_elimina() = assertCaptures("elimina la tarea de la compra del súper", "3")
    @Test fun delete_borrarInfinitivo() = assertCaptures("borrar la tarea de la presentación", "2")
    @Test fun delete_quita() = assertCaptures("quita la tarea de pasear al perro", "5")
    @Test fun delete_sinLa() = assertCaptures("eliminar tarea del informe", "1")

    // c.1022 — pin de honestidad: deleteTask ARCHIVA (recuperable; el nombre
    // canónico de la app es «Archivar», string task_detail_archive; el borrado
    // definitivo es otra acción explícita en la pantalla Archivo). La
    // confirmación debe decir la consecuencia real, nunca «definitiva».
    @Test
    fun delete_confirmacionHonestaNuncaDefinitiva() {
        val ans = ask("borra la tarea del informe")
        assertEquals(AssistantAction.DELETE_TASK, ans.action)
        assertTrue("consecuencia honesta (recuperable): ${ans.text}", "recuperarla desde Archivo" in ans.text)
        assertTrue("NUNCA promete borrado definitivo: ${ans.text}", "definitiva" !in ans.text)
    }

    @Test
    fun delete_tareaCompletadaTambienBorrable() {
        val ans = ask("borra la tarea de enviar el presupuesto")
        assertEquals(AssistantAction.DELETE_TASK, ans.action)
        assertEquals("4", ans.actionPayload)
    }

    @Test
    fun delete_pelada_guiaHonestaSinAccion() {
        val ans = ask("borra la tarea")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.actionPayload.isEmpty())
        assertFalse("guía honesta, no menú: ${ans.text}", ans.text.startsWith("Puedo organizar"))
    }

    @Test
    fun delete_sinCoincidencia_guiaHonesta() {
        val ans = ask("elimina la tarea de declarar impuestos")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.text.startsWith("No encuentro ninguna tarea"))
    }

    @Test
    fun delete_variasCoincidencias_listaHonestaSinAccion() {
        val ans = ask("borra la tarea de enviar")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.actionPayload.isEmpty())
        assertEquals(listOf(1L, 4L), ans.relatedTaskIds)
        assertTrue("nombra opciones: ${ans.text}", "«" in ans.text)
    }

    @Test
    fun delete_archivadaNuncaOfrecida() {
        val ans = ask("borra la tarea de notas viejas")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.text.startsWith("No encuentro ninguna tarea"))
    }

    @Test
    fun delete_negacion_nuncaCaptura() {
        val ans = ask("no borres la tarea del informe")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.actionPayload.isEmpty())
    }

    @Test
    fun delete_pasado_nuncaCaptura() {
        val ans = ask("ya borré la tarea del informe")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.actionPayload.isEmpty())
    }

    @Test
    fun delete_global_nuncaBorradoMasivo() {
        val ans = ask("borra todo")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.actionPayload.isEmpty())
        assertTrue(ans.relatedTaskIds.isEmpty())
    }

    @Test
    fun delete_controlesHermanosIntactos() {
        assertEquals(AssistantAction.POSTPONE_TASK, ask("pospón el informe").action)
        assertEquals(AssistantAction.COMPLETE_TASK, ask("marca como hecha enviar el informe").action)
        assertEquals(AssistantAction.CREATE_TASK, ask("recuérdame llamar al banco").action)
    }
}
