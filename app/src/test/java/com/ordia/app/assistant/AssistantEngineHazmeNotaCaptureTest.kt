package com.ordia.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// c.988: familia enclítica «hazme una nota» (BACKLOG fila c.987 — lateral
// medida FUERA de la unidad «-lo»). Sonda PRE efímera
// `/tmp/probe988/HazmeNotaProbe.kt` (motor real vía tools/run_probe.sh, sobre
// 8c8e091): 4/4 capturas GAP al menú genérico (action=NONE — mentira por
// omisión: el hermano no-enclítico «haz una nota» captura desde c.974), 2/2
// peladas al menú en vez de la guía honesta; 3/3 guards («hazme un favor»,
// «hazme la comida», «hazme un café») en NONE correctos desde PRE (el verbo
// exige la palabra «nota»); 2/2 controles hermanos («haz una nota», «crea
// una nota») intactos. Fix esperado mínimo (2 puntos, MISMAS regex):
// TAKE_NOTE_PREFIX y WRITE_NOTE_WITH_CONTENT admiten «hazme» junto a «haz»
// (misma doctrina de enclíticos c.972…c.987).
class AssistantEngineHazmeNotaCaptureTest {

    private fun ask(q: String) = AssistantEngine.answer(q, emptyList(), emptyList(), emptyList())

    // ---------- capturas con contenido (RED en la sonda sobre 8c8e091) ----------

    @Test fun hazmeUnaNotaConDosPuntos_creaNota() {
        val answer = ask("hazme una nota: comprar leche")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("comprar leche", answer.actionPayload)
    }

    @Test fun hazmeUnaNotaPagarLuz_creaNota() {
        val answer = ask("hazme una nota: pagar la luz mañana")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("pagar la luz mañana", answer.actionPayload)
    }

    @Test fun hazmeUnaNotaDeLaReunion_creaNota() {
        val answer = ask("hazme una nota de la reunión")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("la reunión", answer.actionPayload)
    }

    @Test fun hazmeUnaNotaDeFisica_creaNota() {
        val answer = ask("hazme una nota de física")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("física", answer.actionPayload)
    }

    // ---------- peladas: guía honesta SIN acción (NUNCA nota vacía, doctrina c.969) ----------

    @Test fun hazmeUnaNotaPelada_pideContenidoSinCrearNotaVacia() {
        val answer = ask("hazme una nota")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
        assertTrue(answer.text.contains("guardar como nota"))
    }

    @Test fun hazmeUnaNotaDePelada_pideContenido() {
        val answer = ask("hazme una nota de")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
        assertTrue(answer.text.contains("guardar como nota"))
    }

    // ---------- guards (verdes desde RED: «hazme» sin la palabra «nota» NUNCA captura) ----------

    @Test fun hazmeUnFavor_noCaptura() {
        val answer = ask("hazme un favor")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
    }

    @Test fun hazmeLaComida_noCaptura() {
        val answer = ask("hazme la comida")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
    }

    @Test fun hazmeUnCafe_noCaptura() {
        val answer = ask("hazme un café")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
    }

    // ---------- controles hermanos (verdes desde RED: familia no-enclítica intacta) ----------

    @Test fun hazUnaNota_sigueCreando() {
        val answer = ask("haz una nota: comprar pan")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("comprar pan", answer.actionPayload)
    }

    @Test fun creaUnaNota_sigueCreando() {
        val answer = ask("crea una nota: revisar el informe")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("revisar el informe", answer.actionPayload)
    }
}
