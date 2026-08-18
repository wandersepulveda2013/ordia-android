package com.ordia.app.context

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression locks para el gate de contenido bloqueado de [ContextIntentEngine]
 * (c.613, area context / privacidad / evitar perdida silenciosa de tareas).
 *
 * Antes de la correccion, `containsBlockedContent` casaba raices dainas
 * (`matar`, `bomba`, `droga`, `secuestr`...) con regex primitivos `\b(...)\b`
 * SIN exenciones de contexto legitimo, duplicando fuera de sincronia las
 * reglas canonicas de [ContextPrivacyFilter] /
 * [com.ordia.app.intelligence.IntelligenceSafetyGate] (que desde c.582 usan
 * [com.ordia.app.domain.ContentModeration.isHarmful] con exenciones de
 * colocacion/proximidad). Eso descartaba SILENCIOSAMENTE tareas legitimas en
 * la ruta de captura contextual (`analyze` devolvia null): la misma clase de
 * falso positivo que c.582 corrigio en los otros gates, pero que nunca se
 * migro a esta ruta. Ademas los regex tenian mojibake que impedia casar las
 * palabras previstas.
 *
 * Solucion: `analyze` ya invoca `ContextPrivacyFilter.shouldBlock` (paso 1) que
 * aplica las reglas canonicas CON exenciones. `containsBlockedContent` (paso
 * 3) se reduce a la UNICA categoria tematica que `ContextPrivacyFilter` NO
 * cubre (insultos graves, paridad con `IntelligenceSafetyGate`), delegando al
 * algoritmo centralizado `ContentModeration.isHarmful`. Asi las tareas
 * legitimas ("matar el proceso", "bomba de agua", "droga en la farmacia",
 * "modelo de amenaza", "pistola de agua", "secuestro de DNS") dejan de
 * descartarse, sin abrir paso al contenido genuinamente daino (sigue
 * bloqueado por el paso 1) ni a los insultos.
 */
class ContextIntentEngineBlockedTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(
                ContextCaptureSource.NOTIFICATION,
                raw,
                1723939200000L
            )
        )

    // --- Tareas legitimas que antes se descartaban silenciosamente (falsos positivos) ---

    @Test
    fun legitimateKillProcessTaskIsNotDiscarded() {
        val intent = analyze("recu\u00e9rdame matar el proceso del servidor hoy")
        assertNotNull("matar el proceso es tarea legitima, no debe descartarse", intent)
    }

    @Test
    fun legitimateWaterBombTaskIsNotDiscarded() {
        val intent = analyze("tengo que comprar la bomba de agua ma\u00f1ana")
        assertNotNull("bomba de agua es tarea legitima, no debe descartarse", intent)
    }

    @Test
    fun legitimateDnsHijackReviewIsNotDiscarded() {
        val intent = analyze("no olvides revisar el secuestro de DNS")
        assertNotNull("revisar el secuestro de DNS es tarea legitima", intent)
    }

    @Test
    fun legitimatePharmacyDrugTaskIsNotDiscarded() {
        val intent = analyze("recu\u00e9rdame comprar la droga en la farmacia")
        assertNotNull("comprar la droga en la farmacia es tarea legitima", intent)
    }

    @Test
    fun legitimateThreatModelTaskIsNotDiscarded() {
        val intent = analyze("tengo que hacer el modelo de amenaza del sistema")
        assertNotNull("modelo de amenaza es tarea legitima", intent)
    }

    @Test
    fun legitimateWaterPistolTaskIsNotDiscarded() {
        val intent = analyze("hay que limpiar la pistola de agua de los ni\u00f1os")
        assertNotNull("pistola de agua es tarea legitima", intent)
    }

    // --- Contenido genuinamente daino: SIGUE bloqueado (via ContextPrivacyFilter, paso 1) ---

    @Test
    fun genuineHarmToPersonStillBlocked() {
        val intent = analyze("recu\u00e9rdame matar a juan")
        assertNull("amenaza real a una persona debe bloquearse", intent)
    }

    @Test
    fun genuineIllegalDrugStillBlocked() {
        val intent = analyze("tengo que comprar cocaina ma\u00f1ana")
        assertNull("compra de droga ilegal debe bloquearse", intent)
    }

    @Test
    fun genuineMarijuanaPurchaseStillBlocked() {
        val intent = analyze("no olvides comprar marihuana")
        assertNull("compra de marihuana debe bloquearse", intent)
    }

    // --- Anti-overreach: insultos aislados si se bloquean (paridad con IntelligenceSafetyGate) ---

    @Test
    fun insultIsolatedStillBlocked() {
        val intent = analyze("recu\u00e9rdame pendejo")
        // "recu\u00e9rdame pendejo" no define una tarea organizativa valida; el insulto
        // aislado (gate de insultos, paridad con IntelligenceSafetyGate) lo descarta.
        assertNull("insulto aislado debe bloquearse", intent)
    }
}
