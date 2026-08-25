package com.ordia.app.assistant

// c.1085: alternativas de verbo antes de «una nota» en la captura de notas
// («guarda/toma/haceme/déjame una nota: …»). Audit de routing PRE con
// sonda efímera /tmp/probe1085/Probe.kt (motor real): 10 GAPs medidos
// sobre captura de notas con «una nota» (4 variantes de verbo + pelada +
// mayúscula + sin tilde + conector «de»), guards byte-idénticos de cero
// capturas indebidas. Fix mínimo (2 puntos, MISMAS regex que ya gobiernan
// «escribe/crea/haz/hazme una nota» c.974): el conjunto de verbos de
// TAKE_NOTE_PREFIX/WRITE_NOTE_WITH_CONTENT admite también guarda[r],
// toma[r], haceme (voseo) y d[eé]jame. Anti-overreach: la palabra
// «nota(s)» sigue siendo obligatoria, así «guarda el recuerdo de la
// infancia», «toma la pastilla», «déjame el paquete en la puerta» y
// «haceme favor» NUNCA entran; pelada («guarda una nota») → guía honesta
// SIN acción (doctrina c.976/c.977, NUNCA nota vacía/basura).
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantEngineNotaVerboAlternativoCaptureTest {

    private fun noteRequest(q: String) = AssistantEngine.answer(
        q, emptyList(), emptyList(), emptyList(), 0L
    )

    // — Capturas con verbos alternativos (PRE: menú genérico) —
    @Test
    fun guardaUnaNotaCaptura() {
        val ans = noteRequest("guarda una nota: la wifi es 1234")
        assertEquals(AssistantAction.CREATE_NOTE, ans.action)
        assertEquals("la wifi es 1234", ans.actionPayload)
    }

    @Test
    fun guardarUnaNotaCaptura() {
        assertEquals(AssistantAction.CREATE_NOTE, noteRequest("guardar una nota: el codigo es 4321").action)
    }

    @Test
    fun tomaUnaNotaCaptura() {
        val ans = noteRequest("toma una nota: el cumple es el lunes")
        assertEquals(AssistantAction.CREATE_NOTE, ans.action)
        assertEquals("el cumple es el lunes", ans.actionPayload)
    }

    @Test
    fun tomarUnaNotaCaptura() {
        assertEquals(AssistantAction.CREATE_NOTE, noteRequest("tomar una nota: llamar al banco").action)
    }

    @Test
    fun hacemeUnaNotaCaptura() {
        val ans = noteRequest("haceme una nota: la cita es el martes")
        assertEquals(AssistantAction.CREATE_NOTE, ans.action)
        assertEquals("la cita es el martes", ans.actionPayload)
    }

    @Test
    fun dejameUnaNotaCaptura() {
        val ans = noteRequest("déjame una nota: la llave está en la maceta")
        assertEquals(AssistantAction.CREATE_NOTE, ans.action)
        assertEquals("la llave está en la maceta", ans.actionPayload)
    }

    @Test
    fun dejameUnaNotaSinTildeCaptura() {
        assertEquals(AssistantAction.CREATE_NOTE, noteRequest("dejame una nota: la llave esta en la maceta").action)
    }

    @Test
    fun mayusculaCaptured() {
        assertEquals(AssistantAction.CREATE_NOTE, noteRequest("Guarda una nota: el codigo es 4321").action)
    }

    @Test
    fun conectorDeCaptura() {
        val ans = noteRequest("toma una nota de averiguar precios")
        assertEquals(AssistantAction.CREATE_NOTE, ans.action)
        assertEquals("averiguar precios", ans.actionPayload)
    }

    // — Pelada → guía honesta SIN acción (doctrina c.976/c.977) —
    @Test
    fun peladaGuardaUnaNotaEsGuia() {
        val ans = noteRequest("guarda una nota")
        assertNotEquals(AssistantAction.CREATE_NOTE, ans.action)
        assertTrue(ans.text.contains("anotar", ignoreCase = true))
    }

    // — Guards anti-overreach: la palabra «nota» sigue obligatoria —
    @Test
    fun guardaElRecuerdoNoEsCaptura() {
        assertNotEquals(AssistantAction.CREATE_NOTE, noteRequest("guarda el recuerdo de la infancia").action)
    }

    @Test
    fun tomaLaPastillaNoEsCaptura() {
        assertNotEquals(AssistantAction.CREATE_NOTE, noteRequest("toma la pastilla").action)
    }

    @Test
    fun dejameElPaqueteNuncaCaptura() {
        assertNotEquals(AssistantAction.CREATE_NOTE, noteRequest("déjame el paquete en la puerta").action)
    }

    @Test
    fun hacemeFavorNuncaCaptura() {
        assertNotEquals(AssistantAction.CREATE_NOTE, noteRequest("haceme favor").action)
    }

    // — Pins de regresión de la familia «una nota» existente —
    @Test
    fun regresionEscribeUnaNota() {
        val ans = noteRequest("escribe una nota: el codigo es 1234")
        assertEquals(AssistantAction.CREATE_NOTE, ans.action)
        assertEquals("el codigo es 1234", ans.actionPayload)
    }

    @Test
    fun regresionTomaNota() {
        assertEquals(AssistantAction.CREATE_NOTE, noteRequest("toma nota: llamar al banco").action)
    }

    @Test
    fun regresionHazmeUnaNota() {
        assertEquals(AssistantAction.CREATE_NOTE, noteRequest("hazme una nota: llamar al banco").action)
    }
}
