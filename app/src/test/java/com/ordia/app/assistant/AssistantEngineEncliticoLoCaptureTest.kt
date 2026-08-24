package com.ordia.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// c.987: familia enclítica «-lo» de captura de nota — hermana simétrica de la
// «-melo» cerrada en c.984. [Re-numerado c.986→c.987 por COLISIÓN de cycle-ID
// con el hermano — él tomó c.986 para «recuérdame <contenido>» → CREATE_TASK
// (da7b86a, lateral DISJUNTA); integración NO-destructiva stash→pull --ff-only
// →pop, auto-merge limpio en regiones disjuntas. Precedente c.968→c.969.]
// Sonda efímera `/tmp/probe987/EncliticoLoCaptureProbe.kt`
// (motor real vía tools/run_probe.sh, base 173a74b):
//  PRE — 12/12 capturas GAP (8 tú + 4 usted → menú genérico, action=NONE);
//        2/2 peladas → menú en vez de la guía honesta; 5/5 guards en NONE;
//        2/2 controles «-melo» c.984 intactos.
// Formas: tú «escríbelo/apúntalo/anótalo/guárdalo: X»; usted «escríbalo/
// apúntelo/anótelo/guárdelo: X» (vocalismo [ae], misma doctrina c.984).
// Mismo guard de la familia: la rama EXIGE «:» o fin de frase, así
// «escríbelo mañana»/«guárdalo en el archivo» ni siquiera entran (quedan en
// el menú, no secuestran); pelada → guía honesta SIN acción (NUNCA nota vacía).
class AssistantEngineEncliticoLoCaptureTest {

    private fun ask(q: String) = AssistantEngine.answer(q, emptyList(), emptyList(), emptyList())

    // ---------- tú + «:» + contenido → CREATE_NOTE ----------

    @Test fun escribeloConDosPuntos_creaNota() {
        val answer = ask("escríbelo: la wifi es 1234")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("la wifi es 1234", answer.actionPayload)
    }

    @Test fun escribeloSinTilde_creaNota() {
        val answer = ask("escribelo: la wifi es 1234")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("la wifi es 1234", answer.actionPayload)
    }

    @Test fun apuntaloConDosPuntos_creaNota() {
        val answer = ask("apúntalo: llamar al banco")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("llamar al banco", answer.actionPayload)
    }

    @Test fun apuntaloSinTilde_creaNota() {
        val answer = ask("apuntalo: llamar al banco")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("llamar al banco", answer.actionPayload)
    }

    @Test fun anotaloConDosPuntos_creaNota() {
        val answer = ask("anótalo: pasar la ITV en marzo")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("pasar la ITV en marzo", answer.actionPayload)
    }

    @Test fun anotaloSinTilde_creaNota() {
        val answer = ask("anotalo: pasar la ITV en marzo")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("pasar la ITV en marzo", answer.actionPayload)
    }

    @Test fun guardaloConDosPuntos_creaNota() {
        val answer = ask("guárdalo: el código es 4321")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("el código es 4321", answer.actionPayload)
    }

    @Test fun guardaloSinTilde_creaNota() {
        val answer = ask("guardalo: el código es 4321")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("el código es 4321", answer.actionPayload)
    }

    // ---------- usted + «:» + contenido → CREATE_NOTE ----------

    @Test fun escribaloUsted_creaNota() {
        val answer = ask("escríbalo: la clave es 9999")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("la clave es 9999", answer.actionPayload)
    }

    @Test fun apunteloUsted_creaNota() {
        val answer = ask("apúntelo: llamar al dentista")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("llamar al dentista", answer.actionPayload)
    }

    @Test fun anoteloUsted_creaNota() {
        val answer = ask("anótelo: recoger el paquete")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("recoger el paquete", answer.actionPayload)
    }

    @Test fun guardeloUsted_creaNota() {
        val answer = ask("guárdelo: la puerta es la 3B")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("la puerta es la 3B", answer.actionPayload)
    }

    // ---------- peladas: guía honesta SIN acción (NUNCA nota vacía/basura) ----------

    @Test fun escribeloPelada_pideContenidoSinAccion() {
        val answer = ask("escríbelo:")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
        assertTrue(answer.text.startsWith("¿Qué quieres anotar?"))
    }

    @Test fun apuntaloPelada_pideContenidoSinAccion() {
        val answer = ask("apúntalo")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
        assertTrue(answer.text.startsWith("¿Qué quieres anotar?"))
    }

    @Test fun guardaloPelada_pideContenidoSinAccion() {
        val answer = ask("guárdalo")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
        assertTrue(answer.text.startsWith("¿Qué quieres anotar?"))
    }

    // ---------- guards: continuación sin «:» NUNCA entra en la rama ----------

    @Test fun escribeloManana_noCaptura() {
        val answer = ask("escríbelo mañana")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
    }

    @Test fun apuntaloEnLaLista_noCaptura() {
        val answer = ask("apúntalo en la lista")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
    }

    @Test fun guardaloEnElArchivo_noCaptura() {
        val answer = ask("guárdalo en el archivo")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
    }

    @Test fun escribeloBonito_noCaptura() {
        val answer = ask("escríbelo bonito")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
    }

    @Test fun quieroQueLoEscribas_noCaptura() {
        val answer = ask("quiero que lo escribas")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
    }

    // ---------- control hermano «-melo» c.984: intacto ----------

    @Test fun escribemeloControl_creaNota() {
        val answer = ask("escríbemelo: la wifi es 1234")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("la wifi es 1234", answer.actionPayload)
    }
}
