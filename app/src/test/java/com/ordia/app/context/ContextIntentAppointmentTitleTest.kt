package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Títulos APPOINTMENT sin duplicación del sustantivo (c.654).
 *
 * Defecto descubierto por probe JVM fuente real (pendiente de c.653):
 * [ContextIntentEngine.extractTitle] anteponía siempre el prefijo "Cita:" al
 * resto capturado, así que si el propio resto empezaba en "cita" (con o sin
 * artículo) el título quedaba duplicado — "tengo cita con el dentista" →
 * "Cita: Cita con el dentista", "voy a la cita con el dentista" →
 * "Cita: La cita con el dentista", "cita con el dentista" →
 * "Cita: con el dentista" (perdía además el sustantivo). Overreach P2 (ruido
 * en avisos almacenados; misma lección c.653: preservar el texto del usuario).
 *
 * Solución: si el resto arranca en (una|la)?cita se descarta el artículo (si
 * lo hay) y se capitaliza el resto tal cual; sólo se añade el prefijo "Cita:"
 * cuando el resto NO menciona "cita" ("tengo dentista mañana" →
 * "Cita: Dentista mañana").
 *
 * Cobertura: 5 casos de auto-mención (RED pre-fix → GREEN) + 2 controles que
 * conservan el prefijo legítimo.
 */
class ContextIntentAppointmentTitleTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(
                ContextCaptureSource.NOTIFICATION,
                raw,
                1723939200000L
            )
        )

    // --- Auto-mención de "cita": el prefijo NO debe duplicarse ---

    @Test
    fun tengoCitaDentistaNoDuplicates() {
        val intent = analyze("tengo cita con el dentista")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
        assertEquals("Cita con el dentista", intent.title)
    }

    @Test
    fun voyACitaNoDuplicates() {
        val intent = analyze("voy a cita con el dentista")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
        assertEquals("Cita con el dentista", intent.title)
    }

    @Test
    fun tengoUnaCitaStripsArticle() {
        val intent = analyze("tengo una cita con el doctor")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
        assertEquals("Cita con el doctor", intent.title)
    }

    @Test
    fun directCitaKeepsNoun() {
        val intent = analyze("cita con el dentista")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
        assertEquals("Cita con el dentista", intent.title)
    }

    @Test
    fun voyALaCitaStripsArticle() {
        val intent = analyze("voy a la cita con el dentista")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
        assertEquals("Cita con el dentista", intent.title)
    }

    // --- Controles: el prefijo "Cita:" se conserva si el resto no lo menciona ---

    @Test
    fun prefixKeptWhenNoSelfMention() {
        val intent = analyze("tengo dentista revisión médica mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
        assertEquals("Cita: Dentista revisión médica", intent.title)
    }

    @Test
    fun prefixKeptWhenNoSelfMention2() {
        val intent = analyze("voy a revisión con el doctor")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
        assertEquals("Cita: Revisión con el doctor", intent.title)
    }
}
