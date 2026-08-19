package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guard de verbo de CANCELACIÓN envolvente sobre TASK (c.654).
 *
 * Defecto descubierto por probe JVM (evidencia c.654): "cancelar la cita del
 * dentista" era capturada como APPOINTMENT (0.44 → threshold) con el título
 * corrupto "Cita: del dentista" — dentro de los bonus-kinds APPOINTMENT/CALL
 * no había guard de envolvente para verbos de cancelación (c.653 cerró esa
 * rendija para vocals de enganche sólo). El piso + el bono de la cita ganaban
 * y la semántica de cancelación se perdía: overreach P1. Además, "cancelar la
 * cita" es el paradigma de acción envolvente (misma lección de diseño que
 * c.651/c.652: el contenido subordinado NO es una acción autónoma; "hay que
 * cancelarla" es la intención real).
 *
 * La solución añade "cancelar|anular" a [WRAPPER_PATTERN] y al piso de TASK
 * ([hasStrongTaskImperative], alineado con los templates de [extractTitle]:
 * "cancelar (.+)"→"Cancelar X"). La negación inmediata ("no cancelar la
 * cita"/"no anular…") queda bloqueada por lookbehind `(?<!no )` escopado al
 * alternativo de cancelación (anti-overreach c.614/c.616), porque capturar
 * lo opuesto a la intención del usuario es capta falsa.
 *
 * Cobertura:
 * - 3 verbos de cancelación envolventes (RED pre-fix → GREEN) con título
 *   del envolvente ("Cancelar la cita del dentista", "Anular la reserva").
 * - 2 positivamente envueltos con wrapper preexistente ("tengo que
 *   cancelar") → TASK.
 * - 2 controles de posición libre de bonus-kinds (sin cancelación NO
 *   cambian).
 * - 2 negaciones inmediatas: TASK bloqueada por lookbehind; APPOINTMENT
 *   abierto si el subkind es bonus ("no cancelar la cita"→APPOINTMENT),
 *   NULL si no hay subkind abierto ("no anular la reserva"→NULL).
 */
class ContextIntentEngineCancelWrapperTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(
                ContextCaptureSource.NOTIFICATION,
                raw,
                1723939200000L
            )
        )

    // --- Verbos de cancelación: el envolvente DEBE gobernar (RED → GREEN) ---

    @Test
    fun cancelarLaCitaDelDentistaStaysTask() {
        val intent = analyze("cancelar la cita del dentista")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cancelar la cita del dentista", intent.title)
    }

    @Test
    fun hayQueAnularLaReservaStaysTask() {
        val intent = analyze("hay que anular la reserva")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Anular la reserva", intent.title)
    }

    @Test
    fun anularLaSuscripcionStaysTask() {
        val intent = analyze("anular la suscripción del gimnasio")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Anular la suscripción del gimnasio", intent.title)
    }

    // --- Wrapper preexistente + verbo de cancelación → TASK ---

    @Test
    fun tengoQueCancelarLaReunionStaysTask() {
        val intent = analyze("tengo que cancelar la reunión")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cancelar la reunión", intent.title)
    }

    @Test
    fun recordameCancelarLaCitaStaysTask() {
        val intent = analyze("recuérdame cancelar la cita")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cancelar la cita", intent.title)
    }

    // --- Posiciones libres: los bonus-kinds siguen capturando ---

    @Test
    fun appointmentWithoutCancelStillCaptured() {
        val intent = analyze("cita con el dentista")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
    }

    @Test
    fun callWithoutCancelStillCaptured() {
        val intent = analyze("llamar al banco")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.CALL, intent!!.kind)
    }

    // --- Anti-overreach: la negación inmediata NO activa el wrapper ---

    @Test
    fun negatedCancelKeepsSubkind() {
        // "no cancelar la cita del dentista" → TASK bloqueada (lookbehind),
        // pero APPOINTMENT sigue capturando el contenido (la cita existe).
        val intent = analyze("no cancelar la cita del dentista")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
    }

    @Test
    fun negatedAnularIsDiscarded() {
        // Sin objeto con marca bonus-kind (reserva ≠ cita/llamada), la
        // negación inmediata ("no anular…") descarta TASK y deja ~NULL.
        val intent = analyze("no anular la reserva")
        assertNull(intent)
    }
}
