package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.716 (P1 olvido silencioso en captura pasiva — SEGUNDA clase de verbos
 * cotidianos de gestión, forma 6/14: "coger <objeto>" descubierto por sonda
 * `tools/probe/ManagementVerbDiscoveryProbe.kt` c.711; UNA forma por ciclo,
 * doctrina anti-overreach): "coger el bus mañana"/"coger la ropa mañana" se
 * DESCARTABAN (analyze → NULL) — el usuario apunta una gestión cotidiana con
 * fecha explícita y Ordía lo olvidaba (P1). Fix: piso "coger" en
 * [hasStrongTaskImperative] (ancla inicio/acuse/`TASK_FLOOR_TEMPORAL`,
 * `(?<!no )`, `\s+\w` exige objeto) + plantilla de título "(coger) X"→
 * "Coger X" (patrón c.691…c.715) + keyword "coger" en TASK (paridad
 * avisar/pedir/solicitar/buscar, lección c.713). Kind decidido: TASK, en
 * deliberación contra SHOPPING — "coger" toma/recoge una cosa (bus/ropa/
 * llaves), sin semántica de compra ("comprar/supermercado/tienda" viven en
 * SHOPPING); tampoco es ERRAND (desplazamiento a destino, "ir a/pasar por").
 * Anti-overreach: objeto requerido, negada/duda/pasado "cogí…"/suelto
 * "coger" NULL; envolvente c.613 gobierna TASK. Determinista (regex), sin
 * random, sin IA fingida.
 */
class ContextIntentEngineCogerFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: "coger <objeto>" es una gestión clara ---

    @Test
    fun cogerElBusManana_capturesTaskWithDueAt() {
        val intent = analyze("coger el bus mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Coger el bus", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun cogerLaRopa_capturesTaskWithoutDueAt() {
        val intent = analyze("coger la ropa")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Coger la ropa", intent.title)
    }

    @Test
    fun cogerTrasAcuse_capturesTask() {
        val intent = analyze("vale, coger las llaves mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Coger las llaves", intent.title)
    }

    @Test
    fun cogerTrasPrefijoTemporal_capturesTask() {
        val intent = analyze("mañana coger el traje")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Coger el traje", intent.title)
    }

    // --- Controles anti-overreach (deben permanecer NULL) ---

    @Test
    fun noCogerElBusManana_negatedStaysNull() {
        assertNull(analyze("no coger el bus mañana"))
    }

    @Test
    fun quizasCogerElBus_hedgeStaysNull() {
        assertNull(analyze("quizá coger el bus"))
    }

    @Test
    fun pasadoCogi_pastTenseStaysNull() {
        assertNull(analyze("cogí el bus ayer"))
    }

    @Test
    fun cogerSinObjeto_bareVerbStaysNull() {
        assertNull(analyze("coger"))
    }

    // --- Regresión: la envolvente c.613 ("recuérdame…") sigue gobernando ---

    @Test
    fun recuerdameCogerElBus_wrapperWinsTask() {
        val intent = analyze("recuérdame coger el bus")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}
