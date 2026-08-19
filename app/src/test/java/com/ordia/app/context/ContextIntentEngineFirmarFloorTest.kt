package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.696 (P1 olvido silencioso en captura pasiva, ítem BACKLOG OPEN
 * clase-verbos c.692 — forma 3/6 restante; una forma por ciclo, doctrina
 * anti-overreach): "firmar <objeto>" ("firmar el contrato el jueves")
 * se DESCARTABA (analyze → NULL). Sonda JVM fuente real PRE-fix
 * (`tools/probe/CommonVerbDiscoveryProbe.kt`, c.692): "firmar el contrato
 * el jueves" → NULL. Fix: piso de TASK (ancla inicio/acuse/prefijo
 * temporal — patrón c.691…c.694) + plantilla de título "firmar X"→
 * "Firmar X" que despoja el acuse y el prefijo temporal (lección c.616,
 * match arranca en el verbo). Anti-overreach: `\s+\w` exige objeto,
 * `(?<!no )` bloquea la negada, c.649 mantiene "quizá…"→NULL, el
 * sustantivo "firma" no casa. Determinista (regex), sin random, sin IA
 * fingida.
 */
class ContextIntentEngineFirmarFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: "firmar <objeto>" es una tarea clara ---

    @Test
    fun firmarContratoElJueves_capturesTaskWithDueAt() {
        val intent = analyze("firmar el contrato el jueves")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Firmar el contrato", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun firmarPapelesManana_capturesTaskWithDueAt() {
        val intent = analyze("firmar los papeles mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Firmar los papeles", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun firmarDocumentoALas9_capturesTaskWithDueAt() {
        val intent = analyze("firmar el documento a las 9")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Firmar el documento", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun firmarFormularioSinFecha_capturesTaskWithoutDueAt() {
        val intent = analyze("firmar el formulario")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Firmar el formulario", intent.title)
    }

    @Test
    fun firmarTrasPrefijoDeAcuse_capturesTask() {
        val intent = analyze("vale, firmar el contrato el jueves")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Firmar el contrato", intent.title)
    }

    @Test
    fun firmarTrasPrefijoTemporal_capturesTask() {
        val intent = analyze("hoy firmar el contrato")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Firmar el contrato", intent.title)
        assertNotNull(intent.dueAt)
    }

    // --- Controles anti-overreach (deben permanecer NULL) ---

    @Test
    fun noFirmarContrato_negatedStaysNull() {
        assertNull(analyze("no firmar el contrato"))
    }

    @Test
    fun quizasFirmar_conditionalStaysNull() {
        assertNull(analyze("quizá firmar el contrato mañana"))
    }

    @Test
    fun firmaDelContrato_nounDoesNotMatch() {
        assertNull(analyze("la firma del contrato es mañana"))
    }

    @Test
    fun firmarSinObjeto_bareVerbStaysNull() {
        assertNull(analyze("firmar"))
    }

    // --- Regresión: la envolvente c.613 sigue gobernando ---

    @Test
    fun tengoQueFirmar_wrapperStillWins() {
        val intent = analyze("tengo que firmar el contrato")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Firmar el contrato", intent.title)
    }
}
