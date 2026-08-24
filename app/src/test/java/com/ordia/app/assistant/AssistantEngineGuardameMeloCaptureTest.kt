package com.ordia.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// c.979: lateral BACKLOG ABIERTA (descubierta c.977 — la batería de la sonda
// de «escríbeme esto» midió también esta familia, 4/4 GAP):
//  (a) «guárdame/guardame esto: …» (enclítico de «guarda», simétrico del
//      «escríbeme esto» c.977) y (b) deíctico fundido «-melo»
//      («escríbemelo: …», «apúntemelo: …», «anótemelo: …» y las variantes
//      ortográficas «apúntamelo»/«anótamelo») caían al menú genérico incluso
//      con contenido.
// Sonda efímera `/tmp/probe979/GuardameMeloProbe.kt` (motor real vía
// tools/run_probe.sh, base c.978):
//  PRE — 13/13 GAP: 10 capturas + 3 peladas (menú genérico, action=NONE);
//        8/8 guards en NONE; 4/4 controles de captura existentes intactos.
// El fix EXIGE «esto/eso» + «:» (a) o «melo» + «:» (b): el enclítico desnudo
// («guárdame el archivo», «escríbemelo mañana», «apúntemelo en la lista»)
// NUNCA es captura (misma doctrina c.976/c.977).
class AssistantEngineGuardameMeloCaptureTest {

    private fun ask(q: String) = AssistantEngine.answer(q, emptyList(), emptyList(), emptyList())

    // ---------- (a) enclítico de «guarda» + «esto/eso» + «:» → CREATE_NOTE ----------

    @Test fun guardameEstoConDosPuntos_creaNota() {
        val answer = ask("guárdame esto: la cita es el martes")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("la cita es el martes", answer.actionPayload)
    }

    @Test fun guardameEstoSinTilde_creaNota() {
        val answer = ask("guardame esto: el codigo es 4321")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("el codigo es 4321", answer.actionPayload)
    }

    @Test fun guardameEsoConDosPuntos_creaNota() {
        val answer = ask("guárdame eso: llamar a Ana")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("llamar a Ana", answer.actionPayload)
    }

    // ---------- (b) deíctico fundido «-melo» + «:» → CREATE_NOTE ----------

    @Test fun escribemeloConDosPuntos_creaNota() {
        val answer = ask("escríbemelo: la clave es 1234")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("la clave es 1234", answer.actionPayload)
    }

    @Test fun escribemeloSinTilde_creaNota() {
        val answer = ask("escribemelo: pagar la luz")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("pagar la luz", answer.actionPayload)
    }

    @Test fun apuntemeloConDosPuntos_creaNota() {
        val answer = ask("apúntemelo: comprar leche")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("comprar leche", answer.actionPayload)
    }

    @Test fun apuntemeloSinTilde_creaNota() {
        val answer = ask("apuntemelo: renovar el dni")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("renovar el dni", answer.actionPayload)
    }

    @Test fun apuntameloOrtografico_creaNota() {
        val answer = ask("apúntamelo: la matricula es 9988")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("la matricula es 9988", answer.actionPayload)
    }

    @Test fun anotemeloConDosPuntos_creaNota() {
        val answer = ask("anótemelo: recoger el paquete")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("recoger el paquete", answer.actionPayload)
    }

    @Test fun anotameloOrtografico_creaNota() {
        val answer = ask("anótamelo: el medico dijo reposo")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("el medico dijo reposo", answer.actionPayload)
    }

    // ---------- peladas: guía honesta SIN acción (NUNCA nota vacía/basura) ----------

    @Test fun guardameEstoPelada_pideContenidoSinAccion() {
        val answer = ask("guárdame esto")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
        assertTrue(answer.text.startsWith("¿Qué quieres anotar?"))
    }

    @Test fun guardameEsoPelada_pideContenidoSinAccion() {
        val answer = ask("guardame eso")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
        assertTrue(answer.text.startsWith("¿Qué quieres anotar?"))
    }

    @Test fun escribemeloSoloConDosPuntos_pideContenidoSinAccion() {
        val answer = ask("escríbemelo:")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
        assertTrue(answer.text.startsWith("¿Qué quieres anotar?"))
    }

    // ---------- guards: el enclítico desnudo NUNCA es captura ----------

    @Test fun guardameElArchivo_noEsCaptura() {
        val answer = ask("guárdame el archivo")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }

    @Test fun guardameLaCarpeta_noEsCaptura() {
        val answer = ask("guardame la carpeta")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }

    @Test fun guardameEstoEnElArchivo_noEsCaptura() {
        val answer = ask("guárdame esto en el archivo")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }

    @Test fun escribemeloManana_noEsCaptura() {
        val answer = ask("escríbemelo mañana")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }

    @Test fun escribemeloASecas_noEsCaptura() {
        val answer = ask("escríbemelo")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }

    @Test fun apuntemeloEnLaLista_noEsCaptura() {
        val answer = ask("apúntemelo en la lista")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }

    @Test fun apuntameloBien_noEsCaptura() {
        val answer = ask("apúntamelo bien")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }

    @Test fun quieroQueMeLoEscribas_noEsCaptura() {
        val answer = ask("quiero que me lo escribas")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }
}
