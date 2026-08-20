package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.721f (P1 olvido silencioso en captura pasiva — TERCERA clase de formas
 * cotidianas, forma 6/19: "escribir <objeto>" descubierto por sonda
 * `tools/probe/ThirdClassVerbDiscoveryProbe.kt` c.721; UNA forma por ciclo,
 * doctrina anti-overreach): "escribir el informe el martes" se DESCARTABA
 * (analyze → NULL) por ausencia de piso + keyword (misma clase de raíz que
 * c.691…c.721e). Fix: piso "escribir" en [hasStrongTaskImperative] (ancla
 * inicio/acuse/`TASK_FLOOR_TEMPORAL`, `(?<!no )`, `\s+\w` exige objeto) +
 * keyword "escribir" en TASK (paridad lockstep piso+keyword, lección c.713)
 * + plantilla de título "(escribir) X"→"Escribir X" (patrón c.691…c.721e;
 * lección c.616: el match arranca en el verbo). Kind decidido: TASK, en
 * deliberación contra NOTE — "escribir" es la acción de componer el objeto
 * (informe/correo) como gestión pendiente; NOTE es el contenido capturado,
 * no la acción (criterio c.704). Anti-overreach: objeto requerido,
 * negada/duda/sustantivo "escritura"/pasado "escribí…"/suelto "escribir"
 * NULL; envolvente c.613 gobierna TASK. Determinista (regex), sin random,
 * sin IA fingida.
 */
class ContextIntentEngineEscribirFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: "escribir <objeto>" es una gestión clara ---

    @Test
    fun escribirElInformeElMartes_capturesTaskWithDueAt() {
        val intent = analyze("escribir el informe el martes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Escribir el informe", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun escribirLaCarta_capturesTaskWithoutDueAt() {
        val intent = analyze("escribir la carta")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Escribir la carta", intent.title)
    }

    @Test
    fun escribirTrasAcuse_capturesTask() {
        val intent = analyze("ok, escribir el correo mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Escribir el correo", intent.title)
    }

    @Test
    fun escribirTrasPrefijoTemporal_capturesTask() {
        val intent = analyze("mañana escribir el informe")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Escribir el informe", intent.title)
    }

    // --- Controles anti-overreach (deben permanecer NULL) ---

    @Test
    fun noEscribirElInforme_negatedStaysNull() {
        assertNull(analyze("no escribir el informe"))
    }

    @Test
    fun quizasEscribirElInforme_hedgeStaysNull() {
        assertNull(analyze("quizá escribir el informe el martes"))
    }

    @Test
    fun sustantivoEscritura_nounStaysNull() {
        assertNull(analyze("la escritura del informe fue ayer"))
    }

    @Test
    fun escribiElInforme_pastStaysNull() {
        assertNull(analyze("escribí el informe ayer"))
    }

    @Test
    fun escribirSinObjeto_bareVerbStaysNull() {
        assertNull(analyze("escribir"))
    }

    // --- Regresión: la envolvente c.613 ("recuérdame…") sigue gobernando ---

    @Test
    fun recuerdameEscribirElInforme_wrapperWinsTask() {
        val intent = analyze("recuérdame escribir el informe")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}
