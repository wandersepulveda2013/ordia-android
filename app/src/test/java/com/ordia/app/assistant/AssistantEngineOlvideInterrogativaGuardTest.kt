package com.ordia.app.assistant

import com.ordia.app.data.local.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// c.1093: auditoría probabilística de las guardas del verbo-family
// (olvide/seOlvido/recuérdamelo), PRE con sonda efímera /tmp/probe1093:
// 25/27 guardas OK, 2 GAP — interrogativa sin «¿» de apertura («se olvidó
// comprar leche?» / «olvidé comprar leche?») capturaba falsamente. El
// ancla «^» sólo rechaza el «¿» abierto (deliberado: interrogativa
// ambigua, nunca capturar); el «?» colgante (teclado laxo, la forma más
// frecuente del usuario móvil) caía dentro. Guard simétrica: un contenido
// terminado en «?» → MENÚ, nunca capturar. Declarativas (sin «?»), con
// punto o «!», peladas-guía, negativas y ancla-«¿» intactas.
class AssistantEngineOlvideInterrogativaGuardTest {

    private fun answer(request: String) =
        AssistantEngine.answer(request, listOf(TaskEntity(id = 1, title = "Llamar al banco")), emptyList(), emptyList())

    // --- Capturas de RED (los GAP de la auditoría) ---

    @Test fun seOlvidoInterrogativaColgante_menuNoCaptura() {
        assertEquals(AssistantAction.NONE, answer("se olvidó comprar leche?").action)
    }

    @Test fun seOlvidoDeInterrogativaColgante_menuNoCaptura() {
        assertEquals(AssistantAction.NONE, answer("se olvidó de comprar leche?").action)
    }

    @Test fun olvideInterrogativaColgante_menuNoCaptura() {
        assertEquals(AssistantAction.NONE, answer("olvidé comprar leche?").action)
    }

    // --- Guards/pins (verdes desde RED) ---

    @Test fun seOlvidoDeclarativa_sigueCapturando() {
        val a = answer("se olvidó comprar leche")
        assertEquals(AssistantAction.CREATE_TASK, a.action)
        assertEquals("comprar leche", a.actionPayload)
    }

    @Test fun olvideDeclarativa_sigueCapturando() {
        val a = answer("olvidé comprar leche")
        assertEquals(AssistantAction.CREATE_TASK, a.action)
        assertEquals("comprar leche", a.actionPayload)
    }

    @Test fun seOlvidoExclamativa_esDeclarativaYCaptura() {
        val a = answer("se olvidó comprar leche!")
        assertEquals(AssistantAction.CREATE_TASK, a.action)
        assertEquals("comprar leche", a.actionPayload)
    }

    @Test fun olvideConPunto_esDeclarativaYCaptura() {
        val a = answer("olvidé comprar leche.")
        assertEquals(AssistantAction.CREATE_TASK, a.action)
        assertEquals("comprar leche", a.actionPayload)
    }

    @Test fun interrogativaConQuePrecedido_menuSinCambio() {
        assertEquals(AssistantAction.NONE, answer("¿se olvidó comprar leche?").action)
        assertEquals(AssistantAction.NONE, answer("¿olvidé comprar leche?").action)
    }

    @Test fun peladaInterrogativa_guiaHonestaSinAccion() {
        // Pelada con «?»: conserva la GUÍA (no el menú), útil al usuario.
        val a = answer("se olvidó algo?")
        assertEquals(AssistantAction.NONE, a.action)
        assertTrue(a.text.contains("¿Qué"))
    }

    @Test fun negativaInterrogativa_nuncaEntraALaFamilia() {
        assertEquals(AssistantAction.NONE, answer("no se olvidó comprar leche?").action)
        assertEquals(AssistantAction.NONE, answer("no olvidé comprar leche?").action)
    }
}
