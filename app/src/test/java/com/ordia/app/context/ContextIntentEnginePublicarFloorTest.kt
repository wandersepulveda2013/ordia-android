package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.719 (P1 olvido silencioso en captura pasiva — segunda clase de verbos
 * cotidianos de gestión, forma 9/14: "publicar <contenido>" descubierto por
 * sonda `tools/probe/ManagementVerbDiscoveryProbe.kt` c.711; una forma por
 * ciclo, doctrina anti-overreach): "publicar las fotos mañana" se DESCARTABA
 * (analyze → NULL) — una gestión cotidiana con fecha explícita se perdía
 * silenciosamente (P1). Fix: piso de TASK (ancla inicio/acuse/prefijo
 * temporal — patrón c.691…c.716) + plantilla de título "publicar X"→
 * "Publicar X" (lección c.616: el match arranca en el verbo). Kind decidido
 * en este ciclo: TASK (en deliberación contra NOTE/REMINDER — "publicar" es
 * una acción de gestión sobre un contenido (fotos/vídeo/estado/entrada), no
 * una nota ni un aviso) — gobierna el objeto (las fotos/el vídeo/
 * la entrada) como acción de gestión. Anti-overreach: `\s+\w` exige objeto,
 * `(?<!no )` bloquea la negada, c.649 mantiene "quizá…"→NULL, el sustantivo
 * "publicación" no casa, suelto "publicar" no casa; el envolvente c.613
 * ("recuérdame…") sigue gobernando por su plantilla genérica. Determinista
 * (regex), sin random, sin IA fingida.
 */
class ContextIntentEnginePublicarFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: "publicar <contenido>" es una tarea clara ---

    @Test
    fun publicarLasFotosManana_capturesTaskWithDueAt() {
        val intent = analyze("publicar las fotos mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Publicar las fotos", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun publicarElVideoHoy_capturesTaskViaTemporalAnchor() {
        val intent = analyze("hoy publicar el vídeo")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Publicar el vídeo", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun recordamePublicarLasFotosManana_wrapperCapturesTask() {
        val intent = analyze("recuérdame publicar las fotos mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Publicar las fotos", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun publicarLaEntradaAhoraKindLabelEsTarea() {
        val intent = analyze("publicar la entrada mañana")
        assertNotNull(intent)
        assertEquals("Tarea", intent!!.kind.displayName)
    }

    @Test
    fun publicarYBotonCaptureIsaTarea() {
        val intent = analyze("tengo que publicar las fotos mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Publicar las fotos", intent.title)
    }

    // --- Anti-overreach (c.649, doctrina de una-forma-por-ciclo) ---

    @Test
    fun noPublicar_negatedIsNull() = assertNull(analyze("no publicar las fotos"))

    @Test
    fun quizPublicar_doubtIsNull() = assertNull(analyze("quizá publicar las fotos mañana"))

    @Test
    fun publico_presentFirstPersonIsNull() = assertNull(analyze("¿publico las fotos mañana?"))

    @Test
    fun publicaron_pastTenseIsNull() = assertNull(analyze("publicaron las fotos ayer"))

    @Test
    fun publicacion_nounIsNull() = assertNull(analyze("la publicación salió bien"))

    @Test
    fun publicarAloneNoObjectIsNull() = assertNull(analyze("mañana publicar"))
}
