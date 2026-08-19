package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.709 (P1 olvido silencioso en captura pasiva, ítem BACKLOG OPEN
 * clase-verbos c.692 — forma 7/8; una forma por ciclo, doctrina
 * anti-overreach): "reservar <objeto>" ("reservar el restaurante el
 * sábado") se DESCARTABA (analyze → NULL). Sonda JVM fuente real PRE-fix
 * (`tools/probe/CommonVerbDiscoveryProbe.kt`, c.692, re-ejecutada en este
 * ciclo): "reservar el restaurante el sábado" → NULL. Fix: piso de TASK
 * (ancla inicio/acuse/prefijo temporal — patrón c.691…c.708) + plantilla
 * de título "reservar X"→"Reservar X" (lección c.616: el match arranca en
 * el verbo). Kind decidido en este ciclo: TASK — misma argumentación que
 * "confirmar/renovar/imprimir": "reservar" gobierna el OBJETO
 * (restaurante/mesa/hotel/vuelo) como acción de gestión; el EVENT/cita en
 * sí se captura por su propia vía. Anti-overreach: `\s+\w` exige objeto,
 * `(?<!no )` bloquea la negada, c.649 mantiene "quizá…"→NULL, el
 * sustantivo "reserva" no casa, suelto "reservar" no casa.
 * Determinista (regex), sin random, sin IA fingida.
 */
class ContextIntentEngineReservarFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: "reservar <objeto>" es una tarea clara ---

    @Test
    fun reservarRestauranteElSabado_capturesTaskWithDueAt() {
        val intent = analyze("reservar el restaurante el sábado")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Reservar el restaurante", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun reservarMesaManana_capturesTaskWithDueAt() {
        val intent = analyze("reservar una mesa mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Reservar una mesa", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun reservarHotelElViernes_capturesTaskWithDueAt() {
        val intent = analyze("reservar el hotel el viernes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Reservar el hotel", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun reservarVueloSinFecha_capturesTaskWithoutDueAt() {
        val intent = analyze("reservar el vuelo")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Reservar el vuelo", intent.title)
    }

    @Test
    fun reservarTrasPrefijoDeAcuse_capturesTask() {
        val intent = analyze("vale, reservar la mesa mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Reservar la mesa", intent.title)
    }

    @Test
    fun reservarTrasPrefijoTemporal_capturesTask() {
        val intent = analyze("hoy reservar el hotel")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Reservar el hotel", intent.title)
        assertNotNull(intent.dueAt)
    }

    // --- Controles anti-overreach (deben permanecer NULL) ---

    @Test
    fun noReservarHotel_negatedStaysNull() {
        assertNull(analyze("no reservar el hotel"))
    }

    @Test
    fun quizasReservar_conditionalStaysNull() {
        assertNull(analyze("quizá reservar el restaurante mañana"))
    }

    @Test
    fun reservaDelRestaurante_nounDoesNotMatch() {
        assertNull(analyze("la reserva del restaurante es mañana"))
    }

    @Test
    fun reservarSinObjeto_bareVerbStaysNull() {
        assertNull(analyze("reservar"))
    }

    // --- Regresión: la envolvente c.613 sigue gobernando ---

    @Test
    fun tengoQueReservar_wrapperStillWins() {
        val intent = analyze("tengo que reservar el hotel")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Reservar el hotel", intent.title)
    }
}
