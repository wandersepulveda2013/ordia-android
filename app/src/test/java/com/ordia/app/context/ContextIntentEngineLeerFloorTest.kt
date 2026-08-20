package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.721e (P1 olvido silencioso en captura pasiva — TERCERA clase de formas
 * cotidianas, forma 5/19: "leer <objeto>" descubierto por sonda
 * `tools/probe/ThirdClassVerbDiscoveryProbe.kt` c.721; UNA forma por ciclo,
 * doctrina anti-overreach): "leer el contrato hoy" se DESCARTABA
 * (analyze → NULL) por ausencia de piso + keyword (misma clase de raíz que
 * c.691…c.721d). Fix: piso "leer" en [hasStrongTaskImperative] (ancla
 * inicio/acuse/`TASK_FLOOR_TEMPORAL`, `(?<!no )`, `\s+\w` exige objeto) +
 * keyword "leer" en TASK (paridad lockstep piso+keyword, lección c.713)
 * + plantilla de título "(leer) X"→"Leer X" (patrón c.691…c.721d;
 * lección c.616: el match arranca en el verbo). Kind decidido: TASK, en
 * deliberación contra NOTE — "leer" es la acción de consumir el objeto
 * (contrato/documento/libro) como gestión pendiente; NOTE es contenido
 * capturado, no acción (criterio c.704). Anti-overreach: objeto requerido,
 * negada/duda/sustantivo "lectura"/pasado "leí…"/suelto "leer" NULL;
 * envolvente c.613 gobierna TASK. Determinista (regex), sin random, sin IA
 * fingida.
 */
class ContextIntentEngineLeerFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: "leer <objeto>" es una gestión clara ---

    @Test
    fun leerElContratoHoy_capturesTaskWithDueAt() {
        val intent = analyze("leer el contrato hoy")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Leer el contrato", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun leerElArticulo_capturesTaskWithoutDueAt() {
        val intent = analyze("leer el artículo")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Leer el artículo", intent.title)
    }

    @Test
    fun leerTrasAcuse_capturesTask() {
        val intent = analyze("ok, leer el documento mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Leer el documento", intent.title)
    }

    @Test
    fun leerTrasPrefijoTemporal_capturesTask() {
        val intent = analyze("mañana leer el contrato")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Leer el contrato", intent.title)
    }

    // --- Controles anti-overreach (deben permanecer NULL) ---

    @Test
    fun noLeerElContrato_negatedStaysNull() {
        assertNull(analyze("no leer el contrato"))
    }

    @Test
    fun quizasLeerElContrato_hedgeStaysNull() {
        assertNull(analyze("quizá leer el contrato hoy"))
    }

    @Test
    fun sustantivoLectura_nounStaysNull() {
        assertNull(analyze("la lectura del contrato fue ayer"))
    }

    @Test
    fun leiElContrato_pastStaysNull() {
        assertNull(analyze("leí el contrato ayer"))
    }

    @Test
    fun leerSinObjeto_bareVerbStaysNull() {
        assertNull(analyze("leer"))
    }

    // --- Regresión: la envolvente c.613 ("recuérdame…") sigue gobernando ---

    @Test
    fun recuerdameLeerElContrato_wrapperWinsTask() {
        val intent = analyze("recuérdame leer el contrato")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}
