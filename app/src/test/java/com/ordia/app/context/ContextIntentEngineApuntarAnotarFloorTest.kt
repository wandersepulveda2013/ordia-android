package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.714 (P1 olvido silencioso en captura pasiva — SEGUNDA clase de verbos
 * cotidianos de gestión, forma 4/14: "apuntar/anotar <nota>" descubierto por
 * sonda `tools/probe/ManagementVerbDiscoveryProbe.kt` c.711; UNA forma por
 * ciclo, doctrina anti-overreach): "apuntar la dirección del médico"/
 * "anotar el número del banco mañana" se DESCARTABAN (analyze → NULL) — el
 * usuario se auto-anota información útil desde una notificación y Ordía lo
 * olvidaba (P1). Las keywords de NOTE ("apuntar"/"anotar") dan base ~0.12–0.22
 * (< [MINIMUM_CONFIDENCE]) sin piso. Fix: piso de NOTE + plantilla de título
 * "apuntar X"→"Apuntar X" (lección c.616: el match arranca en el verbo, igual
 * que c.691…c.713). Kind decidido en este ciclo: NOTE, en deliberación contra
 * TASK — "apuntar/anotar" es el verbo canónico de la NOTA útil (downstream:
 * [ConfirmExternalSuggestionUseCase] lo convierte en entidad NOTE real, no en
 * tarea); marcar el teléfono/dirección/número no es una acción ejecutable.
 * Anti-overreach: `\s+\w` exige objeto, `(?<!no )` bloquea la negada, duda
 * (c.649) y condición (c.650) descartan post-piso, suelto "apuntar"/"anotar"
 * no casa; el envolvente c.652 ("recuérdame…") sigue gobernando en TASK vía
 * guard [imperativeIsWrapped] registrado en [WRAPPABLE_PATTERNS]. Determinista
 * (regex), sin random, sin IA fingida.
 */
class ContextIntentEngineApuntarAnotarFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: "apuntar/anotar <objeto>" es una nota clara ---

    @Test
    fun apuntarLaDireccionDelMedico_capturesNote() {
        val intent = analyze("apuntar la dirección del médico")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.NOTE, intent!!.kind)
        assertEquals("Apuntar la dirección del médico", intent.title)
    }

    @Test
    fun anotarElNumeroDelBancoManana_capturesNoteWithDueAt() {
        val intent = analyze("anotar el número del banco mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.NOTE, intent!!.kind)
        assertEquals("Anotar el número del banco", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun apuntarElCumpleanos_capturesNoteWithoutDueAt() {
        val intent = analyze("apuntar el cumpleaños de ana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.NOTE, intent!!.kind)
        assertEquals("Apuntar el cumpleaños de ana", intent.title)
    }

    @Test
    fun anotarTrasPrefijoDeAcuse_capturesNote() {
        val intent = analyze("sí, anotar el número del banco mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.NOTE, intent!!.kind)
        assertEquals("Anotar el número del banco", intent.title)
    }

    @Test
    fun apuntarTrasPrefijoTemporal_capturesNoteWithDueAt() {
        val intent = analyze("mañana apuntar la dirección")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.NOTE, intent!!.kind)
        assertEquals("Apuntar la dirección", intent.title)
        assertNotNull(intent.dueAt)
    }

    // --- Controles anti-overreach (deben permanecer NULL) ---

    @Test
    fun noApuntarLaDireccion_negatedStaysNull() {
        assertNull(analyze("no apuntar la dirección"))
    }

    @Test
    fun quizasAnotar_hedgeStaysNull() {
        assertNull(analyze("quizá anotar el número del banco"))
    }

    @Test
    fun siTengoTiempoAnotar_conditionalStaysNull() {
        assertNull(analyze("si tengo tiempo anotar el número"))
    }

    @Test
    fun apuntarSinObjeto_bareVerbStaysNull() {
        assertNull(analyze("apuntar"))
    }

    // --- Regresión: la envolvente c.613 ("recuérdame…") sigue gobernando ---

    @Test
    fun recuerdameApuntarLaDireccion_wrapperWinsTask() {
        val intent = analyze("recuérdame apuntar la dirección")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}
