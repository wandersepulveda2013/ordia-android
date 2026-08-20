package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.721d (P1 olvido silencioso en captura pasiva — TERCERA clase de formas
 * cotidianas, forma 4/19: "redactar <objeto>" descubierto por sonda
 * `tools/probe/ThirdClassVerbDiscoveryProbe.kt` c.721; UNA forma por ciclo,
 * doctrina anti-overreach): "redactar el correo mañana" se DESCARTABA
 * (analyze → NULL) por ausencia de piso + keyword (misma clase de raíz que
 * c.691…c.721c). Fix: piso "redactar" en [hasStrongTaskImperative] (ancla
 * inicio/acuse/`TASK_FLOOR_TEMPORAL`, `(?<!no )`, `\s+\w` exige objeto) +
 * keyword "redactar" en TASK (paridad lockstep piso+keyword, lección c.713)
 * + plantilla de título "(redactar) X"→"Redactar X" (patrón c.691…c.721c;
 * lección c.616: el match arranca en el verbo). Kind decidido: TASK, en
 * deliberación contra NOTE — "redactar" es la acción de componer un texto
 * (correo/carta/documento/informe) como gestión pendiente del usuario; NOTE
 * es el contenido capturado, no la acción (criterio c.704). Anti-overreach:
 * objeto requerido, negada/duda/sustantivo "redacción"/past "redacté…"/
 * suelto "redactar" NULL; envolvente c.613 gobierna TASK. Determinista
 * (regex), sin random, sin IA fingida.
 */
class ContextIntentEngineRedactarFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: "redactar <objeto>" es una gestión clara ---

    @Test
    fun redactarElCorreoManana_capturesTaskWithDueAt() {
        val intent = analyze("redactar el correo mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Redactar el correo", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun redactarLaCarta_capturesTaskWithoutDueAt() {
        val intent = analyze("redactar la carta")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Redactar la carta", intent.title)
    }

    @Test
    fun redactarTrasAcuse_capturesTask() {
        val intent = analyze("ok, redactar el documento mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Redactar el documento", intent.title)
    }

    @Test
    fun redactarTrasPrefijoTemporal_capturesTask() {
        val intent = analyze("mañana redactar el informe")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Redactar el informe", intent.title)
    }

    // --- Controles anti-overreach (deben permanecer NULL) ---

    @Test
    fun noRedactarElCorreo_negatedStaysNull() {
        assertNull(analyze("no redactar el correo"))
    }

    @Test
    fun quizasRedactarElCorreo_hedgeStaysNull() {
        assertNull(analyze("quizá redactar el correo mañana"))
    }

    @Test
    fun sustantivoRedaccion_nounStaysNull() {
        assertNull(analyze("la redacción del correo fue ayer"))
    }

    @Test
    fun redacteElCorreo_pastStaysNull() {
        assertNull(analyze("redacté el correo ayer"))
    }

    @Test
    fun redactarSinObjeto_bareVerbStaysNull() {
        assertNull(analyze("redactar"))
    }

    // --- Regresión: la envolvente c.613 ("recuérdame…") sigue gobernando ---

    @Test
    fun recuerdameRedactarElCorreo_wrapperWinsTask() {
        val intent = analyze("recuérdame redactar el correo")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}
