package com.ordia.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// c.972: delta tras la colisión de lateral con el hermano (su c.971 publicó la
// captura «apunta…»/«anota…» — producción resuelta a SU versión, byte-idéntica,
// precedente c.969/c.970). Sonda POST-colisión `/tmp/probe971/DeltaProbe.kt`
// sobre 871a856 midió dos huecos que sus 13 tests no ejercen:
//  (a) enclíticos «apúntame…»/«anótame…» (± sin tilde, escritura móvil real) →
//      menú genérico (5/5 GAP) — la forma cotidiana «apúntame esto: …»;
//  (b) pelada con «esto» («apunta esto»/«anota esto»/«apuntar esto») creaba una
//      nota BASURA titulada «esto» (3/3) — viola NUNCA nota vacía/basura: el
//      «esto» es muletilla deíctica, no contenido.
// «apuntarme»/«anotarme» (infinitivo+«me», bivalente con apuntarse/anotarse)
// quedan FUERA a propósito. «apunta a las nueve» se pinnea con la semántica
// publicada del hermano (captura) — divergencia de criterio documentada,
// reversible si aparece evidencia de secuestro.
class AssistantEngineApuntameCaptureTest {

    private fun ask(q: String) = AssistantEngine.answer(q, emptyList(), emptyList(), emptyList())

    // ---------- (a) enclíticos (RED en la sonda sobre 871a856) ----------

    @Test fun apuntameEstoConDosPuntos_creaNota() {
        val answer = ask("apúntame esto: comprar leche")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("comprar leche", answer.actionPayload)
    }

    @Test fun apuntameConDosPuntos_creaNota() {
        val answer = ask("apúntame: comprar leche")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("comprar leche", answer.actionPayload)
    }

    @Test fun apuntameDirecto_creaNota() {
        val answer = ask("apúntame comprar leche")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("comprar leche", answer.actionPayload)
    }

    @Test fun anotameEstoConDosPuntos_creaNota() {
        val answer = ask("anótame esto: pagar la luz")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("pagar la luz", answer.actionPayload)
    }

    @Test fun anotameDirecto_creaNota() {
        val answer = ask("anótame pagar la luz")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("pagar la luz", answer.actionPayload)
    }

    @Test fun apuntameSinTilde_creaNota() {
        val answer = ask("apuntame esto: llamar al banco")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("llamar al banco", answer.actionPayload)
    }

    // ---------- (b) pelada con «esto»: NUNCA nota basura «esto» (RED) ----------

    @Test fun apuntaEstoPelada_pideContenidoSinCrearNotaBasura() {
        val answer = ask("apunta esto")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
    }

    @Test fun anotaEstoPelada_pideContenido() {
        val answer = ask("anota esto")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
    }

    @Test fun apuntarEstoPelada_pideContenido() {
        val answer = ask("apuntar esto")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
    }

    // ---------- guards y pines (byte-idénticos pre/post delta) ----------

    @Test fun apuntaALasNueve_pinSemanticaPublicadaC971() {
        // Semántica PUBLICADA por el hermano en c.971: el conector «a» no se
        // trata como bivalente y se captura. Divergencia de criterio (este lado
        // la medía bivalente-conservadora en su versión descartada) documentada
        // y pinneada tal cual — reversible si hay evidencia de secuestro.
        val answer = ask("apunta a las nueve")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("a las nueve", answer.actionPayload)
    }

    @Test fun apuntarmeAYoga_bivalenteApuntarseNoSecuestra() {
        val answer = ask("apuntarme a yoga")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
    }

    @Test fun anotarmeEnElCurso_bivalenteAnotarseNoSecuestra() {
        val answer = ask("anotarme en el curso")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
    }

    @Test fun apuntado_participioNoSecuestra() {
        val answer = ask("lo tengo apuntado")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
    }

    @Test fun anotePasado_noSecuestra() {
        val answer = ask("ya lo anoté")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
    }

    @Test fun laAnotacion_sustantivoNoSecuestra() {
        val answer = ask("la anotación de ayer")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
    }

    // ---------- regresiones (hermano c.971 / c.969 intactas) ----------

    @Test fun apuntaConDosPuntos_sigueIntacto() {
        val answer = ask("apunta: llamar al banco")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("llamar al banco", answer.actionPayload)
    }

    @Test fun apuntaEstoConDosPuntos_sigueIntacto() {
        val answer = ask("apunta esto: llamar al banco")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("llamar al banco", answer.actionPayload)
    }

    @Test fun apuntarInfinitivo_sigueIntacto() {
        val answer = ask("apuntar: llamar al banco")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("llamar al banco", answer.actionPayload)
    }

    @Test fun tomarNota_sigueIntacto() {
        val answer = ask("tomar nota: llamar al banco")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("llamar al banco", answer.actionPayload)
    }

    @Test fun anotaPelada_siguePidiendoContenido() {
        val answer = ask("anota")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
    }
}
