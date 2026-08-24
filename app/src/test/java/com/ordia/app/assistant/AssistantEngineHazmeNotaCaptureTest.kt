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
// c.989: COLISIÓN cycle-ID c.988/c.988 CONVERGENTE TOTAL con el hermano — él
// publicó la MISMA unidad (`4b65465`, producción funcionalmente idéntica:
// «haz|hazme» ≡ «haz(?:me)?»; su guard extra: conector pelado «de»).
// Integración NO-destructiva (doctrina duplicados c.980/c.985): producción
// propia descartada (`git checkout --` — jamás se pisó el remoto), base suya
// integrada `pull --ff-only` (8c8e091→0604ebb), re-numeración c.988→c.989.
// Delta conservado (UNIÓN 11+6=17): 6 pins que sus 11 no ejercen — forma
// directa sin «:»/«de», mayúsculas (?i), perífrasis (ancla ^) y los 3 del
// modismo «nota mental» (mi guard extra en `takeNoteCapture`: «mental» a
// secas NUNCA es nota — medido en mi sonda como nota BASURA preexistente
// c.974: CREATE_NOTE payload «mental»).
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

    // ---------- delta c.989 (UNIÓN tras la colisión c.988/c.988): pins que
    // los 11 del hermano no ejercen ----------

    @Test fun hazmeUnaNotaDirecta_creaNota() {
        val answer = ask("hazme una nota comprar leche")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("comprar leche", answer.actionPayload)
    }

    @Test fun hazmeUnaNotaMayusculas_creaNota() {
        val answer = ask("Hazme una nota: pagar la luz")
        assertEquals(AssistantAction.CREATE_NOTE, answer.action)
        assertEquals("pagar la luz", answer.actionPayload)
    }

    @Test fun perifrasis_noCaptura() {
        val answer = ask("quiero que me hagas una nota: comprar leche")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
    }

    // ---------- modismo «nota mental»: NUNCA nota basura (guard c.989) ----------

    @Test fun hazmeUnaNotaMental_noCreaNotaBasura() {
        val answer = ask("hazme una nota mental")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
    }

    @Test fun hazUnaNotaMental_noCreaNotaBasura() {
        val answer = ask("haz una nota mental")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
    }

    @Test fun escribeUnaNotaMental_noCreaNotaBasura() {
        val answer = ask("escribe una nota mental")
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.actionPayload.isEmpty())
    }
}
