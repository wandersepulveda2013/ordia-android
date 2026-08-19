package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.700 (P1 olvido silencioso en captura pasiva, ítem BACKLOG OPEN
 * clase-verbos c.692 — forma 5/6; una forma por ciclo, doctrina
 * anti-overreach): "confirmar <objeto>" ("confirmar la reserva esta
 * noche") se DESCARTABA (analyze → NULL). Sonda JVM fuente real PRE-fix
 * (`tools/probe/CommonVerbDiscoveryProbe.kt`, c.692, re-ejecutada en este
 * ciclo): "confirmar la reserva esta noche" → NULL. Fix: piso de TASK
 * (ancla inicio/acuse/prefijo temporal — patrón c.691…c.698) + plantilla
 * de título "confirmar X"→"Confirmar X" que despoja el acuse y el prefijo
 * temporal (lección c.616, match arranca en el verbo). Kind decidido en
 * este ciclo: TASK — "confirmar" es una acción de gestión sobre el objeto
 * (reserva/cita/asistencia/pedido), no un evento (MEETING) ni un aviso
 * (REMINDER); la cita/reserva en sí ya se captura por su propia vía.
 * Anti-overreach: `\s+\w` exige objeto, `(?<!no )` bloquea la negada,
 * c.649 mantiene "quizá…"→NULL, el sustantivo "confirmación" no casa.
 * Determinista (regex), sin random, sin IA fingida.
 */
class ContextIntentEngineConfirmarFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: "confirmar <objeto>" es una tarea clara ---

    @Test
    fun confirmarReservaEstaNoche_capturesTaskWithDueAt() {
        val intent = analyze("confirmar la reserva esta noche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Confirmar la reserva", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun confirmarCitaManana_capturesTaskWithDueAt() {
        val intent = analyze("confirmar la cita mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Confirmar la cita", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun confirmarAsistenciaElViernes_capturesTaskWithDueAt() {
        val intent = analyze("confirmar la asistencia el viernes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Confirmar la asistencia", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun confirmarPedidoSinFecha_capturesTaskWithoutDueAt() {
        val intent = analyze("confirmar el pedido")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Confirmar el pedido", intent.title)
    }

    @Test
    fun confirmarTrasPrefijoDeAcuse_capturesTask() {
        val intent = analyze("vale, confirmar la reserva mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Confirmar la reserva", intent.title)
    }

    @Test
    fun confirmarTrasPrefijoTemporal_capturesTask() {
        val intent = analyze("hoy confirmar la cita")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Confirmar la cita", intent.title)
        assertNotNull(intent.dueAt)
    }

    // --- Controles anti-overreach (deben permanecer NULL) ---

    @Test
    fun noConfirmarReserva_negatedStaysNull() {
        assertNull(analyze("no confirmar la reserva"))
    }

    @Test
    fun quizasConfirmar_conditionalStaysNull() {
        assertNull(analyze("quizá confirmar la reserva mañana"))
    }

    @Test
    fun confirmacionDeLaReserva_nounDoesNotMatch() {
        assertNull(analyze("la confirmación de la reserva llegó ayer"))
    }

    @Test
    fun confirmarSinObjeto_bareVerbStaysNull() {
        assertNull(analyze("confirmar"))
    }

    // --- Regresión: la envolvente c.613 sigue gobernando ---

    @Test
    fun tengoQueConfirmar_wrapperStillWins() {
        val intent = analyze("tengo que confirmar la reserva")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Confirmar la reserva", intent.title)
    }
}
