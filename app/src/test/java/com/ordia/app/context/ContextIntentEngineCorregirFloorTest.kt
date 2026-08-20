package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.721g (P1 olvido silencioso en captura pasiva — TERCERA clase de formas
 * cotidianas, forma 7/19: "corregir <objeto>" descubierto por sonda
 * `tools/probe/ThirdClassVerbDiscoveryProbe.kt` c.721; UNA forma por ciclo,
 * doctrina anti-overreach): "corregir el ensayo mañana" se DESCARTABA
 * (analyze → NULL) por ausencia de piso + keyword (misma clase de raíz que
 * c.691…c.721f). Fix: piso "corregir" en [hasStrongTaskImperative] (ancla
 * inicio/acuse/`TASK_FLOOR_TEMPORAL`, `(?<!no )`, `\s+\w` exige objeto) +
 * keyword "corregir" en TASK (paridad lockstep piso+keyword, lección c.713)
 * + plantilla de título "(corregir) X"→"Corregir X" (patrón c.691…c.721f;
 * lección c.616). Kind decidido: TASK, en deliberación contra EDUCATION —
 * "corregir" es la acción de marcar/revisar el objeto (ensayo/examen/
 * borrador) pendiente; EDUCATION es el dominio del estudio, no la gestión
 * de la revisión (criterio c.704). Anti-overreach: objeto requerido,
 * negada/duda/sustantivo "corrección"/pasado "corregí…"/suelto "corregir"
 * NULL; envolvente c.613 gobierna TASK. Determinista (regex), sin random,
 * sin IA fingida.
 */
class ContextIntentEngineCorregirFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: "corregir <objeto>" es una gestión clara ---

    @Test
    fun corregirElEnsayoManana_capturesTaskWithDueAt() {
        val intent = analyze("corregir el ensayo mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Corregir el ensayo", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun corregirLosExámenes_capturesTaskWithoutDueAt() {
        val intent = analyze("corregir los exámenes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Corregir los exámenes", intent.title)
    }

    @Test
    fun corregirTrasAcuse_capturesTask() {
        val intent = analyze("ok, corregir el borrador hoy")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Corregir el borrador", intent.title)
    }

    @Test
    fun corregirTrasPrefijoTemporal_capturesTask() {
        val intent = analyze("mañana corregir el ensayo")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Corregir el ensayo", intent.title)
    }

    // --- Controles anti-overreach (deben permanecer NULL) ---

    @Test
    fun noCorregirElEnsayo_negatedStaysNull() {
        assertNull(analyze("no corregir el ensayo"))
    }

    @Test
    fun quizasCorregirElEnsayo_hedgeStaysNull() {
        assertNull(analyze("quizá corregir el ensayo mañana"))
    }

    @Test
    fun sustantivoCorreccion_nounStaysNull() {
        assertNull(analyze("la corrección del ensayo fue ayer"))
    }

    @Test
    fun corregiElEnsayo_pastStaysNull() {
        assertNull(analyze("corregí el ensayo ayer"))
    }

    @Test
    fun corregirSinObjeto_bareVerbStaysNull() {
        assertNull(analyze("corregir"))
    }

    // --- Regresión: la envolvente c.613 ("recuérdame…") sigue gobernando ---

    @Test
    fun recuerdameCorregirElEnsayo_wrapperWinsTask() {
        val intent = analyze("recuérdame corregir el ensayo")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}
