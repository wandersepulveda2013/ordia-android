package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.823 (P1 olvido silencioso en captura pasiva — verbo de encargos/comisiones
 * nunca con piso; una forma por ciclo, doctrina anti-overreach):
 * "mandar <objeto>" ("mandar el paquete el jueves") se DESCARTABA
 * (analyze → NULL). Sonda JVM fuente real PRE-fix (metodología de
 * `tools/probe/CaptureCoverageProbe.kt`, c.822; pool de dispersión por
 * epoch-day elige una forma por ciclo): "mandar el paquete el jueves",
 * "mandar el fax mañana", con acuse "vale, …", con prefijo temporal
 * "hoy …" y desnuda "mandar el paquete" → NULL; en cambio "tengo que
 * mandar el paquete el jueves" ya capturaba por la envolvente (TASK 0.45,
 * título limpio "Mandar el paquete"). Fix: piso de TASK (ancla inicio/
 * acuse/prefijo temporal — patrón c.691…c.822, hermano de "pedir" c.712) +
 * plantilla de título "mandar X"→"Mandar X" que despoja el acuse y el
 * prefijo temporal (lección c.616, match arranca en el verbo). Kind
 * decidido en este ciclo: TASK — "mandar" gobierna el OBJETO
 * (paquete/fax/documento) como acción de gestión; es hermano semántico
 * de "pedir"(c.712)/"enviar"(c.692), no ERRAND (sin desplazamiento a
 * destino físico). Anti-overreach: `\s+\w` exige objeto, `(?<!no )`
 * bloquea la negada, c.649 mantiene "quizá…"→NULL, el sustantivo
 * "mandado" no casa, la primera persona narrativa "te mando"/el pasado
 * "mandó" no casan, el verbo suelto no casa. Determinista (regex),
 * sin random, sin IA fingida.
 */
class ContextIntentEngineMandarFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: "mandar <objeto>" es una tarea clara ---

    @Test
    fun mandarPaqueteElJueves_capturesTaskWithDueAt() {
        val intent = analyze("mandar el paquete el jueves")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Mandar el paquete", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun mandarFaxManana_capturesTaskWithDueAt() {
        val intent = analyze("mandar el fax mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Mandar el fax", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun mandarFaxElLunes_capturesTaskWithDueAt() {
        val intent = analyze("mandar el fax el lunes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Mandar el fax", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun mandarPaqueteSinFecha_capturesTaskWithoutDueAt() {
        val intent = analyze("mandar el paquete")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Mandar el paquete", intent.title)
    }

    @Test
    fun mandarTrasPrefijoDeAcuse_capturesTask() {
        val intent = analyze("vale, mandar el paquete el jueves")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Mandar el paquete", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun mandarTrasPrefijoTemporal_capturesTask() {
        val intent = analyze("hoy mandar el paquete")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Mandar el paquete", intent.title)
        assertNotNull(intent.dueAt)
    }

    // --- Controles anti-overreach (deben permanecer NULL; verificados en
    // sonda PRE-fix) ---

    @Test
    fun teMandoElPaquete_firstPersonNarrativeStaysNull() {
        assertNull(analyze("te mando el paquete"))
    }

    @Test
    fun noMandarElPaquete_negatedStaysNull() {
        assertNull(analyze("no mandar el paquete mañana"))
    }

    @Test
    fun quizasMandar_conditionalStaysNull() {
        assertNull(analyze("quizá mandar el paquete mañana"))
    }

    @Test
    fun elMandadoDelSupermercado_nounDoesNotMatch() {
        assertNull(analyze("el mandado del supermercado"))
    }

    @Test
    fun mandoElPaqueteAyer_pastNarrativeStaysNull() {
        assertNull(analyze("mandó el paquete ayer"))
    }

    @Test
    fun mandarSinObjeto_bareVerbStaysNull() {
        assertNull(analyze("mandar"))
    }

    // --- Regresión: la envolvente c.613 sigue gobernando (PRE-fix:
    // TASK 0.45, título limpio) ---

    @Test
    fun tengoQueMandar_wrapperStillWins() {
        val intent = analyze("tengo que mandar el paquete el jueves")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Mandar el paquete", intent.title)
        assertNotNull(intent.dueAt)
    }
}
