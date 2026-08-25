package com.ordia.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

// c.1093: auditoría probabilística de las guardas del verbo-family
// (olvide / seOlvido / recuérdamelo). Hallazgo medido con sonda efímera
// /tmp/probe1093/Probe.kt: contenido SIN SUSTANCIA creaba tarea basura
// (doctrina c.969 violada): conector huérfano («olvide de» → «de») y
// puramente temporal («recuérdamelo a las 5» → «a las 5»,
// «recuérdamelo mañana por la mañana», «olvidé mañana»). La guía
// honesta es la respuesta; NUNCA tarea basura. RED exacto EXACTAMENTE
// 8 fallos (los huecos); guards/regresiones verdes desde RED como pins.
class AssistantEngineVerboFamilyContenidoSinSustanciaTest {

    private val now = 1753495200000L // 2026-07-26 12:00 UTC
    private val zone: ZoneId = ZoneId.of("America/Bogota")

    private fun answer(query: String): AssistantAnswer =
        AssistantEngine.answer(query, emptyList(), emptyList(), emptyList(), now, zone)

    // --- huecos: conector «de» huérfano (nunca tarea «de») ---
    @Test fun olvideDeSoloEsGuia() {
        val ans = answer("olvide de")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.text.contains("¿Qué se te olvidó?"))
    }

    @Test fun seOlvidoDeSoloEsGuia() {
        val ans = answer("se olvidó de")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.text.contains("¿Qué se olvidó?"))
    }

    // --- huecos: contenido puramente temporal (nunca tarea temporal) ---
    @Test fun recuerdameloALasEsGuia() {
        val ans = answer("recuérdamelo a las 5")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.text.contains("Escríbeme qué"))
    }

    @Test fun recuerdameloMananaPorLaMananaEsGuia() {
        val ans = answer("recuérdamelo mañana por la mañana")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.text.contains("Escríbeme qué"))
    }

    @Test fun recuerdameloPorLaNocheEsGuia() {
        val ans = answer("recuérdamelo por la noche")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.text.contains("Escríbeme qué"))
    }

    @Test fun recuerdameloMananaALasEsGuia() {
        val ans = answer("recuérdamelo mañana a las 9")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.text.contains("Escríbeme qué"))
    }

    @Test fun olvideTemporalSoloEsGuia() {
        val ans = answer("olvidé mañana")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.text.contains("¿Qué se te olvidó?"))
    }

    @Test fun olvideALasEsGuia() {
        val ans = answer("olvidé a las 5")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.text.contains("¿Qué se te olvidó?"))
    }

    // --- pins (capturas reales sobreviven) ---
    @Test fun capturaOlvideRealSigue() {
        val ans = answer("olvidé comprar leche")
        assertEquals(AssistantAction.CREATE_TASK, ans.action)
        assertEquals("comprar leche", ans.actionPayload)
    }

    @Test fun capturaSeFormRealSigue() {
        val ans = answer("se olvidó de la cita")
        assertEquals(AssistantAction.CREATE_TASK, ans.action)
        assertEquals("la cita", ans.actionPayload)
    }

    @Test fun capturaRecuerdameloRealSigue() {
        val ans = answer("recuérdamelo: llamar al banco")
        assertEquals(AssistantAction.CREATE_TASK, ans.action)
        assertEquals("llamar al banco", ans.actionPayload)
    }

    // --- pins anti-overreach (temporal dentro de contenido real captura) ---
    @Test fun contenidoRealConTemporalCaptura() {
        val ans = answer("recuérdamelo comprar leche mañana")
        assertEquals(AssistantAction.CREATE_TASK, ans.action)
        assertEquals("comprar leche mañana", ans.actionPayload)
    }

    @Test fun verboConALasCaptura() {
        val ans = answer("recuérdamelo llamar a las 5")
        assertEquals(AssistantAction.CREATE_TASK, ans.action)
        assertEquals("llamar a las 5", ans.actionPayload)
    }

    // --- pins de guía preexistentes ---
    @Test fun peladaOlvideGuiaConserva() {
        val ans = answer("olvidé")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.text.contains("¿Qué se te olvidó?"))
    }

    @Test fun peladaRecuerdameloGuiaConserva() {
        val ans = answer("recuérdamelo")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.text.contains("Escríbeme qué"))
    }

    @Test fun olvideDeAlgoGuiaDesdeRed() {
        val ans = answer("olvidé de algo")
        assertEquals(AssistantAction.NONE, ans.action)
        assertTrue(ans.text.contains("¿Qué se te olvidó?"))
    }
}
