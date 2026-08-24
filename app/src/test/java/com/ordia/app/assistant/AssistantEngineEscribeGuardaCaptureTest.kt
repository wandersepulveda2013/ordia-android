package com.ordia.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// c.976: BACKLOG fila abierta (descubrimiento c.972, sonda PRE de la unidad
// «apúntame»). Sonda efímera `/tmp/probe974/CaptureEscribeGuardaProbe.kt`
// (motor real vía tools/run_probe.sh; reconstrucción de la batería original
// /tmp/probe971/CaptureNextProbe.kt, perdida con el /tmp del entorno):
//  PRE — 3/3 GAP: «escribe esto: la wifi es 1234», «guarda esto: el codigo es
//        4321» y la pelada «guarda esto» caían al menú genérico (action=NONE);
//        5/5 guards («escribe un correo a juan», «escríbeme un poema»,
//        «guarda el archivo», «guarda los cambios», «escribe tu nombre en la
//        lista») en NONE; 4/4 controles de captura existentes intactos.
// El fix EXIGE «esto/eso» como sujeto inequívoco de dictado: el verbo desnudo
// («escribe un correo», «guarda el archivo») NUNCA es captura de nota.
class AssistantEngineEscribeGuardaCaptureTest {

    private fun ask(q: String) = AssistantEngine.answer(q, emptyList(), emptyList(), emptyList())

    // ---------- capturas: «esto/eso» + «:» + contenido → CREATE_NOTE ----------

    @Test fun escribeEstoConDosPuntos_creaNota() {
        val answer = ask("escribe esto: la wifi es 1234")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("la wifi es 1234", answer.actionPayload)
    }

    @Test fun guardaEstoConDosPuntos_creaNota() {
        val answer = ask("guarda esto: el codigo es 4321")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("el codigo es 4321", answer.actionPayload)
    }

    @Test fun escribeEsoConDosPuntos_creaNota() {
        val answer = ask("escribe eso: pagar el alquiler el dia 5")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("pagar el alquiler el dia 5", answer.actionPayload)
    }

    @Test fun guardaEsoConDosPuntos_creaNota() {
        val answer = ask("guarda eso: el pasaporte caduca en mayo")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("el pasaporte caduca en mayo", answer.actionPayload)
    }

    @Test fun escribeEstoMayusculas_creaNota() {
        val answer = ask("Escribe esto: llamar a Ana")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("llamar a Ana", answer.actionPayload)
    }

    // ---------- peladas: guía honesta SIN acción (NUNCA nota vacía/basura) ----------

    @Test fun guardaEstoPelada_pideContenidoSinAccion() {
        val answer = ask("guarda esto")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
        assertTrue(answer.text.startsWith("¿Qué quieres anotar?"))
    }

    @Test fun escribeEstoPelada_pideContenidoSinAccion() {
        val answer = ask("escribe esto")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
        assertTrue(answer.text.startsWith("¿Qué quieres anotar?"))
    }

    // ---------- guards: el verbo desnudo NUNCA es captura ----------

    @Test fun escribeUnCorreo_noEsCaptura() {
        val answer = ask("escribe un correo a juan")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }

    @Test fun escribemeUnPoema_noEsCaptura() {
        val answer = ask("escríbeme un poema")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }

    @Test fun guardaElArchivo_noEsCaptura() {
        val answer = ask("guarda el archivo")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }

    @Test fun guardaLosCambios_noEsCaptura() {
        val answer = ask("guarda los cambios")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }

    @Test fun escribeTuNombre_noEsCaptura() {
        val answer = ask("escribe tu nombre en la lista")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }
}
