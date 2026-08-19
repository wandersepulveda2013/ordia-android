package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.712 (P1 olvido silencioso en captura pasiva — segunda clase de verbos
 * cotidianos de gestión, forma 2/14: "pedir <objeto>" descubierto por sonda
 * `tools/probe/ManagementVerbDiscoveryProbe.kt` c.711; una forma por ciclo,
 * doctrina anti-overreach): "pedir el taxi mañana" se DESCARTABA (analyze →
 * NULL) — una gestión cotidiana con fecha explícita se perdía silenciosamente
 * (P1). Fix: piso de TASK (ancla inicio/acuse/prefijo temporal — patrón
 * c.691…c.711) + plantilla de título "pedir X"→"Pedir X" (lección c.616: el
 * match arranca en el verbo). Kind decidido en este ciclo: TASK (en
 * deliberación contra ERRAND/APPOINTMENT — "pedir" es solicitar/encargar el
 * objeto; "pedir una cita" gestiona la solicitud, la cita en sí se captura
 * por su propia vía) — gobierna el objeto (taxi/cita/comida/presupuesto)
 * como acción de gestión. Anti-overreach: `\s+\w` exige objeto, `(?<!no )`
 * bloquea la negada, c.649 mantiene "quizá…"→NULL, el sustantivo "pedido" no
 * casa, suelto "pedir" no casa; el envolvente c.613 ("recuérdame…") sigue
 * gobernando por su plantilla genérica. Determinista (regex), sin random,
 * sin IA fingida.
 */
class ContextIntentEnginePedirFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: "pedir <objeto>" es una tarea clara ---

    @Test
    fun pedirElTaxiManana_capturesTaskWithDueAt() {
        val intent = analyze("pedir el taxi mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Pedir el taxi", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun pedirUnaCitaHoy_capturesTaskWithDueAt() {
        val intent = analyze("pedir una cita hoy")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Pedir una cita", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun pedirElPresupuestoElViernes_capturesTaskWithDueAt() {
        val intent = analyze("pedir el presupuesto el viernes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Pedir el presupuesto", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun pedirLaComidaSinFecha_capturesTaskWithoutDueAt() {
        val intent = analyze("pedir la comida")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Pedir la comida", intent.title)
    }

    @Test
    fun pedirTrasPrefijoDeAcuse_capturesTask() {
        val intent = analyze("vale, pedir el taxi mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Pedir el taxi", intent.title)
    }

    @Test
    fun pedirTrasPrefijoTemporal_capturesTask() {
        val intent = analyze("hoy pedir la cena")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Pedir la cena", intent.title)
        assertNotNull(intent.dueAt)
    }

    // --- Controles anti-overreach (deben permanecer NULL) ---

    @Test
    fun noPedirElTaxi_negatedStaysNull() {
        assertNull(analyze("no pedir el taxi mañana"))
    }

    @Test
    fun quizasPedir_hedgeStaysNull() {
        assertNull(analyze("quizá pedir el taxi mañana"))
    }

    @Test
    fun elPedidoLlegoAyer_nounDoesNotMatch() {
        assertNull(analyze("el pedido llegó ayer"))
    }

    @Test
    fun pedirSinObjeto_bareVerbStaysNull() {
        assertNull(analyze("pedir"))
    }

    // --- Regresión: la envolvente c.613 ("recuérdame…") sigue gobernando ---

    @Test
    fun recuerdamePedir_wrapperStillWins() {
        val intent = analyze("recuérdame pedir el taxi mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Pedir el taxi", intent.title)
    }
}
