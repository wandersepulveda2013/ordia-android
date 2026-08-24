package com.ordia.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// c.974: auditoría de captura del asistente (próxima prioridad registrada en
// c.972). Sonda PRE efímera `/tmp/probe973/EscribeCreaNotaProbe.kt` (motor real
// vía tools/run_probe.sh, sobre 1726097): las formas cotidianas «escribe una
// nota…» / «crea una nota…» / «haz una nota…» (hermanas de «tomar nota» c.969,
// «apunta/anota» c.971, «apúntame/anótame» c.972) caían al menú genérico —
// 8/8 con contenido → action=NONE (mentira por omisión de captura) y 3/3
// peladas → menú en vez de la guía honesta. Guards 7/7 correctos (listing,
// «la nota de la reunión», capturas previas intactas).
// Frontera deliberada: el verbo exige la palabra «nota» — «escribe esto: X»
// (sin «nota») queda FUERA como lateral documentada; «quiero escribir una
// nota» no casa por el ancla de inicio de frase.
class AssistantEngineEscribeCreaNotaCaptureTest {

    private fun ask(q: String) = AssistantEngine.answer(q, emptyList(), emptyList(), emptyList())

    // ---------- capturas con contenido (RED en la sonda sobre 1726097) ----------

    @Test fun escribeUnaNotaConDosPuntos_creaNota() {
        val answer = ask("escribe una nota: comprar leche")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("comprar leche", answer.actionPayload)
    }

    @Test fun escribemeUnaNota_creaNota() {
        val answer = ask("escríbeme una nota: pagar la luz")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("pagar la luz", answer.actionPayload)
    }

    @Test fun escribemeSinTilde_creaNota() {
        val answer = ask("escribeme una nota: llamar al banco")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("llamar al banco", answer.actionPayload)
    }

    @Test fun creaUnaNota_creaNota() {
        val answer = ask("crea una nota: la reunión es el lunes")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("la reunión es el lunes", answer.actionPayload)
    }

    @Test fun crearUnaNota_creaNota() {
        val answer = ask("crear una nota: revisar el informe")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("revisar el informe", answer.actionPayload)
    }

    @Test fun hazUnaNota_creaNota() {
        val answer = ask("haz una nota: comprar pan")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("comprar pan", answer.actionPayload)
    }

    @Test fun escribeDirectoSinDosPuntos_creaNota() {
        val answer = ask("escribe una nota llamar al banco")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("llamar al banco", answer.actionPayload)
    }

    @Test fun creaNotaSinArticulo_creaNota() {
        val answer = ask("crea nota: comprar leche")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("comprar leche", answer.actionPayload)
    }

    @Test fun escribeConectorDe_creaNota() {
        val answer = ask("escribe una nota de la reunión")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("la reunión", answer.actionPayload)
    }

    // ---------- peladas: guía honesta, NUNCA nota vacía/basura ----------

    @Test fun escribeUnaNotaPelada_guiaHonestaSinAccion() {
        val answer = ask("escribe una nota")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
        assertTrue(answer.text.contains("tomar nota"))
    }

    @Test fun creaUnaNotaPelada_guiaHonestaSinAccion() {
        val answer = ask("crea una nota")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
        assertTrue(answer.text.contains("tomar nota"))
    }

    @Test fun hazUnaNotaPelada_guiaHonestaSinAccion() {
        val answer = ask("haz una nota")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
        assertTrue(answer.text.contains("tomar nota"))
    }

    @Test fun escribeEstoPelada_nuncaNotaBasuraEsto() {
        val answer = ask("escribe una nota esto")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
    }

    // ---------- guards (verdes desde RED: no deben moverse) ----------

    @Test fun quieroEscribirUnaNota_noEsInicioDeFrase_noSecuestra() {
        val answer = ask("quiero escribir una nota")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
    }

    @Test fun laNotaDeLaReunion_sigueRuteandoABusqueda() {
        val answer = ask("la nota de la reunión")
        assertEquals(AssistantAction.OPEN_SEARCH, answer.action)
        assertEquals("la nota de la reunión", answer.actionPayload)
    }

    @Test fun notasListing_intacto() {
        val answer = ask("notas")
        assertEquals(AssistantAction.OPEN_SEARCH, answer.action)
        assertEquals("notas", answer.actionPayload)
    }

    @Test fun tomarNota_regresionC969() {
        val answer = ask("tomar nota: llamar al banco")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("llamar al banco", answer.actionPayload)
    }

    @Test fun apuntame_regresionC972() {
        val answer = ask("apúntame esto: comprar leche")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("comprar leche", answer.actionPayload)
    }

    @Test fun guardarComoNota_regresionClasica() {
        val answer = ask("guardar como nota: pagar la luz")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("pagar la luz", answer.actionPayload)
    }
}
