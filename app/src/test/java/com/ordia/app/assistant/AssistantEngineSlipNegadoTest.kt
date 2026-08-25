package com.ordia.app.assistant

import com.ordia.app.data.local.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// c.1092: guard negada de «se me/se nos olvidó/pasó» (modismo activado c.797).
// PRE (sonda efímera sobre HEAD SU be4ebeb): «no se me olvidó nada» con una
// vencida en la cola nombraba la vencida (RUN_REPLAN) — mentira por omisión
// (declara una recuperación que el usuario está negando). Guardás espejo a
// c.1091 planWrapperIsNegated: `«no»` como preposición local, sin comer el
// resto del vocabulario; los positivos siguen recuperando.
class AssistantEngineSlipNegadoTest {

    private val now = 1_000_000_000_000L
    private val overdue = TaskEntity(id = 7, title = "Llamar al banco", dueAt = now - 90_000_000L)

    private fun answer(request: String, tasks: List<TaskEntity>) =
        AssistantEngine.answer(request, tasks, emptyList(), emptyList(), now)

    // --- Negados: caen al MENÚ (NONE), ya no a recapitulación ---

    @Test fun noSeMeOlvido_menu() {
        assertEquals(AssistantAction.NONE, answer("no se me olvidó nada", listOf(overdue)).action)
    }

    @Test fun noSeMeOlvida_menu() {
        assertEquals(AssistantAction.NONE, answer("no se me olvida nada", listOf(overdue)).action)
    }

    @Test fun noSeNosOlvido_menu() {
        assertEquals(AssistantAction.NONE, answer("no se nos olvidó nada", listOf(overdue)).action)
    }

    @Test fun noSeMePasa_menu() {
        assertEquals(AssistantAction.NONE, answer("no se me pasa nada", listOf(overdue)).action)
    }

    @Test fun noSeNosPasaron_menu() {
        assertEquals(AssistantAction.NONE, answer("no se nos pasaron cosas", listOf(overdue)).action)
    }

    @Test fun noSeMeOlvidoContent_menu() {
        assertEquals(AssistantAction.NONE, answer("no se me olvidó llamar al banco", listOf(overdue)).action)
    }

    @Test fun nadaSeMeOlvido_menu() {
        assertEquals(AssistantAction.NONE, answer("nada se me olvidó", listOf(overdue)).action)
    }

    // --- Positivos: siguen recuperando (c.797 intacta) ---

    @Test fun seMeOlvido_recupera() {
        val a = answer("se me olvidó llamar", listOf(overdue))
        assertTrue("recupera la vencida: ${a.text}", a.text.contains("Llamar al banco"))
        assertEquals(AssistantAction.RUN_REPLAN, a.action)
    }

    @Test fun seNosOlvido_recupera() {
        val a = answer("¿qué se nos olvidó?", listOf(overdue))
        assertEquals(AssistantAction.RUN_REPLAN, a.action)
    }

    @Test fun seMeOlvidaba_recupera() {
        val a = answer("¿qué se me olvidaba?", listOf(overdue))
        assertEquals(AssistantAction.RUN_REPLAN, a.action)
    }

    @Test fun seMePaso_recupera() {
        val a = answer("¿qué se me pasó?", listOf(overdue))
        assertEquals(AssistantAction.RUN_REPLAN, a.action)
    }

    // --- Guard local: la negación no come otro vocabulario ---

    @Test fun negadoOtroVerbo_sigueSinGuard() {
        // «no» delante de un negativo distinto no activa la guarda: la frase
        // no contiene «se me/no» y cae a otros caminos normales.
        assertEquals(AssistantAction.NONE, answer("no tengo nada vencido", emptyList()).action)
    }

    @Test fun interrogativaNo_menuNoReclamaVencida() {
        // «¿no se me olvidó nada?» (pregunta) también se negan.
        val a = answer("¿no se me olvidó nada?", listOf(overdue))
        assertTrue("nunca nombra vencidas al preguntar negando: ${a.text}", !a.text.contains("Llamar al banco"))
        assertEquals(AssistantAction.NONE, a.action)
    }
}
