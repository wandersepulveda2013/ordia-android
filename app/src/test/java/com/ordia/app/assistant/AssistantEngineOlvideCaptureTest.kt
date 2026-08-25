package com.ordia.app.assistant

import com.ordia.app.data.local.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// c.1090: lateral ABIERTA (4) — última restante de la auditoría c.1085.
// «olvidé/olvide <contenido>» declaración pasada caía al MENÚ (mentira por
// omisión, medida con sonda efímera). Hereda el contrato de la familia
// creación (c.987..c.1087): payload = título crudo tras el verbo; la UI
// confirma con vm.addSmartTask (NADA creado en silencio); OlvidEs escrita
// sin tilde (UI real: teclado laxo). Guards: pelada «olvidé» → guía SIN
// acción; negativo «no olvidé …» → MENÚ; imperativo «no olvides …» sigue
// capturando (c.1087 conserva).
class AssistantEngineOlvideCaptureTest {

    private fun answer(request: String) =
        AssistantEngine.answer(request, listOf(TaskEntity(id = 1, title = "Llamar al banco")), emptyList(), emptyList())

    @Test fun olvideConContenido_capturaCreate() {
        val a = answer("olvidé comprar leche")
        assertEquals(AssistantAction.CREATE_TASK, a.action)
        assertEquals("comprar leche", a.actionPayload)
    }

    @Test fun olvideSinTilde_capturaCreate() {
        val a = answer("olvide comprar leche")
        assertEquals(AssistantAction.CREATE_TASK, a.action)
        assertEquals("comprar leche", a.actionPayload)
    }

    @Test fun olvidePagoConDe_capturaCreate() {
        // El conector «de» («olvidé de llamar») también se despoja.
        val a = answer("olvidé de pagar el recibo")
        assertEquals(AssistantAction.CREATE_TASK, a.action)
        assertEquals("pagar el recibo", a.actionPayload)
    }

    // --- Guards ---

    @Test fun olvidePelada_guiaHonestaSinAccion() {
        val a = answer("olvidé")
        assertEquals(AssistantAction.NONE, a.action)
        assertTrue(a.text.contains("¿Qué"))
    }

    @Test fun olvideAlgoPelada_guiaHonestaSinAccion() {
        val a = answer("olvidé algo")
        assertEquals(AssistantAction.NONE, a.action)
        assertTrue(a.text.contains("¿Qué"))
    }

    @Test fun negativoNoOlvide_menuSinCapturar() {
        assertEquals(AssistantAction.NONE, answer("no olvidé comprar leche").action)
    }

    @Test fun imperativoNoOlvides_sigueCapturando() {
        // c.1087 conserva: el imperativo «no olvides …» captura.
        val a = answer("no olvides comprar leche")
        assertEquals(AssistantAction.CREATE_TASK, a.action)
    }

    @Test fun noSeMeOlvida_noCapturar() {
        assertEquals(AssistantAction.NONE, answer("no se me olvida nada").action)
    }

    // --- Regresiones hermanas intactas ---

    @Test fun regresionesFamiliasCaptura() {
        assertEquals(AssistantAction.CREATE_TASK, answer("recuérdamelo: comprar leche").action)
        assertEquals(AssistantAction.COMPLETE_TASK, answer("marca como hecha llamar al banco").action)
    }
}
