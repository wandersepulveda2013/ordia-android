package com.ordia.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// c.993: lateral (c) de la sonda persistente de creación de tareas —
// despoje del «que» subordinado en «recuérdame que…». Medido con sonda
// efímera /tmp/probe993/RecuerdameQueProbe.kt (PRE, base 94b24f0):
// 4/4 capturas con residuo «que » en el payload (título sucio: «que
// tengo que llamar al banco»); la pelada-con-«que» («recuérdame que»)
// creaba tarea BASURA «que» (conector pelado, doctrina c.988); y la
// negación tras «que» («recuérdame que no llame a ana») creaba la tarea
// «que no llame a ana» — capturaba lo CONTRARIO de la intención porque
// el check anti-overreach «no » se aplicaba al contenido crudo. Fix:
// despoje del «que» subordinado ANTES de los checks (pelada → guía
// honesta SIN acción; «no …» → menú honesto). Guards: «quedarme» (sin
// espacio tras «que») y «qué» (con tilde) NUNCA se despojan.
class AssistantEngineRecuerdameQueStripTest {

    private fun ask(q: String) = AssistantEngine.answer(q, emptyList(), emptyList(), emptyList())

    // ---------- capturas: payload SIN el «que» subordinado ----------

    @Test fun recuerdameQueTengoQue_despojaQue() {
        val answer = ask("recuérdame que tengo que llamar al banco")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("tengo que llamar al banco", answer.actionPayload)
    }

    @Test fun recuerdameQuePague_despojaQue() {
        val answer = ask("recuérdame que pague la luz")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("pague la luz", answer.actionPayload)
    }

    @Test fun recuerdameQueSinTilde_despojaQue() {
        val answer = ask("recuerdame que llame a ana")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("llame a ana", answer.actionPayload)
    }

    @Test fun recuerdameDosPuntosQue_despojaQue() {
        val answer = ask("recuérdame: que comprar pan")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("comprar pan", answer.actionPayload)
    }

    // ---------- pelada-con-«que»: NUNCA tarea basura «que» ----------

    @Test fun recuerdameQuePelada_guiaHonestaSinAccion() {
        val answer = ask("recuérdame que")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.text.contains("recuerde"))
    }

    // ---------- negación tras «que»: NUNCA capturar lo contrario ----------

    @Test fun recuerdameQueNoLlame_menuHonesto() {
        val answer = ask("recuérdame que no llame a ana")
        assertEquals(AssistantAction.NONE, answer.action)
    }

    // ---------- guards: NUNCA despojar de más ----------

    @Test fun recuerdameQuedarme_payloadIntacto() {
        val answer = ask("recuérdame quedarme en casa")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("quedarme en casa", answer.actionPayload)
    }

    @Test fun recuerdameQueConTilde_payloadIntacto() {
        val answer = ask("recuérdame qué día es mañana")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("qué día es mañana", answer.actionPayload)
    }

    // ---------- regresiones hermanas (byte-idénticas) ----------

    @Test fun recuerdameSimple_regresionIntacta() {
        val answer = ask("recuérdame llamar al banco mañana")
        assertEquals(AssistantAction.CREATE_TASK, answer.action)
        assertEquals("llamar al banco mañana", answer.actionPayload)
    }

    @Test fun recuerdameNo_regresionIntacta() {
        val answer = ask("recuérdame no llamar al banco")
        assertEquals(AssistantAction.NONE, answer.action)
    }
}
