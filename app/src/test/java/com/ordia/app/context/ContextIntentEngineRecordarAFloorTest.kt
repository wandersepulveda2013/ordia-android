package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.720 (P1 olvido silencioso en captura pasiva — segunda clase de verbos
 * cotidianos de gestión, forma 10/14, ÚLTIMA: "recordar a <persona>
 * <evento>" descubierta por sonda
 * `tools/probe/ManagementVerbDiscoveryProbe.kt` c.711; una forma por ciclo,
 * doctrina anti-overreach): "recordar a papá el almuerzo mañana" se
 * DESCARTABA (analyze → NULL) — una gestión cotidiana con fecha explícita
 * se perdía silenciosamente (P1). Fix: piso de TASK (ancla inicio/acuse/
 * prefijo temporal — patrón c.691…c.719) + plantilla de título
 * "recordar a X"→"Recordar a X" (lección c.616: el match arranca en el
 * verbo). **Kind decidido en este ciclo: TASK, en deliberación contra
 * REMINDER** — "recordar a alguien de algo" es la ACCIÓN del usuario
 * (avisar a papá), no un aviso automático a sí mismo (REMINDER puro es
 * "recuérdame"/"avísame", c.717). Anti-overreach: el objeto exigido tras
 * la preposición "a" (`a\s+\w`) bloquea la polisemia (recordar como
 * memoria propia "recordar mucho…" no casa), `(?<!no )` bloquea la
 * negada, c.649 mantiene "quizá…"→NULL, el sustantivo "recuerdo" no casa,
 * pasado "recordé…" no casa, suelto "recordar a" no casa; el envolvente
 * c.613 ("recuérdame…") sigue gobernando por su plantilla genérica.
 * Determinista (regex), sin random, sin IA fingida.
 */
class ContextIntentEngineRecordarAFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: "recordar a <persona> <evento>" es una tarea clara ---

    @Test
    fun recordarAPapaElAlmuerzoManana_capturesTaskWithDueAt() {
        val intent = analyze("recordar a papá el almuerzo mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Recordar a papá el almuerzo", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun recordarAMamaElPedido_capturesTaskViaTemporalAnchor() {
        val intent = analyze("mañana recordar a mamá el pedido")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Recordar a mamá el pedido", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun recuerdameRecordarAPapaElAlmuerzo_wrapperCapturesTask() {
        val intent = analyze("recuérdame recordar a papá el almuerzo mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Recordar a papá el almuerzo", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun recordarAMamaLaCenaKindLabelEsTarea() {
        val intent = analyze("recordar a mamá la cena mañana")
        assertNotNull(intent)
        assertEquals("Tarea", intent!!.kind.displayName)
    }

    @Test
    fun tengoQueRecordarAPapa_capturesTask() {
        val intent = analyze("tengo que recordar a papá el almuerzo mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Recordar a papá el almuerzo", intent.title)
    }

    // --- Anti-overreach (c.649, doctrina de una-forma-por-ciclo) ---

    @Test
    fun noRecordarAPapa_negatedIsNull() = assertNull(analyze("no recordar a papá el almuerzo mañana"))

    @Test
    fun quizaRecordarAPapa_doubtIsNull() = assertNull(analyze("quizá recordar a papá el almuerzo mañana"))

    @Test
    fun elRecuerdoDePapa_nounIsNull() = assertNull(analyze("el recuerdo de papá es bonito"))

    @Test
    fun recordeAPapa_pastTenseIsNull() = assertNull(analyze("recordé a papá el almuerzo ayer"))

    @Test
    fun recordarA_aloneNoObjectIsNull() = assertNull(analyze("recordar a"))

    @Test
    fun recordarMuchoEsSano_memoryUsageIsNull() = assertNull(analyze("recordar mucho es sano hoy"))
}
