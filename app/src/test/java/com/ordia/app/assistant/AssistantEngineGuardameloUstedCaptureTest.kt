package com.ordia.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// c.984: lateral ABIERTA del backlog (descubierta c.981 — «laterales medidas
// FUERA» de la unidad «guárdame/-melo»). Sonda efímera
// `/tmp/probe984/GuardameloUstedProbe.kt` (motor real vía tools/run_probe.sh,
// base 5d325b0):
//  PRE — 6/6 GAP (todas las capturas → menú genérico, action=NONE); 4/4
//        peladas → menú en vez de la guía honesta; 6/6 guards en NONE;
//        4/4 controles de captura existentes (c.980) intactos.
// (a) «guárdamelo/guardamelo: X» — fusión «-melo» de «guarda» (tú), simétrica
//     de la rama c.980(b) que cubre escribe/apunta/anota pero NO guarda.
// (b) formas de usted: «guárdemelo/guardemelo: X» y «escríbamelo/escribamelo:
//     X» («apúntemelo»/«anótamelo» ya quedaron cubiertas por el [ae] de c.980).
// Mismo guard de la familia: la rama «-melo» EXIGE «:» o fin de frase, así
// «guárdamelo mañana»/«escríbamelo bonito» ni siquiera entran (quedan en el
// menú, no secuestran); pelada → guía honesta SIN acción (NUNCA nota vacía).
class AssistantEngineGuardameloUstedCaptureTest {

    private fun ask(q: String) = AssistantEngine.answer(q, emptyList(), emptyList(), emptyList())

    // ---------- (a) fusión «-melo» de «guarda» (tú) + «:» + contenido → CREATE_NOTE ----------

    @Test fun guardameloConDosPuntos_creaNota() {
        val answer = ask("guárdamelo: la wifi es 1234")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("la wifi es 1234", answer.actionPayload)
    }

    @Test fun guardameloSinTilde_creaNota() {
        val answer = ask("guardamelo: el codigo es 4321")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("el codigo es 4321", answer.actionPayload)
    }

    // ---------- (b) formas de usted + «:» + contenido → CREATE_NOTE ----------

    @Test fun guardemeloUsted_creaNota() {
        val answer = ask("guárdemelo: pagar el alquiler el dia 5")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("pagar el alquiler el dia 5", answer.actionPayload)
    }

    @Test fun guardemeloUstedSinTilde_creaNota() {
        val answer = ask("guardemelo: llamar a ana")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("llamar a ana", answer.actionPayload)
    }

    @Test fun escribameloUsted_creaNota() {
        val answer = ask("escríbamelo: el pasaporte caduca en mayo")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("el pasaporte caduca en mayo", answer.actionPayload)
    }

    @Test fun escribameloUstedSinTilde_creaNota() {
        val answer = ask("escribamelo: comprar leche")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("comprar leche", answer.actionPayload)
    }

    // ---------- peladas: guía honesta SIN acción (NUNCA nota vacía/basura) ----------

    @Test fun guardameloPelada_pideContenidoSinAccion() {
        val answer = ask("guárdamelo")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
        assertTrue(answer.text.startsWith("¿Qué quieres anotar?"))
    }

    @Test fun guardemeloPelada_pideContenidoSinAccion() {
        val answer = ask("guárdemelo")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
        assertTrue(answer.text.startsWith("¿Qué quieres anotar?"))
    }

    @Test fun escribameloPelada_pideContenidoSinAccion() {
        val answer = ask("escríbamelo")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
        assertTrue(answer.text.startsWith("¿Qué quieres anotar?"))
    }

    @Test fun guardameloSoloConDosPuntos_pideContenidoSinAccion() {
        val answer = ask("guárdamelo:")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
        assertTrue(answer.text.startsWith("¿Qué quieres anotar?"))
    }

    // ---------- guards: el desnudo con continuación / contenido real NUNCA es captura ----------

    @Test fun guardameloManana_noEsCaptura() {
        val answer = ask("guárdamelo mañana")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }

    @Test fun guardameloBien_noEsCaptura() {
        val answer = ask("guárdamelo bien")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }

    @Test fun guardemeloEnElArchivo_noEsCaptura() {
        val answer = ask("guardemelo en el archivo")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }

    @Test fun escribameloBonito_noEsCaptura() {
        val answer = ask("escríbamelo bonito")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }

    @Test fun quieroQueMeLoGuardes_noEsCaptura() {
        val answer = ask("quiero que me lo guardes")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }

    @Test fun guardameElArchivo_noEsCaptura() {
        val answer = ask("guárdame el archivo")
        assertTrue(answer.action != AssistantAction.CREATE_NOTE)
    }
}
