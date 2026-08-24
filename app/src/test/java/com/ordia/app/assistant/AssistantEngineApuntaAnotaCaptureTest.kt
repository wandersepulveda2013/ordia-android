package com.ordia.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// c.971: captura «apunta …» / «anota …» (imperativo e infinitivo cotidianos,
// hermanos de «tomar nota» c.969; auditoría pedida por la «próxima prioridad»
// de c.969 — sonda PRE c.971: 9/9 GAP al menú genérico). Con contenido («: X»,
// «esto: X» o directo «apunta llamar al banco») crea la nota; pelada, guía
// honesta (NUNCA nota vacía — la UI crea «Nota sin título» si CREATE_NOTE
// llega sin payload). El prefijo exige inicio de frase + frontera de palabra:
// «apuntarse», «apuntarme», «anotaciones» y «quiero apuntar» no se secuestran.
class AssistantEngineApuntaAnotaCaptureTest {

    private fun ask(q: String) = AssistantEngine.answer(q, emptyList(), emptyList(), emptyList())

    // ---------- capturas (RED en la sonda PRE) ----------

    @Test fun apuntaConDosPuntos_creaNotaConContenido() {
        val answer = ask("apunta: llamar al banco")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("llamar al banco", answer.actionPayload)
    }

    @Test fun apuntaDirecto_creaNotaConContenido() {
        val answer = ask("apunta llamar al banco")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("llamar al banco", answer.actionPayload)
    }

    @Test fun apuntaEstoConDosPuntos_creaNotaSinElEsto() {
        val answer = ask("apunta esto: la reunión es el lunes")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("la reunión es el lunes", answer.actionPayload)
    }

    @Test fun anotaConDosPuntos_creaNotaConContenido() {
        val answer = ask("anota: comprar leche")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("comprar leche", answer.actionPayload)
    }

    @Test fun anotaDirecto_creaNotaConContenido() {
        val answer = ask("anota comprar leche")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("comprar leche", answer.actionPayload)
    }

    @Test fun apuntarInfinitivo_creaNotaConContenido() {
        val answer = ask("apuntar: revisar el informe")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("revisar el informe", answer.actionPayload)
    }

    @Test fun anotarInfinitivo_creaNotaConContenido() {
        val answer = ask("anotar: revisar el informe")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("revisar el informe", answer.actionPayload)
    }

    @Test fun apuntaPelada_pideContenidoSinCrearNotaVacia() {
        val answer = ask("apunta")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
        assertTrue(answer.text.contains("guardar como nota"))
    }

    @Test fun anotaPelada_pideContenido() {
        val answer = ask("anota")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
        assertTrue(answer.text.contains("guardar como nota"))
    }

    // ---------- guards (byte-idénticos pre/post) ----------

    @Test fun anotaciones_noSecuestra() {
        val answer = ask("anotaciones")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
    }

    @Test fun apuntarseAlGimnasio_noSecuestra() {
        val answer = ask("apuntarse al gimnasio")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
    }

    @Test fun quieroApuntar_noSecuestra() {
        val answer = ask("quiero apuntar")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
    }

    @Test fun apuntarmeAYoga_noSecuestra() {
        val answer = ask("apuntarme a yoga")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
    }
}
