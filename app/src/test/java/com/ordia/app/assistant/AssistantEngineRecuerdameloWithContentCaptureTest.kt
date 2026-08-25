package com.ordia.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

// c.1088: «recuérdamelo: <contenido>» — cierre de la lateral ABIERTA (3)
// de la auditoría c.1085. La guía pelada c.996 admite EXACTAMENTE
// «recuérdamelo[:] [temporal]»; la forma CON contenido caía al MENÚ
// genérico (mentira por omisión) mientras «recuérdame <contenido>»
// captura (c.987). RED exacto EXACTAMENTE 4 fallos (las 4 capturas;
// los guards/guides/regresiones pasan desde RED como pins).
class AssistantEngineRecuerdameloWithContentCaptureTest {

    private val now = 1753495200000L // 2026-07-26 12:00 UTC
    private val zone: ZoneId = ZoneId.of("America/Bogota")

    private fun answer(query: String): AssistantAnswer =
        AssistantEngine.answer(query, emptyList(), emptyList(), emptyList(), now, zone)

    // --- capturas ---
    @Test fun capturaConDosPuntos() {
        val ans = answer("recuérdamelo: llamar al banco")
        assertEquals(AssistantAction.CREATE_TASK, ans.action)
        assertEquals("llamar al banco", ans.actionPayload)
    }

    @Test fun capturaSinDosPuntos() {
        val ans = answer("recuérdamelo pagar la luz")
        assertEquals(AssistantAction.CREATE_TASK, ans.action)
        assertEquals("pagar la luz", ans.actionPayload)
    }

    @Test fun capturaConObjetoCompra() {
        val ans = answer("recuérdamelo: comprar leche")
        assertEquals(AssistantAction.CREATE_TASK, ans.action)
        assertEquals("comprar leche", ans.actionPayload)
    }

    @Test fun capturaSinTilde() {
        val ans = answer("recuerdamelo: llamar a Ana")
        assertEquals(AssistantAction.CREATE_TASK, ans.action)
        assertEquals("llamar a Ana", ans.actionPayload)
    }

    // --- guards ---
    @Test fun peladaGuiaSinAccion() {
        val ans = answer("recuérdamelo")
        assertTrue(ans.text.contains("Escríbeme qué"))
        assertEquals(AssistantAction.NONE, ans.action)
    }

    @Test fun peladaConTemporalGuiaSinAccion() {
        val ans = answer("recuérdamelo mañana")
        assertTrue(ans.text.contains("Escríbeme qué"))
        assertEquals(AssistantAction.NONE, ans.action)
    }

    @Test fun negativoNuncaCapturaLoContrario() {
        val ans = answer("recuérdamelo no ir al banco")
        assertEquals(AssistantAction.NONE, ans.action)
    }

    // --- regresión ---
    @Test fun recuerdameSinEncliticoSigueCapturando() {
        val ans = answer("recuérdame llamar al banco")
        assertEquals(AssistantAction.CREATE_TASK, ans.action)
        assertEquals("llamar al banco", ans.actionPayload)
    }
}
