package com.ordia.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

// c.1087: familia recordatorio «no se te olvide <x>» / «no olvides <x>» /
// «no te olvides <x>» — caía al MENÚ genérico (mentira por omisión) mientras
// «recuérdame …» capturaba (c.987). Hermana de remindMeCapture: contenido →
// CREATE_TASK con el payload; pelada → guía honesta SIN acción; guards de
// anti-overreach («no me olvides» despedida, pretérito «no olvidaste»,
// contenido negativo «no …») NUNCA capturan.
class AssistantEngineNoOlvidesReminderCaptureTest {

    private val now = 1753495200000L // 2026-07-26 12:00 UTC
    private val zone: ZoneId = ZoneId.of("America/Bogota")

    private fun answer(query: String): AssistantAnswer =
        AssistantEngine.answer(query, emptyList(), emptyList(), emptyList(), now, zone)

    @Test fun seTeOlvideCaptura() {
        val ans = answer("no se te olvide llamar a mamá")
        assertEquals(AssistantAction.CREATE_TASK, ans.action)
        assertEquals("llamar a mamá", ans.actionPayload)
    }

    @Test fun olvidesCaptura() {
        val ans = answer("no olvides las pastillas")
        assertEquals(AssistantAction.CREATE_TASK, ans.action)
        assertEquals("las pastillas", ans.actionPayload)
    }

    @Test fun teOlvidesCaptura() {
        val ans = answer("no te olvides de comprar leche")
        assertEquals(AssistantAction.CREATE_TASK, ans.action)
        assertEquals("de comprar leche", ans.actionPayload)
    }

    @Test fun conectorDeStrip() {
        val ans = answer("no olvides que la cita es a las 3")
        assertEquals(AssistantAction.CREATE_TASK, ans.action)
        assertEquals("la cita es a las 3", ans.actionPayload)
    }

    @Test fun mayusculaCaptura() {
        val ans = answer("No olvides las llaves")
        assertEquals(AssistantAction.CREATE_TASK, ans.action)
        assertEquals("las llaves", ans.actionPayload)
    }

    @Test fun peladaGuiaHonestaSinAccion() {
        val ans = answer("no olvides")
        assertNotEquals(AssistantAction.CREATE_TASK, ans.action)
        assertTrue(ans.text.contains("recuérdame"))
    }

    @Test fun despedidaNoMeOlvidesNuncaCaptura() {
        val ans = answer("no me olvides")
        assertNotEquals(AssistantAction.CREATE_TASK, ans.action)
    }

    @Test fun preteritoNuncaCaptura() {
        val ans = answer("no olvidaste llamar a mamá")
        assertNotEquals(AssistantAction.CREATE_TASK, ans.action)
    }

    @Test fun contenidoNegativoNuncaCaptura() {
        val ans = answer("no olvides no ir al banco")
        assertNotEquals(AssistantAction.CREATE_TASK, ans.action)
    }

    @Test fun recuerdameRegresion() {
        val ans = answer("recuérdame llamar a mamá")
        assertEquals(AssistantAction.CREATE_TASK, ans.action)
        assertEquals("llamar a mamá", ans.actionPayload)
    }
}
