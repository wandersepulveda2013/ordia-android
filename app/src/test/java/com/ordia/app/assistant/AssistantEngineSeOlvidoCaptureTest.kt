package com.ordia.app.assistant

import com.ordia.app.data.local.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// c.1092+1: GAP residual del PRE — «se olvidó <contenido>» no-pronominal
// (3.ª persona sin «me/nos») caía al MENÚ (medido con sonda efímera). Espejo
// a c.1090 «olvidé/olvide»: captura CREATE_TASK con confirmación en la UI
// (NADA creado en silencio). Pelada («se olvidó»/«se olvidó algo/nada») →
// guía honesta; negativo «no se olvidó…» → MENÚ; ancla «^» se conserva, así
// la interrogativa «¿se olvidó X?» sigue al MENÚ (deliberado: ambiguo).
// Pronombre «se me/nos olvidó…» NO entra: es CO-TERMINO como olvido
// pronominal (c.797 recupera, c.797 guard negada - c.1092).
class AssistantEngineSeOlvidoCaptureTest {

    private fun answer(request: String) =
        AssistantEngine.answer(request, listOf(TaskEntity(id = 1, title = "Llamar al banco")), emptyList(), emptyList())

    @Test fun seOlvidoConContenido_capturaCreate() {
        val a = answer("se olvidó llamar a mamá")
        assertEquals(AssistantAction.CREATE_TASK, a.action)
        assertEquals("llamar a mamá", a.actionPayload)
    }

    @Test fun seOlvidoSinTilde_capturaCreate() {
        val a = answer("se olvido llamar a mamá")
        assertEquals(AssistantAction.CREATE_TASK, a.action)
        assertEquals("llamar a mamá", a.actionPayload)
    }

    @Test fun seOlvidoConDe_capturaCreate() {
        val a = answer("se olvidó de pagar el recibo")
        assertEquals(AssistantAction.CREATE_TASK, a.action)
        assertEquals("pagar el recibo", a.actionPayload)
    }

    // --- Guards ---

    @Test fun seOlvidoPelada_guiaHonestaSinAccion() {
        val a = answer("se olvidó")
        assertEquals(AssistantAction.NONE, a.action)
        assertTrue(a.text.contains("¿Qué"))
    }

    @Test fun seOlvidoAlgoPelada_guiaHonestaSinAccion() {
        val a = answer("se olvidó algo")
        assertEquals(AssistantAction.NONE, a.action)
        assertTrue(a.text.contains("¿Qué"))
    }

    @Test fun seOlvidoNadaPelada_guiaHonestaSinAccion() {
        val a = answer("se olvidó nada")
        assertEquals(AssistantAction.NONE, a.action)
        assertTrue(a.text.contains("¿Qué"))
    }

    @Test fun negativoNoSeOlvido_menuSinCapturar() {
        assertEquals(AssistantAction.NONE, answer("no se olvidó nada").action)
    }

    @Test fun interrogativoConQuePrecedido_menuNoSeCaptura() {
        // La ancla «^» se conserva: «¿se olvidó llamar a mamá?» sigue al MENÚ
        // (deliberado: forma interrogativa ambigua).
        assertEquals(AssistantAction.NONE, answer("¿se olvidó llamar a mamá?").action)
    }

    // --- Pronominarios del olvido duermen en otra vía (c.797) ---

    @Test fun seMeOlvido_noCapturaCreando() {
        // «se me olvidó llamar» no-procure: c.797 signature on the 'me/nos' so
        // la ruta de recuperación (RUN_REPLAN) sostiene. Jamás capturaría.
        val a = answer("se me olvidó llamar")
        assertTrue(a.action != AssistantAction.CREATE_TASK)
    }

    @Test fun seNosOlvido_noCapturaCreando() {
        val a = answer("se nos olvidó hacer la compra")
        assertTrue(a.action != AssistantAction.CREATE_TASK)
    }

    // --- Regresiones hermanas intactas ---

    @Test fun regresionesOlvideYRemind() {
        assertEquals(AssistantAction.CREATE_TASK, answer("olvidé llamar a mamá").action)
        assertEquals(AssistantAction.CREATE_TASK, answer("recuérdame: comprar leche").action)
    }
}
