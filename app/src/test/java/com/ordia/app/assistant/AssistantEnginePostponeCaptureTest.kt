package com.ordia.app.assistant

import com.ordia.app.data.local.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * c.999 — «pospón/aplaza <tarea> (para mañana)» → AssistantAction.POSTPONE_TASK confirmable.
 *
 * PRE medido con sonda efímera /tmp/probe999/DeferProbe.kt (base 56b0f5f):
 * «aplaza …» caía al menú genérico y «pospón <tarea>» caía en la rama de
 * CONSULTA «qué puedo posponer» (respondía una candidata; NUNCA movía la tarea
 * nombrada). Rama hermana de markDoneCapture (c.997/c.998): única coincidencia
 * sobre pendientes CON fecha → POSTPONE_TASK con id y botón confirmatorio
 * (NADA se pospone en silencio); cero → guía honesta; varias → lista honesta
 * SIN acción; sin fecha → explicación honesta; temporal ≠ mañana → limitación
 * honesta. El ancla ^ en imperativo disjunta negación («no pospongas…») y la
 * consulta («qué puedo posponer»).
 */
class AssistantEnginePostponeCaptureTest {

    private val tasks = listOf(
        TaskEntity(id = 1, title = "Enviar el informe", dueAt = 1_800_000_000_000L),
        TaskEntity(id = 2, title = "Preparar la presentación", dueAt = 1_800_000_000_000L),
        TaskEntity(id = 3, title = "Compra del súper", dueAt = 1_800_000_000_000L),
        TaskEntity(id = 4, title = "Llamar al banco", dueAt = 1_800_000_000_000L, completed = true),
        TaskEntity(id = 5, title = "Pasear al perro"),
        TaskEntity(id = 6, title = "La reunión con Ana", dueAt = 1_800_000_000_000L)
    )

    private fun ask(input: String) = AssistantEngine.answer(input, tasks, emptyList(), emptyList())

    private fun assertCaptures(input: String, expectedId: String) {
        val ans = ask(input)
        assertEquals("«$input» debe proponer posponer: ${ans.text}", AssistantAction.POSTPONE_TASK, ans.action)
        assertEquals(expectedId, ans.actionPayload)
    }

    @Test fun postpone_verboPospon() = assertCaptures("pospón el informe", "1")
    @Test fun postpone_verboAplazaConManana() = assertCaptures("aplaza la compra del súper para mañana", "3")
    @Test fun postpone_infinitivoAplazarAManana() = assertCaptures("aplazar la presentación a mañana", "2")
    @Test fun postpone_infinitivoPosponer() = assertCaptures("posponer el informe", "1")
    @Test fun postpone_hastaManana() = assertCaptures("aplaza el informe hasta mañana", "1")

    @Test
    fun postpone_pelada_guiaHonestaSinAccion() {
        val ans = ask("aplaza")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.actionPayload.isEmpty())
        assertFalse("guía honesta, no menú: ${ans.text}", ans.text.startsWith("Puedo organizar"))
    }

    @Test
    fun postpone_sinCoincidencia_guiaHonesta() {
        val ans = ask("aplaza el presupuesto")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.text.startsWith("No encuentro ninguna tarea pendiente"))
    }

    @Test
    fun postpone_variasCoincidencias_listaHonestaSinAccion() {
        val many = tasks + listOf(
            TaskEntity(id = 7, title = "Llamar al dentista", dueAt = 1_800_000_000_000L),
            TaskEntity(id = 8, title = "Llamar al proveedor", dueAt = 1_800_000_000_000L))
        val ans = AssistantEngine.answer("aplaza llamar", many, emptyList(), emptyList())
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.text.startsWith("Hay varias tareas pendientes"))
        assertTrue(ans.text.contains("Llamar al dentista") && ans.text.contains("Llamar al proveedor"))
    }

    @Test
    fun postpone_sinFecha_explicacionHonestaSinAccion() {
        val ans = ask("aplaza pasear al perro")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue("explica que falta fecha: ${ans.text}", ans.text.contains("no tiene fecha"))
    }

    @Test
    fun postpone_temporalNoSoportado_limitacionHonestaSinAccion() {
        val ans = ask("pospón la reunión para el lunes")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue("limitación honesta (solo mañana): ${ans.text}", ans.text.contains("mañana"))
        assertFalse(ans.text.startsWith("Puedo organizar"))
    }

    @Test
    fun postpone_completadaNuncaOfrecida_guiaHonesta() {
        val ans = ask("pospón llamar al banco")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.text.startsWith("No encuentro ninguna tarea pendiente"))
    }

    @Test
    fun postpone_negacion_noCaptura() {
        val ans = ask("no aplaces el informe")
        assertEquals(AssistantAction.NONE, ans.action)
    }

    @Test
    fun postpone_consultaQuePuedoPosponer_noEsAccion() {
        val ans = ask("qué puedo posponer")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.actionPayload.isEmpty())
    }

    @Test
    fun postpone_controlesHermanosIntactos() {
        assertEquals(AssistantAction.CREATE_TASK, ask("recuérdame llamar al banco").action)
        assertEquals(AssistantAction.COMPLETE_TASK, ask("marca como hecha enviar el informe").action)
        assertEquals(AssistantAction.COMPLETE_TASK, ask("completé el informe").action)
        assertEquals(AssistantAction.CREATE_NOTE, ask("apúntame comprar pan").action)
    }
}
