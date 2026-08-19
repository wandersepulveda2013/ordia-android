package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.713 (P1 olvido silencioso en captura pasiva — segunda clase de verbos
 * cotidianos de gestión, forma 3/14: "solicitar <objeto>" descubierto por
 * sonda `tools/probe/ManagementVerbDiscoveryProbe.kt` c.711; una forma por
 * ciclo, doctrina anti-overreach): "solicitar la cita el lunes" se
 * DESCARTABA (analyze → NULL) — una gestión cotidiana con fecha explícita se
 * perdía silenciosamente (P1). Fix: piso de TASK (ancla inicio/acuse/prefijo
 * temporal — patrón c.691…c.712) + plantilla de título "solicitar X"→
 * "Solicitar X" (lección c.616: el match arranca en el verbo). Kind decidido
 * en este ciclo: TASK (en deliberación contra APPOINTMENT/ERRAND/CALL —
 * "solicitar" es gestionar la solicitud del objeto; la cita en sí se captura
 * por su propia vía) — gobierna el objeto (cita/prestación/permiso/
 * presupuesto) como acción de gestión. Anti-overreach: `\s+\w` exige objeto,
 * `(?<!no )` bloquea la negada, c.649 mantiene "quizá…"→NULL, el sustantivo
 * "solicitud" no casa, suelto "solicitar" no casa; el envolvente c.613
 * ("recuérdame…") sigue gobernando por su plantilla genérica. Determinista
 * (regex), sin random, sin IA fingida.
 */
class ContextIntentEngineSolicitarFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: "solicitar <objeto>" es una tarea clara ---

    @Test
    fun solicitarLaCitaElLunes_capturesTaskWithDueAt() {
        val intent = analyze("solicitar la cita el lunes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Solicitar la cita", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun solicitarElPresupuestoManana_capturesTaskWithDueAt() {
        val intent = analyze("solicitar el presupuesto mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Solicitar el presupuesto", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun solicitarLaPrestacionHoy_capturesTaskWithDueAt() {
        val intent = analyze("solicitar la prestación hoy")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Solicitar la prestación", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun solicitarElPermisoSinFecha_capturesTaskWithoutDueAt() {
        val intent = analyze("solicitar el permiso")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Solicitar el permiso", intent.title)
    }

    @Test
    fun solicitarTrasPrefijoDeAcuse_capturesTask() {
        val intent = analyze("vale, solicitar la cita mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Solicitar la cita", intent.title)
    }

    @Test
    fun solicitarTrasPrefijoTemporal_capturesTask() {
        val intent = analyze("mañana solicitar el presupuesto")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Solicitar el presupuesto", intent.title)
        assertNotNull(intent.dueAt)
    }

    // --- Controles anti-overreach (deben permanecer NULL) ---

    @Test
    fun noSolicitarLaCita_negatedStaysNull() {
        assertNull(analyze("no solicitar la cita el lunes"))
    }

    @Test
    fun quizasSolicitar_hedgeStaysNull() {
        assertNull(analyze("quizá solicitar la cita mañana"))
    }

    @Test
    fun laSolicitudLlegoAyer_nounDoesNotMatch() {
        assertNull(analyze("la solicitud de la cita llegó ayer"))
    }

    @Test
    fun solicitarSinObjeto_bareVerbStaysNull() {
        assertNull(analyze("solicitar"))
    }

    // --- Regresión: la envolvente c.613 ("recuérdame…") sigue gobernando ---

    @Test
    fun recuerdameSolicitar_wrapperStillWins() {
        val intent = analyze("recuérdame solicitar la cita mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Solicitar la cita", intent.title)
    }
}
