package com.ordia.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// c.977: lateral documentada FUERA por c.976 («próxima prioridad»: «escríbeme
// esto…», enclítico + deíctico sin «nota»). Sonda efímera
// `/tmp/probe977/EscribemeEstoProbe.kt` (motor real vía tools/run_probe.sh):
//  PRE — 5/5 GAP: «escríbeme esto: la wifi es 1234», «escribeme esto: …»,
//        «escríbeme eso: …», «Escríbeme esto: …» y la pelada «escríbeme esto»
//        caían al menú genérico (action=NONE); 5/5 guards («escríbeme un
//        poema», «escríbeme un correo a juan», «escríbeme», «quiero que me
//        escribas esto», «escríbeme mañana») en NONE; 4/4 controles de
//        captura existentes (c.976/c.972/c.974) intactos.
// El fix EXIGE «esto/eso» como sujeto inequívoco de dictado (misma doctrina
// c.976): el enclítico desnudo («escríbeme un poema») NUNCA es captura.
class AssistantEngineEscribemeEstoCaptureTest {

    private fun ask(q: String) = AssistantEngine.answer(q, emptyList(), emptyList(), emptyList())

    // ---------- capturas: enclítico + «esto/eso» + «:» + contenido → CREATE_NOTE ----------

    @Test fun escribemeEstoConDosPuntos_creaNota() {
        val answer = ask("escríbeme esto: la wifi es 1234")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("la wifi es 1234", answer.actionPayload)
    }

    @Test fun escribemeEstoSinTilde_creaNota() {
        val answer = ask("escribeme esto: el codigo es 4321")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("el codigo es 4321", answer.actionPayload)
    }

    @Test fun escribemeEsoConDosPuntos_creaNota() {
        val answer = ask("escríbeme eso: pagar el alquiler el dia 5")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("pagar el alquiler el dia 5", answer.actionPayload)
    }

    @Test fun escribemeEstoMayusculas_creaNota() {
        val answer = ask("Escríbeme esto: llamar a Ana")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("llamar a Ana", answer.actionPayload)
    }

    // ---------- peladas: guía honesta SIN acción (NUNCA nota vacía/basura) ----------

    @Test fun escribemeEstoPelada_pideContenidoSinAccion() {
        val answer = ask("escríbeme esto")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
        assertTrue(answer.text.startsWith("¿Qué quieres anotar?"))
    }

    @Test fun escribemeEsoPelada_pideContenidoSinAccion() {
        val answer = ask("escribeme eso")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
        assertTrue(answer.text.startsWith("¿Qué quieres anotar?"))
    }

    // ---------- guards: el enclítico desnudo NUNCA es captura ----------

    @Test fun escribemeUnPoema_noEsCaptura() {
        val answer = ask("escríbeme un poema")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }

    @Test fun escribemeUnCorreo_noEsCaptura() {
        val answer = ask("escríbeme un correo a juan")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }

    @Test fun escribemePelada_noEsCaptura() {
        val answer = ask("escríbeme")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }

    @Test fun quieroQueMeEscribasEsto_noEsCaptura() {
        val answer = ask("quiero que me escribas esto")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }

    @Test fun escribemeManana_noEsCaptura() {
        val answer = ask("escríbeme mañana")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }

    // c.978: delta conservado tras COLISIÓN c.977/c.977 convergente TOTAL (mi
    // pin que sus 11 tests no ejercen; precedente c.970/c.973 duplicados).
    @Test fun escribemeLaCarta_noEsCaptura() {
        val answer = ask("escríbeme la carta")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }
}
