package com.ordia.app.assistant

import com.ordia.app.data.local.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// c.1096: lateral ABIERTA (B) de la auditoría SU c.1093 — mayúsculas con
// tilde no capturan en el engine del asistente. Causa raíz medida con sonda
// efímera /tmp/probe1096/Probe.kt (motor real vía tools/run_probe.sh):
// todos los patterns usan «(?i)» inline, que en la JVM es ASCII-only —
// «RECUÉRDAME LLAMAR A MAMÁ», «OLVIDÉ COMPRAR LECHE», «ESCRÍBEME UNA NOTA»
// → NONE honesto (captura real perdida: el usuario declara en caps y el
// asistente lo ignora; evitar-olvidos P1). Fix UN punto mecánico:
// «(?i)» → «(?iu)» en todo AssistantEngine.kt (60 patrones; UNICODE_CASE
// añade fold Unicode, cero cambio de semántica ASCII; \b intacto).
// Guards byte-equivalentes pineadas: pelada-guía, interrogativa «¿…?»,
// negación, despedida, hermanas minúsculas (intactas desde RED).
class AssistantEngineMayusculasTildeCaptureTest {

    private fun answer(request: String) =
        AssistantEngine.answer(request, listOf(TaskEntity(id = 1, title = "Llamar al banco", dueAt = 1_800_000_000_000L)), emptyList(), emptyList())

    // --- Capturas medidas PRE con sonda: mayúsculas acentuadas → acción hermana de minúsculas ---

    @Test fun recuerdameCaps_tareaCreada() {
        val a = answer("RECUÉRDAME LLAMAR A MAMÁ")
        assertEquals(AssistantAction.CREATE_TASK, a.action)
        assertEquals("LLAMAR A MAMÁ", a.actionPayload)
    }

    @Test fun recuerdameloCaps_tareaCreada() {
        val a = answer("RECUÉRDAMELO: COMPRAR LECHE")
        assertEquals(AssistantAction.CREATE_TASK, a.action)
        assertEquals("COMPRAR LECHE", a.actionPayload)
    }

    @Test fun olvideCaps_tareaCreada() {
        val a = answer("OLVIDÉ COMPRAR LECHE")
        assertEquals(AssistantAction.CREATE_TASK, a.action)
        assertEquals("COMPRAR LECHE", a.actionPayload)
    }

    @Test fun seOlvidoCaps_tareaCreada() {
        val a = answer("SE OLVIDÓ COMPRAR LECHE")
        assertEquals(AssistantAction.CREATE_TASK, a.action)
        assertEquals("COMPRAR LECHE", a.actionPayload)
    }

    @Test fun avisameCaps_tareaCreada() {
        val a = answer("AVÍSAME MAÑANA DE LLAMAR AL BANCO")
        assertEquals(AssistantAction.CREATE_TASK, a.action)
        assertEquals("LLAMAR AL BANCO MAÑANA", a.actionPayload)
    }

    @Test fun escribemeNotaCaps_notaCreada() {
        val a = answer("ESCRÍBEME UNA NOTA: IDEAS")
        assertEquals(AssistantAction.CREATE_NOTE, a.action)
        assertEquals("IDEAS", a.actionPayload)
    }

    @Test fun apuntameNotaCaps_notaCreada() {
        val a = answer("APÚNTAME UNA NOTA: IDEAS")
        assertEquals(AssistantAction.CREATE_NOTE, a.action)
        assertEquals("UNA NOTA: IDEAS", a.actionPayload)
    }

    @Test fun marcaHechaCaps_tareaCompletada() {
        val a = answer("MÁRCALA COMO HECHA LLAMAR AL BANCO")
        assertEquals(AssistantAction.COMPLETE_TASK, a.action)
    }

    @Test fun anadeTareaCaps_tareaCreada() {
        val a = answer("AÑADE UNA TAREA: COMPRAR LECHE")
        assertEquals(AssistantAction.CREATE_TASK, a.action)
        assertEquals("COMPRAR LECHE", a.actionPayload)
    }

    @Test fun posponCaps_tareaPospuesta() {
        val a = answer("POSPÓN LLAMAR AL BANCO PARA MAÑANA")
        assertEquals(AssistantAction.POSTPONE_TASK, a.action)
        assertEquals("1", a.actionPayload)
    }

    // --- Guards byte-equivalentes (anti-overreach: NUNCA capturar lo contrario) ---

    @Test fun peladaCapsGuia_guiaHonestaSinAccion() {
        val a = answer("OLVIDÉ ALGO")
        assertEquals(AssistantAction.NONE, a.action)
        assertTrue(a.text.contains("¿Qué"))
    }

    @Test fun interrogativaCaps_noCaptura() {
        val a = answer("¿OLVIDÉ COMPRAR LECHE?")
        assertEquals(AssistantAction.NONE, a.action)
    }

    @Test fun negacionCaps_noCaptura() {
        val a = answer("NO OLVIDÉ COMPRAR LECHE")
        assertEquals(AssistantAction.NONE, a.action)
    }

    @Test fun despedidaCaps_noCaptura() {
        val a = answer("NO ME OLVIDES")
        assertEquals(AssistantAction.NONE, a.action)
    }

    // --- Controles: minúsculas hermanas y caps SIN tilde ya capturaban — intactas ---

    @Test fun minusculasHermana_sigueCapturando() {
        val a = answer("recuérdame llamar a mamá")
        assertEquals(AssistantAction.CREATE_TASK, a.action)
        assertEquals("llamar a mamá", a.actionPayload)
    }

    @Test fun capsSinTildeYaCapturaba_sigueCapturando() {
        val a = answer("NO OLVIDES COMPRAR LECHE")
        assertEquals(AssistantAction.CREATE_TASK, a.action)
    }

    @Test fun capsSinTildeNotaYaCapturaba_sigueCapturando() {
        val a = answer("GUARDA UNA NOTA: IDEAS")
        assertEquals(AssistantAction.CREATE_NOTE, a.action)
    }
}
