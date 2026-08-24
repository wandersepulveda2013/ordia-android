package com.ordia.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// c.990: lateral (a) de la sonda PERSISTENTE
// `tools/probe/AssistantTaskCreationProbe.kt` — «crea/añade/agrega (una)
// tarea…» (imperativo explícito de tarea, hermano del «recuérdame …»
// c.986). Sonda efímera PRE `/tmp/probe990/CreaTareaProbe.kt` (HEAD
// c8ab66a): 7/7 capturas GAP al menú genérico + 3/3 peladas al menú en
// vez de la guía honesta; 5/5 guards correctos; 3/3 regresiones
// hermanas intactas. Doctrina anti-overreach (UNA forma por ciclo):
// sólo el imperativo con la palabra «tarea» — el verbo desnudo
// («crea una tabla», «quiero crear una tarea») NUNCA captura.
// NUNCA tarea vacía/basura: pelada y conector pelado «de» (doctrina
// c.988) responden guía honesta SIN acción.
class AssistantEngineCreaTareaCaptureTest {

    private fun ask(q: String) = AssistantEngine.answer(q, emptyList(), emptyList(), emptyList())

    // ---------- capturas ----------

    @Test fun creaUnaTareaDosPuntos_creaTarea() {
        val answer = ask("crea una tarea: llamar a Ana")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("llamar a Ana", answer.actionPayload)
    }

    @Test fun crearTareaDirecta_creaTarea() {
        val answer = ask("crear tarea pagar la luz mañana")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("pagar la luz mañana", answer.actionPayload)
    }

    @Test fun anadeUnaTareaDosPuntos_creaTarea() {
        val answer = ask("añade una tarea: sacar al perro")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("sacar al perro", answer.actionPayload)
    }

    @Test fun agregaUnaTareaDirecta_creaTarea() {
        val answer = ask("agrega una tarea llamar al dentista")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("llamar al dentista", answer.actionPayload)
    }

    @Test fun anadirTarea_creaTarea() {
        val answer = ask("añadir tarea comprar leche")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("comprar leche", answer.actionPayload)
    }

    @Test fun agregarUnaTareaDosPuntos_creaTarea() {
        val answer = ask("agregar una tarea: pagar el alquiler")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("pagar el alquiler", answer.actionPayload)
    }

    @Test fun creaTareaSinArticulo_creaTarea() {
        val answer = ask("crea tarea llamar al banco")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("llamar al banco", answer.actionPayload)
    }

    // ---------- peladas: guía honesta SIN acción (NUNCA tarea vacía/basura) ----------

    @Test fun creaUnaTareaPelada_guiaHonestaSinAccion() {
        val answer = ask("crea una tarea")
        assertEquals(AssistantAction.NONE, answer.action)
        assertEquals("", answer.actionPayload)
        assertTrue(answer.text.contains("tarea", ignoreCase = true))
    }

    @Test fun anadeUnaTareaDosPuntosPelada_guiaHonestaSinAccion() {
        val answer = ask("añade una tarea:")
        assertEquals(AssistantAction.NONE, answer.action)
        assertEquals("", answer.actionPayload)
    }

    @Test fun creaUnaTareaConectorPelado_guiaHonestaSinAccion() {
        val answer = ask("crea una tarea de")
        assertEquals(AssistantAction.NONE, answer.action)
        assertEquals("", answer.actionPayload)
    }

    // ---------- guards: NUNCA CREATE_TASK ----------

    @Test fun creaUnaTabla_noCaptura() {
        assertNotEquals(AssistantAction.CREATE_TASK, ask("crea una tabla").action)
    }

    @Test fun creaUnaNota_sigueSiendoNota() {
        val answer = ask("crea una nota: comprar pan")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("comprar pan", answer.actionPayload)
    }

    @Test fun perifrasis_noCaptura() {
        assertNotEquals(AssistantAction.CREATE_TASK, ask("quiero crear una tarea").action)
    }

    @Test fun sustantivoPasado_noCaptura() {
        assertNotEquals(AssistantAction.CREATE_TASK, ask("la tarea creada ayer").action)
    }

    @Test fun agregaUnaTareaConectorPelado_noCreaBasura() {
        val answer = ask("agrega una tarea de")
        assertNotEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("", answer.actionPayload)
    }

    // ---------- regresiones hermanas ----------

    @Test fun recuerdame_sigueCreandoTarea() {
        val answer = ask("recuérdame llamar al banco mañana")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("llamar al banco mañana", answer.actionPayload)
    }

    @Test fun tomaNota_sigueSiendoNota() {
        val answer = ask("toma nota: comprar pan")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("comprar pan", answer.actionPayload)
    }
}
