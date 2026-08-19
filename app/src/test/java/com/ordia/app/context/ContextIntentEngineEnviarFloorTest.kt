package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.692 (P1 olvido silencioso en captura pasiva, ítem BACKLOG OPEN
 * descubierto c.692 con `tools/probe/CommonVerbDiscoveryProbe.kt` —
 * clase de verbos cotidianos sin piso; una forma por ciclo, doctrina
 * anti-overreach): el verbo "enviar" con objeto ("enviar el informe
 * mañana") se DESCARTABA (analyze → NULL): ningún piso lo cubre y el
 * bono temporal no alcanza el umbral. Sonda JVM fuente real PRE-fix:
 * 5 formas de captura → NULL; controles ya NULL; envolvente c.613 →
 * TASK. Mismo patrón que c.691 ("revisar").
 *
 * Fix: piso de TASK (ancla inicio/acuse, patrón SHOPPING/PAYMENT
 * c.651) + plantilla de título "enviar X"→"Enviar X" que despoja el
 * acuse (alineación piso↔título, lección c.616). Anti-overreach:
 * `\s+\w` exige objeto, `(?<!no )` bloquea la negada, c.649 mantiene
 * "quizá…"→NULL, el sustantivo "envío" no casa. Determinista (regex),
 * sin random, sin IA fingida.
 */
class ContextIntentEngineEnviarFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: "enviar <objeto>" es una tarea clara ---

    @Test
    fun enviarInformeManana_capturesTaskWithDueAt() {
        val intent = analyze("enviar el informe mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Enviar el informe", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun enviarCorreoElLunes_capturesTaskWithDueAt() {
        val intent = analyze("enviar el correo el lunes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Enviar el correo", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun enviarDocumentosALas5_capturesTaskWithDueAt() {
        val intent = analyze("enviar los documentos a las 5")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Enviar los documentos", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun enviarFotosSinFecha_capturesTaskWithoutDueAt() {
        val intent = analyze("enviar las fotos")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Enviar las fotos", intent.title)
    }

    @Test
    fun enviarTrasPrefijoDeAcuse_capturesTask() {
        val intent = analyze("vale, enviar el informe mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Enviar el informe", intent.title)
    }

    // --- Controles anti-overreach (deben permanecer NULL) ---

    @Test
    fun noEnviarInforme_negatedStaysNull() {
        assertNull(analyze("no enviar el informe"))
    }

    @Test
    fun quizasEnviar_conditionalStaysNull() {
        assertNull(analyze("quizá enviar el correo mañana"))
    }

    @Test
    fun envioDelPaquete_nounDoesNotMatch() {
        assertNull(analyze("el envío del paquete es mañana"))
    }

    @Test
    fun enviarSinObjeto_bareVerbStaysNull() {
        assertNull(analyze("enviar"))
    }

    // --- Regresión: la envolvente c.613 sigue gobernando ---

    @Test
    fun tengoQueEnviar_wrapperStillWins() {
        val intent = analyze("tengo que enviar el informe")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Enviar el informe", intent.title)
    }
}
