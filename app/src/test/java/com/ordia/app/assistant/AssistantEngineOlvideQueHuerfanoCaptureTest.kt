package com.ordia.app.assistant

import com.ordia.app.data.local.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// c.1095: lateral ABIERTA de mi auditoría c.1093 — «que» huérfano en el
// payload del verbo-family (olvide/seOlVidó). Medida PRE con sonda efímera
// /tmp/probe1095/Probe.kt (motor real vía tools/run_probe.sh):
//  - «olvidé que» / «se olvidó que» creaban TAREA BASURA «que»
//    (doctrina c.969 violada — hermana de la «:» pelada c.992 y del
//    «que» subordinado c.993);
//  - «olvidé que llamar a mamá» capturaba con título residual «que llamar
//    a mamá» (conector huérfano; las hermanas remindMe c.993 y
//    noOlvides c.1087 ya lo despojan con LEADING_QUE);
//  - bonus anti-overreach: «olvidé que no llamar…» capturaba la NEGACIÓN
//    (la guarda «no » no alcanzaba tras el «que») — c.993 ya medía que la
//    negación tras «que» debe llegar al check.
// Fix UN punto: despojar LEADING_QUE del crudo en `olvideCapture` (el val
// ya existe c.993; hermanas noOlvides c.1087 y remindMeLo c.1088/c.1093 lo
// reusan). Guards byte-equivalentes: capturas sin «que», interrogativa con
// ancla «¿», pelada-guía «olvidé algo», regresiones hermanas intactas.
class AssistantEngineOlvideQueHuerfanoCaptureTest {

    private fun answer(request: String) =
        AssistantEngine.answer(request, listOf(TaskEntity(id = 1, title = "Llamar al banco")), emptyList(), emptyList())

    // --- Capturas: «que» subordinado se despoja del título (espejo c.993) ---

    @Test fun olvideQueConContenido_tituloSinQue() {
        val a = answer("olvidé que llamar a mamá")
        assertEquals(AssistantAction.CREATE_TASK, a.action)
        assertEquals("llamar a mamá", a.actionPayload)
    }

    @Test fun olvideQueComprar_tituloSinQue() {
        val a = answer("olvidé que comprar leche")
        assertEquals(AssistantAction.CREATE_TASK, a.action)
        assertEquals("comprar leche", a.actionPayload)
    }

    @Test fun seOlvidoQueConContenido_tituloSinQue() {
        val a = answer("se olvidó que tenía que pagar la luz")
        assertEquals(AssistantAction.CREATE_TASK, a.action)
        assertEquals("tenía que pagar la luz", a.actionPayload)
    }

    @Test fun olvideSinTildeQue_tituloSinQue() {
        val a = answer("olvide que avisar al banco")
        assertEquals(AssistantAction.CREATE_TASK, a.action)
        assertEquals("avisar al banco", a.actionPayload)
    }

    // --- Pelada CON «que»: guía honesta, NUNCA tarea basura «que» (c.969/c.992/c.993) ---

    @Test fun olvideQuePelada_guiaHonestaSinAccion() {
        val a = answer("olvidé que")
        assertEquals(AssistantAction.NONE, a.action)
        assertTrue(a.text.contains("¿Qué"))
    }

    @Test fun seOlvidoQuePelada_guiaHonestaSinAccion() {
        val a = answer("se olvidó que")
        assertEquals(AssistantAction.NONE, a.action)
        assertTrue(a.text.contains("¿Qué"))
    }

    // --- Anti-overreach: la negación tras «que» llega a la guarda (c.993) ---

    @Test fun olvideQueNegado_menuSinCapturar() {
        // «olvidé que no llamar a mamá» pide lo contrario; NUNCA capturar.
        assertEquals(AssistantAction.NONE, answer("olvidé que no llamar a mamá").action)
    }

    // --- Guards byte-equivalentes (medidos PRE) ---

    @Test fun guardSinQue_capturaIntacta() {
        val a = answer("olvidé comprar leche")
        assertEquals(AssistantAction.CREATE_TASK, a.action)
        assertEquals("comprar leche", a.actionPayload)
    }

    @Test fun guardHermana_remindMeQueSigueCapturando() {
        val a = answer("recuérdame que comprar leche")
        assertEquals(AssistantAction.CREATE_TASK, a.action)
        assertEquals("comprar leche", a.actionPayload)
    }

    @Test fun guardHermana_noOlvidesQueSigueCapturando() {
        // c.1087 conserva: el imperativo «no olvides …» captura.
        assertEquals(AssistantAction.CREATE_TASK, answer("no olvides que comprar leche").action)
    }

    @Test fun guardPelada_olvideAlgoGuia() {
        val a = answer("olvidé algo")
        assertEquals(AssistantAction.NONE, a.action)
        assertTrue(a.text.contains("¿Qué"))
    }

    @Test fun guardInterrogativa_acentuadaAlMenu() {
        assertEquals(AssistantAction.NONE, answer("¿olvidé comprar leche?").action)
    }

    // --- Regresiones hermanas intactas ---

    @Test fun regresionesFamiliasCaptura() {
        assertEquals(AssistantAction.CREATE_TASK, answer("recuérdamelo: comprar leche").action)
        assertEquals(AssistantAction.COMPLETE_TASK, answer("marca como hecha llamar al banco").action)
    }
}
