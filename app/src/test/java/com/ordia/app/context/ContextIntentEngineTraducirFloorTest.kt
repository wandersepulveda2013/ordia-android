package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.721h (P1 olvido silencioso en captura pasiva — TERCERA clase de formas
 * cotidianas, forma 8/19: "traducir <objeto>" descubierto por sonda
 * `tools/probe/ThirdClassVerbDiscoveryProbe.kt` c.721; UNA forma por ciclo,
 * doctrina anti-overreach): "traducir el documento mañana" se DESCARTABA
 * (analyze → NULL) por ausencia de piso + keyword. Fix: piso "traducir" en
 * [hasStrongTaskImperative] (ancla inicio/acuse/`TASK_FLOOR_TEMPORAL`,
 * `(?<!no )`, `\s+\w` exige objeto) + keyword "traducir" en TASK (paridad
 * lockstep piso+keyword, lección c.713) + plantilla "(traducir) X"→"Traducir X"
 * (patrón c.691…c.721g; lección c.616). Kind decidido: TASK, en deliberación
 * contra EDUCATION — acción de gestión sobre un documento pendiente
 * (documento/curriculum/carta), no el dominio del estudio (criterio c.704).
 * Anti-overreach: objeto requerido, negada/duda/sustantivo "traducción"/
 * pasado "traducí…"/suelto "traducir" NULL; envolvente c.613 gobierna TASK.
 * Determinista (regex), sin random, sin IA fingida.
 */
class ContextIntentEngineTraducirFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: "traducir <objeto>" es una gestión clara ---

    @Test
    fun traducirElDocumentoManana_capturesTaskWithDueAt() {
        val intent = analyze("traducir el documento mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Traducir el documento", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun traducirLaCarta_capturesTaskWithoutDueAt() {
        val intent = analyze("traducir la carta")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Traducir la carta", intent.title)
    }

    @Test
    fun traducirTrasAcuse_capturesTask() {
        val intent = analyze("ok, traducir el documento mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Traducir el documento", intent.title)
    }

    @Test
    fun traducirTrasPrefijoTemporal_capturesTask() {
        val intent = analyze("mañana traducir el documento")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Traducir el documento", intent.title)
    }

    // --- Controles anti-overreach (deben permanecer NULL) ---

    @Test
    fun noTraducirElDocumento_negatedStaysNull() {
        assertNull(analyze("no traducir el documento"))
    }

    @Test
    fun quizasTraducirElDocumento_hedgeStaysNull() {
        assertNull(analyze("quizá traducir el documento mañana"))
    }

    @Test
    fun sustantivoTraduccion_nounStaysNull() {
        assertNull(analyze("la traducción del documento fue ayer"))
    }

    @Test
    fun traduciElDocumento_pastStaysNull() {
        assertNull(analyze("traduje el documento ayer"))
    }

    @Test
    fun traducirSinObjeto_bareVerbStaysNull() {
        assertNull(analyze("traducir"))
    }

    // --- Regresión: la envolvente c.613 ("recuérdame…") sigue gobernando ---

    @Test
    fun recuerdameTraducirElDocumento_wrapperWinsTask() {
        val intent = analyze("recuérdame traducir el documento")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}
