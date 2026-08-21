package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.837 — cobertura ADITIVA (no duplicada) del piso enclítico de equipaje.
 *
 * Registro honesto de colisión: este run implementó de forma independiente la
 * misma unidad («hacerme la maleta», candidata 1/4 de la sonda persistida
 * `tools/probe/SixthClassEncliticProbe.kt` c.834 — `(?:me|nos)?` sobre
 * hacer|preparar, 18 tests, TDD RED exacto 8/18 → GREEN 18/18, suite OK
 * 5295 sobre base integrada `2bdcae4`) pero el hermano remoto llegó ANTES al
 * push con el mismo fix y cobertura SUPERIOR (c.836 `6c5a7cd`+`e284875`:
 * `(?:me|te|se|nos)?` sobre los 3 verbos, incluye «hacerte/hacerse la
 * maleta» — forma que mi versión difería como candidata separada). Por la
 * doctrina anti-colisión (precedente c.834: un agente NO sobrescribe trabajo
 * válido de otro; se descarta lo duplicado y se conserva sólo lo NO
 * duplicado) se descartaron mi cambio de motor y los 12 tests duplicados, y
 * se conservan aquí únicamente los 6 casos que el hermano no cubrió:
 *
 * - anclas de posición sobre la forma enclítica: acuse «vale, hacerme…» y
 *   prefijo temporal «hoy hacerme…» (el piso se ancla a ^/ACK/temporal —
 *   lección c.616: piso y título en lockstep);
 * - pronombre «me» sobre «preparar» («prepararme la maleta el sábado» — el
 *   hermano cubrió te/nos sobre preparar);
 * - «nos» sobre «hacer» con objeto plural («hacernos las maletas»);
 * - guard de verbo suelto sin objeto («hacerme» → NULL: el piso exige el
 *   ancla de objeto `maletas?`, anti-overreach);
 * - ruta envolvente («recuérdame hacerme la maleta mañana» → TASK 0.66,
 *   título limpio con pronombre — regresión de la asimetría de ruta
 *   documentada en la sonda PRE `/tmp/probe835/HacermePreProbe.kt`).
 *
 * NOTA de diseño (deliberada, del hermano c.836): «meter» TAMBIÉN admite el
 * enclítico («meterme la maleta» captura — forma no idiomática pero benigna:
 * capturar texto no idiomático no causa daño) y «hacerse la maleta» (3ª
 * persona reflexiva) captura. Mis dos guards contrarios se descartaron con
 * la implementación duplicada.
 */
class ContextIntentEngineHacermeMaletaFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Anclas de posición sobre la forma enclítica (no cubiertas por c.836) ---

    @Test
    fun ackValeHacermeLaMaleta_ackAnchorCapturesTaskWithCleanTitle() {
        val intent = analyze("vale, hacerme la maleta")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacerme la maleta", intent.title)
    }

    @Test
    fun hoyHacermeLaMaleta_temporalPrefixAnchorCapturesTask() {
        val intent = analyze("hoy hacerme la maleta")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacerme la maleta", intent.title)
        assertNotNull(intent.dueAt)
    }

    // --- Combinaciones pronombre×verbo×plural no cubiertas por c.836 ---

    @Test
    fun prepararmeLaMaletaElSabado_meOnPrepararCapturesTaskWithDueAt() {
        val intent = analyze("prepararme la maleta el sábado")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Prepararme la maleta", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun hacernosLasMaletasEstaNoche_nosOnHacerPluralCapturesTask() {
        val intent = analyze("hacernos las maletas esta noche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacernos las maletas", intent.title)
        assertNotNull(intent.dueAt)
    }

    // --- Guard no cubierto por c.836: el enclítico sin objeto no captura ---

    @Test
    fun hacermeSuelto_encliticVerbAloneStaysNull() {
        assertNull(analyze("hacerme"))
    }

    // --- Regresión de ruta: la envolvente capturaba ya y sigue intacta ---

    @Test
    fun recuerdameHacermeLaMaleta_wrapperStillCapturesTaskWithCleanTitle() {
        val intent = analyze("recuérdame hacerme la maleta mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacerme la maleta", intent.title)
        assertNotNull(intent.dueAt)
    }
}
