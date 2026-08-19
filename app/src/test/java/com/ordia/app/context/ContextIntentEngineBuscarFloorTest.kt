package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.715 (P1 olvido silencioso en captura pasiva — SEGUNDA clase de verbos
 * cotidianos de gestión, forma 5/14: "buscar <objeto>" descubierto por sonda
 * `tools/probe/ManagementVerbDiscoveryProbe.kt` c.711; UNA forma por ciclo,
 * doctrina anti-overreach): "buscar el seguro de la casa mañana" se
 * DESCARTABA (analyze → NULL) — el usuario recupera una cosa concreta y
 * Ordía lo olvidaba (P1). Fix: piso "buscar" en [hasStrongTaskImperative]
 * (ancla inicio/acuse/`TASK_FLOOR_TEMPORAL`, `(?<!no )`, `\s+\w` exige
 * objeto) + plantilla de título "(buscar) X"→"Buscar X" (patrón
 * c.691…c.714; lección c.616: el match arranca en el verbo). Kind decidido:
 * TASK, en deliberación contra ERRAND — "buscar" recupera el objeto (un
 * documento/llave/seguro), no un destino (ERRAND reserva el desplazamiento
 * a "ir a/pasar por"). Anti-overreach: objeto requerido, negada/duda/
 * condición/sustantivo "búsqueda"/suelto "buscar" NULL; envolvente c.613
 * gobierna TASK. Determinista (regex), sin random, sin IA fingida.
 */
class ContextIntentEngineBuscarFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: "buscar <objeto>" es una gestión clara ---

    @Test
    fun buscarElSeguroDeLaCasaManana_capturesTaskWithDueAt() {
        val intent = analyze("buscar el seguro de la casa mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Buscar el seguro de la casa", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun buscarLasLlaves_capturesTaskWithoutDueAt() {
        val intent = analyze("buscar las llaves")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Buscar las llaves", intent.title)
    }

    @Test
    fun buscarTrasAcuse_capturesTask() {
        val intent = analyze("vale, buscar las llaves mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Buscar las llaves", intent.title)
    }

    @Test
    fun buscarTrasPrefijoTemporal_capturesTask() {
        val intent = analyze("mañana buscar el traje")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Buscar el traje", intent.title)
    }

    // --- Controles anti-overreach (deben permanecer NULL) ---

    @Test
    fun noBuscarLasLlaves_negatedStaysNull() {
        assertNull(analyze("no buscar las llaves"))
    }

    @Test
    fun quizasBuscarLaLlave_hedgeStaysNull() {
        assertNull(analyze("quizá buscar la llave"))
    }

    @Test
    fun sustantivoBusqueda_nounStaysNull() {
        assertNull(analyze("la búsqueda de las llaves"))
    }

    @Test
    fun buscarSinObjeto_bareVerbStaysNull() {
        assertNull(analyze("buscar"))
    }

    // --- Regresión: la envolvente c.613 ("recuérdame…") sigue gobernando ---

    @Test
    fun recuerdameBuscarElSeguro_wrapperWinsTask() {
        val intent = analyze("recuérdame buscar el seguro")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}
