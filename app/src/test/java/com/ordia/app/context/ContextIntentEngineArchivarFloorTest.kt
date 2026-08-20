package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.723 (P1 olvido silencioso en captura pasiva — TERCERA clase de formas
 * cotidianas, forma 10/19: "archivar <objeto>" descubierto por sonda
 * `tools/probe/ThirdClassVerbDiscoveryProbe.kt` c.721; UNA forma por ciclo,
 * doctrina anti-overreach): "archivar el contrato el viernes" se DESCARTABA
 * (analyze → NULL) por ausencia de piso + keyword. Fix: piso "archivar" en
 * [hasStrongTaskImperative] (ancla inicio/acuse/`TASK_FLOOR_TEMPORAL`,
 * `(?<!no )`, `\s+\w` exige objeto) + keyword "archivar" en TASK (paridad
 * lockstep piso+keyword, lección c.713) + plantilla "(archivar) X"→"Archivar X"
 * (patrón c.691…c.722; lección c.616). Kind decidido: TASK, en deliberación
 * contra NOTE — acción de depositar el objeto (contrato/documento/factura) en
 * su archivo; NOTE es el contenido capturado, no la acción (criterio c.704).
 * Anti-overreach: objeto requerido, negada/duda/sustantivo "archivo"/
 * pasado "archivé…"/suelto "archivar" NULL; envolvente c.613 gobierna TASK.
 * Determinista (regex), sin random, sin IA fingida.
 */
class ContextIntentEngineArchivarFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: "archivar <objeto>" es una gestión clara ---

    @Test
    fun archivarElContratoViernes_capturesTaskWithDueAt() {
        val intent = analyze("archivar el contrato el viernes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Archivar el contrato", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun archivarLaFactura_capturesTaskWithoutDueAt() {
        val intent = analyze("archivar la factura")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Archivar la factura", intent.title)
    }

    @Test
    fun archivarTrasAcuse_capturesTask() {
        val intent = analyze("ok, archivar el contrato el viernes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Archivar el contrato", intent.title)
    }

    @Test
    fun archivarTrasPrefijoTemporal_capturesTask() {
        val intent = analyze("mañana archivar el documento")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Archivar el documento", intent.title)
    }

    // --- Controles anti-overreach (deben permanecer NULL) ---

    @Test
    fun noArchivarElContrato_negatedStaysNull() {
        assertNull(analyze("no archivar el contrato"))
    }

    @Test
    fun quizasArchivarElContrato_hedgeStaysNull() {
        assertNull(analyze("quizá archivar el contrato el viernes"))
    }

    @Test
    fun sustantivoArchivo_nounStaysNull() {
        assertNull(analyze("el archivo del contrato fue ayer"))
    }

    @Test
    fun archiveElContrato_pastStaysNull() {
        assertNull(analyze("archivé el contrato ayer"))
    }

    @Test
    fun archivarSinObjeto_bareVerbStaysNull() {
        assertNull(analyze("archivar"))
    }

    // --- Regresión: la envolvente c.613 ("recuérdame…") sigue gobernando ---

    @Test
    fun recuerdameArchivarElContrato_wrapperWinsTask() {
        val intent = analyze("recuérdame archivar el contrato")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}
