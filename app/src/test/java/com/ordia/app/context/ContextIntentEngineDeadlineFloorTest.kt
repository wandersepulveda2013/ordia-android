package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Piso de marcadores inequívocos de FECHA LÍMITE (c.654).
 *
 * Defecto descubierto por probe JVM (evidencia c.654): el kind DEADLINE no
 * podía disparar el umbral de captura pasiva. Sin fecha/hora, el marcador
 * inequívoco ("deadline"/"fecha límite"/"vencimiento") sumaba 0.12 (palabra
 * clave) + bonos específicos débiles ≈ 0.22–0.24 (< [MINIMUM_CONFIDENCE]) →
 * NULL. "deadline: enviar el informe"/"fecha límite: enviar el informe" se
 * DESCARTABAN: olvido silencioso P1 (una fecha tope fallida tiene coste real,
 * como un pago; misma clase que c.626/c.630 para compra/pago).
 *
 * La solución centraliza el patrón de activación en [DEADLINE_FLOORS] (misma
 * lección c.648/c.652: guards y activadores no deben diverger) y lo usa en
 * [hasStrongDeadlineImperative] (piso → [MINIMUM_CONFIDENCE]) y en
 * [WRAPPABLE_PATTERNS] (guard [imperativeIsWrapped]). El guard de envolvente
 * intercepta si un wrapper precede al marcador ("recuérdame la fecha límite"
 * → TASK, no DEADLINE). Marcadores genéricos ("tope"/"límite"/"finaliza") NO
 * activan piso: anti-overreach. El lookbehind `(?<!no )` bloquea la negación
 * inmediata. Título limpio via [extractTitle] rama DEADLINE (c.654): quita la
 * etiqueta del marcador ("deadline:" → "Enviar el informe").
 *
 * Cobertura:
 * - 3 marcadores inequívocos (RED pre-fix → GREEN), título limpio.
 * - 1 envolvente gana TASK ("recuérdame la fecha límite").
 * - 1 negación inmediata NO dispara piso ("no deadline: ...").
 * - 1 marcador genérico sin piso ("tope: ...") → NULL.
 */
class ContextIntentEngineDeadlineFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(
                ContextCaptureSource.NOTIFICATION,
                raw,
                1723939200000L
            )
        )

    // --- Marcadores inequívocos: el piso DEBE disparar (RED → GREEN) ---

    @Test
    fun deadlineLabelTriggersDeadlineKind() {
        val intent = analyze("deadline: enviar el informe")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.DEADLINE, intent!!.kind)
        assertEquals("Enviar el informe", intent.title)
    }

    @Test
    fun fechaLimiteLabelTriggersDeadlineKind() {
        val intent = analyze("fecha límite: enviar el informe")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.DEADLINE, intent!!.kind)
        assertEquals("Enviar el informe", intent.title)
    }

    @Test
    fun vencimientoLabelTriggersDeadlineKind() {
        val intent = analyze("vencimiento: enviar el informe")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.DEADLINE, intent!!.kind)
        assertEquals("Enviar el informe", intent.title)
    }

    // --- Envolvente: TASK gobierna al marcador subordinado ---

    @Test
    fun recordameLaFechaLimiteStaysTask() {
        val intent = analyze("recuérdame la fecha límite de entrega")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    // --- Anti-overreach: negación y marcadores genéricos NO activan piso ---

    @Test
    fun negatedDeadlineIsDiscarded() {
        val intent = analyze("no deadline: enviar el informe")
        assertNull(intent)
    }

    @Test
    fun genericTopeIsDiscarded() {
        val intent = analyze("tope: enviar el informe")
        assertNull(intent)
    }
}
