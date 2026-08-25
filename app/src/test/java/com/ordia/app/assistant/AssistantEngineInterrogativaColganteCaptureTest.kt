package com.ordia.app.assistant

import com.ordia.app.data.local.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Test

// c.1099: última lateral ABIERTA de la auditoría SU c.1093 — «?» de cierre
// SIN «¿» de apertura en las familias hermanas de `olvideCapture`. Doctrina
// c.1093 (ya en olvide/seOlvido): la interrogativa colgante (teclado laxo)
// es ambigua y NUNCA debe capturar — MENÚ honesto (NONE). PRE medido con
// sonda efímera /tmp/probe1099/Probe.kt (motor real vía tools/run_probe.sh,
// base 277aaa6): 8 familias capturaban con el «?» como residuo en el
// payload («recuérdame llamar a mamá?» → CREATE_TASK «llamar a mamá?»…)
// y la pelada con espacio creaba TAREA BASURA literal «?» (doctrina c.969
// violada: «recuérdame ?» → CREATE_TASK payload «?»). Fix UN punto espejo:
// guarda `rawContent.endsWith("?") -> null` en las capturas hermanas
// (tras guía pelada/negación, orden espejo c.1093), cubriendo la pelada
// (contenido crudo «?») por el mismo camino. Match-engines (marca-hecha,
// pospón) inmunes: operan sobre ids ya resueltos, pineados byte-idénticos.
class AssistantEngineInterrogativaColganteCaptureTest {

    private fun answer(request: String) =
        AssistantEngine.answer(request, listOf(TaskEntity(id = 1, title = "Llamar al banco", dueAt = 1_800_000_000_000L)), emptyList(), emptyList())

    // --- Interrogativa colgante → MENÚ honesto (espejo doctrina c.1093) ---

    @Test fun recuerdameInterrogativaColgante_menuHonesto() {
        assertEquals(AssistantAction.NONE, answer("recuérdame llamar a mamá?").action)
    }

    @Test fun recuerdameloInterrogativaColgante_menuHonesto() {
        assertEquals(AssistantAction.NONE, answer("recuérdamelo: comprar leche?").action)
    }

    @Test fun avisameInterrogativaColgante_menuHonesto() {
        assertEquals(AssistantAction.NONE, answer("avísame mañana de llamar al banco?").action)
    }

    @Test fun escribemeNotaInterrogativaColgante_menuHonesto() {
        assertEquals(AssistantAction.NONE, answer("escríbeme una nota: ideas?").action)
    }

    @Test fun anadeTareaInterrogativaColgante_menuHonesto() {
        assertEquals(AssistantAction.NONE, answer("añade una tarea: comprar leche?").action)
    }

    @Test fun noOlvidesInterrogativaColgante_menuHonesto() {
        assertEquals(AssistantAction.NONE, answer("no olvides comprar leche?").action)
    }

    @Test fun quieroQueInterrogativaColgante_menuHonesto() {
        assertEquals(AssistantAction.NONE, answer("quiero que me recuerdes llamar a mamá?").action)
    }

    @Test fun hazmeNotaInterrogativaColgante_menuHonesto() {
        assertEquals(AssistantAction.NONE, answer("hazme una nota: ideas?").action)
    }

    @Test fun apuntameNotaInterrogativaColgante_menuHonesto() {
        assertEquals(AssistantAction.NONE, answer("apúntame una nota: ideas?").action)
    }

    @Test fun guardameEstoInterrogativaColgante_menuHonesto() {
        assertEquals(AssistantAction.NONE, answer("guárdame esto: llamar al banco?").action)
    }

    // --- Pelada con «?»: NUNCA tarea basura literal «?» (c.969) ---

    @Test fun recuerdamePeladaInterrogativa_menuHonestoNoBasura() {
        assertEquals(AssistantAction.NONE, answer("recuérdame ?").action)
    }

    // --- Controles: hermanas sin «?» capturan byte-idénticas ---

    @Test fun recuerdameSinCierre_capturaIntacta() {
        val a = answer("recuérdame llamar a mamá")
        assertEquals(AssistantAction.CREATE_TASK, a.action)
        assertEquals("llamar a mamá", a.actionPayload)
    }

    @Test fun olvideSinCierre_capturaIntacta() {
        val a = answer("olvidé comprar leche")
        assertEquals(AssistantAction.CREATE_TASK, a.action)
        assertEquals("comprar leche", a.actionPayload)
    }

    @Test fun escribemeNotaSinCierre_capturaIntacta() {
        val a = answer("escríbeme una nota: ideas")
        assertEquals(AssistantAction.CREATE_NOTE, a.action)
        assertEquals("ideas", a.actionPayload)
    }

    // --- Pines c.1093: la familia olvide ya trataba la colgante ---

    @Test fun olvideInterrogativaColgante_menuHonestoPin1093() {
        assertEquals(AssistantAction.NONE, answer("olvidé comprar leche?").action)
    }

    @Test fun seOlvidoInterrogativaColgante_menuHonestoPin1093() {
        assertEquals(AssistantAction.NONE, answer("se olvidó comprar leche?").action)
    }

    // --- Pines anti-overreach: interrogativa completa «¿…?» NUNCA captura ---

    @Test fun olvideInterrogativaCompleta_menuHonesto() {
        assertEquals(AssistantAction.NONE, answer("¿olvidé comprar leche?").action)
    }

    @Test fun recuerdameInterrogativaCompleta_menuHonesto() {
        assertEquals(AssistantAction.NONE, answer("¿me recuerdas llamar a mamá?").action)
    }

    @Test fun queOlvideInterrogativaCompleta_menuHonesto() {
        assertEquals(AssistantAction.NONE, answer("¿qué olvidé?").action)
    }

    // --- Pines: match-engines inmunes al «?» (operan sobre ids) ---

    @Test fun marcaHechaInterrogativaColgante_inmuneSobreIds() {
        val a = answer("márcala como hecha llamar al banco?")
        assertEquals(AssistantAction.COMPLETE_TASK, a.action)
        assertEquals("1", a.actionPayload)
    }

    @Test fun posponInterrogativaColgante_inmuneSobreIds() {
        val a = answer("pospón llamar al banco para mañana?")
        assertEquals(AssistantAction.POSTPONE_TASK, a.action)
        assertEquals("1", a.actionPayload)
    }

    // --- Pines guards: negación y despedida con colgante → MENÚ ---

    @Test fun negadaInterrogativaColgante_menuHonesto() {
        assertEquals(AssistantAction.NONE, answer("no olvidé comprar leche?").action)
    }

    @Test fun despedidaInterrogativaColgante_menuHonesto() {
        assertEquals(AssistantAction.NONE, answer("no me olvides?").action)
    }
}
