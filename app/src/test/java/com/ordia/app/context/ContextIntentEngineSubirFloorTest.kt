package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.724 (P1 olvido silencioso en captura pasiva — TERCERA clase de formas
 * cotidianas, forma 11/19: "subir <objeto>" descubierto por sonda
 * `tools/probe/ThirdClassVerbDiscoveryProbe.kt` c.721; UNA forma por ciclo,
 * doctrina anti-overreach): "subir el documento hoy" se DESCARTABA
 * (analyze → NULL) por ausencia de piso + keyword. Fix: piso "subir" en
 * [hasStrongTaskImperative] (ancla inicio/acuse/`TASK_FLOOR_TEMPORAL`,
 * `(?<!no )`, `\s+\w` exige objeto) + keyword "subir" en TASK (paridad
 * lockstep piso+keyword, lección c.713) + plantilla "(subir) X"→"Subir X"
 * (patrón c.691…c.723; lección c.616). Kind decidido: TASK, en deliberación
 * contra TRAVEL/NOTE — acción de transferir el objeto (documento/archivo/
 * foto) a su destino; NOTE es contenido, no acción (criterio c.704).
 * Anti-overreach: objeto requerido, negada/duda/sustantivo "subida"/
 * pasado "subí…"/suelto "subir" NULL; envolvente c.613 gobierna TASK.
 * Determinista (regex), sin random, sin IA fingida.
 */
class ContextIntentEngineSubirFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: "subir <objeto>" es una gestión clara ---

    @Test
    fun subirElDocumentoHoy_capturesTaskWithDueAt() {
        val intent = analyze("subir el documento hoy")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Subir el documento", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun subirElContrato_capturesTaskWithoutDueAt() {
        val intent = analyze("subir el contrato")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Subir el contrato", intent.title)
    }

    @Test
    fun subirTrasAcuse_capturesTask() {
        val intent = analyze("ok, subir el documento hoy")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Subir el documento", intent.title)
    }

    @Test
    fun subirTrasPrefijoTemporal_capturesTask() {
        val intent = analyze("mañana subir el archivo")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Subir el archivo", intent.title)
    }

    // --- Controles anti-overreach (deben permanecer NULL) ---

    @Test
    fun noSubirElDocumento_negatedStaysNull() {
        assertNull(analyze("no subir el documento"))
    }

    @Test
    fun quizasSubirElDocumento_hedgeStaysNull() {
        assertNull(analyze("quizá subir el documento hoy"))
    }

    @Test
    fun sustantivoSubida_nounStaysNull() {
        assertNull(analyze("la subida del documento fue ayer"))
    }

    @Test
    fun subiElDocumento_pastStaysNull() {
        assertNull(analyze("subí el documento ayer"))
    }

    @Test
    fun subirSinObjeto_bareVerbStaysNull() {
        assertNull(analyze("subir"))
    }

    // --- Regresión: la envolvente c.613 ("recuérdame…") sigue gobernando ---

    @Test
    fun recuerdameSubirElDocumento_wrapperWinsTask() {
        val intent = analyze("recuérdame subir el documento")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}
