package com.ordia.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// c.991: lateral (f) de la sonda persistente de creación de tareas —
// «recuérdame:» (pelada CON «:») creaba una tarea BASURA con payload «:»
// (medido c.990 con sonda efímera /tmp/probe990/PeladaDosPuntosProbe.kt:
// action=CREATE_TASK, payload=":"). Causa raíz: el (.+) de
// REMIND_ME_WITH_CONTENT se tragaba el propio «:» al ser el «:» de la
// regex opcional (backtracking). NUNCA tarea basura (doctrina c.969:
// pelada → guía honesta SIN acción). Fix: extractor ([^:].*) — el
// contenido no puede empezar por «:». Rama hermana c.986; la pelada sin
// «:» ya guiaba honesta. Simetría con el extractor c.990 de
// createTaskCapture, que nació ya con ([^:].*).
class AssistantEngineRecuerdameDosPuntosPeladaTest {

    private fun ask(q: String) = AssistantEngine.answer(q, emptyList(), emptyList(), emptyList())

    // ---------- pelada con «:» — NUNCA tarea basura ----------

    @Test fun recuerdameDosPuntosPelada_guiaHonestaSinAccion() {
        val answer = ask("recuérdame:")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.text.contains("recuerde"))
    }

    @Test fun recuerdameDosPuntosSinTildePelada_guiaHonestaSinAccion() {
        val answer = ask("recuerdame:")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.text.contains("recuerde"))
    }

    // Pin conservador byte-idéntico (medido c.991): TODO mayúsculas va al
    // menú genérico — el motor no captura en mayúsculas sostenidas
    // (comportamiento preexistente global, FUERA de la unidad).
    @Test fun recuerdameDosPuntosMayusculasPelada_noCaptura() {
        val answer = ask("RECUÉRDAME:")
        assertEquals(AssistantAction.NONE, answer.action)
    }

    // ---------- regresiones hermanas (byte-idénticas) ----------

    @Test fun recuerdameDosPuntosConContenido_sigueCreandoTarea() {
        val answer = ask("recuérdame: sacar al perro")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("sacar al perro", answer.actionPayload)
    }

    @Test fun recuerdameSinDosPuntosConContenido_sigueCreandoTarea() {
        val answer = ask("recuérdame llamar al banco mañana")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("llamar al banco mañana", answer.actionPayload)
    }

    @Test fun recuerdamePeladaSinDosPuntos_sigueGuiandoHonesta() {
        val answer = ask("recuérdame")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.text.contains("recuerde"))
    }

    @Test fun recuerdameContenidoConDosPuntosInternos_conservaContenido() {
        val answer = ask("recuérdame: cita 14:30 con Ana")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("cita 14:30 con Ana", answer.actionPayload)
    }

    // ---------- guards (nunca capturan) ----------

    @Test fun recuerdameNoLlamar_sigueSinCapturar() {
        val answer = ask("recuérdame no llamar al banco")
        assertNotEquals(AssistantAction.CREATE_TASK, answer.action)
    }

    @Test fun crearTareaDosPuntos_sigueCreandoTarea() {
        val answer = ask("crea una tarea: llamar a Ana")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("llamar a Ana", answer.actionPayload)
    }
}
