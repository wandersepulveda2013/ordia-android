package com.ordia.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// c.969: captura y recuperación de «la nota de X» / «tomar nota» (descubrimiento
// de la sonda de paridad c.967 — sonda PRE c.969: 8/8 GAP al menú genérico).
// «la nota de la reunión» es una nota CONCRETA por contenido: debe rutear a la
// búsqueda con la consulta íntegra (hermana de «notas de física», c.794), no al
// bundle «las notas». «tomar nota» es la forma cotidiana de pedir captura:
// con contenido («: X» / «de X») crea la nota; pelada, guía honesta (NUNCA nota
// vacía — la UI crea «Nota sin título» si CREATE_NOTE llega sin payload).
class AssistantEngineTomarNotaCaptureTest {

    private fun ask(q: String) = AssistantEngine.answer(q, emptyList(), emptyList(), emptyList())

    // ---------- capturas (RED en la sonda PRE) ----------

    @Test fun laNotaDeLaReunion_ruteaABusquedaConConsultaIntegra() {
        val answer = ask("la nota de la reunión")
        assertEquals(AssistantAction.OPEN_SEARCH, answer.action)
        assertEquals("la nota de la reunión", answer.actionPayload)
    }

    @Test fun laNotaDelMedico_ruteaABusqueda() {
        val answer = ask("la nota del médico")
        assertEquals(AssistantAction.OPEN_SEARCH, answer.action)
        assertEquals("la nota del médico", answer.actionPayload)
    }

    @Test fun tomarNotaPelada_pideContenidoSinCrearNotaVacia() {
        val answer = ask("tomar nota")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
        assertTrue(answer.text.contains("guardar como nota"))
    }

    @Test fun tomaNotaPelada_pideContenido() {
        val answer = ask("toma nota")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.text.contains("guardar como nota"))
    }

    @Test fun tomarNotaConDosPuntos_creaNotaConContenido() {
        val answer = ask("tomar nota: llamar al banco")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("llamar al banco", answer.actionPayload)
    }

    @Test fun tomarNotaConDe_creaNotaConContenido() {
        val answer = ask("tomar nota de la reunión")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("la reunión", answer.actionPayload)
    }

    @Test fun tomaNotaDeConContenido_creaNota() {
        val answer = ask("toma nota de comprar leche")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("comprar leche", answer.actionPayload)
    }

    @Test fun tomarNotaDeSinContenido_pideContenido() {
        val answer = ask("tomar nota de")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
        assertTrue(answer.text.contains("guardar como nota"))
    }

    // ---------- guards (byte-idénticos pre/post) ----------

    @Test fun laNotaPelada_sigueListando() {
        val answer = ask("la nota")
        assertEquals(AssistantAction.OPEN_SEARCH, answer.action)
        assertEquals("notas", answer.actionPayload)
    }

    @Test fun laNotaDeSinCalificador_noRutea() {
        val answer = ask("la nota de")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
        assertFalse(answer.text.contains("guardar como nota"))
    }

    @Test fun guardarComoNota_intacta() {
        val answer = ask("guardar como nota: comprar leche")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("comprar leche", answer.actionPayload)
    }

    @Test fun notasDeFisica_contenido_intacta() {
        val answer = ask("notas de física")
        assertEquals(AssistantAction.OPEN_SEARCH, answer.action)
        assertEquals("notas de física", answer.actionPayload)
    }
}
