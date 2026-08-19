package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.698 (P1 olvido silencioso en captura pasiva, ítem BACKLOG OPEN
 * clase-verbos c.692 — forma 4/6; una forma por ciclo, doctrina
 * anti-overreach): "renovar <objeto>" ("renovar el DNI la semana que
 * viene") se DESCARTABA (analyze → NULL). Sonda JVM fuente real PRE-fix
 * (`tools/probe/CommonVerbDiscoveryProbe.kt`, c.692): "renovar el DNI la
 * semana que viene" → NULL. Fix: piso de TASK (ancla inicio/acuse/prefijo
 * temporal — patrón c.691…c.696) + plantilla de título "renovar X"→
 * "Renovar X" que despoja el acuse y el prefijo temporal (lección c.616,
 * match arranca en el verbo). Kind decidido en este ciclo (sonda + código
 * ERRAND): TASK — "renovar" gobierna el OBJETO (DNI/seguro/suscripción/
 * contrato), no el desplazamiento; muchos casos son gestión remota/digital
 * ("renovar la suscripción") y ERRAND está anclado a destinos físicos.
 * Anti-overreach: `\s+\w` exige objeto, `(?<!no )` bloquea la negada,
 * c.649 mantiene "quizá…"→NULL, el sustantivo "renovación" no casa.
 * Determinista (regex), sin random, sin IA fingida.
 */
class ContextIntentEngineRenovarFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: "renovar <objeto>" es una tarea clara ---

    @Test
    fun renovarDniSemanaQueViene_capturesTaskWithDueAt() {
        val intent = analyze("renovar el DNI la semana que viene")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Renovar el DNI", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun renovarSeguroManana_capturesTaskWithDueAt() {
        val intent = analyze("renovar el seguro mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Renovar el seguro", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun renovarPasaporteElLunes_capturesTaskWithDueAt() {
        val intent = analyze("renovar el pasaporte el lunes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Renovar el pasaporte", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun renovarSuscripcionSinFecha_capturesTaskWithoutDueAt() {
        val intent = analyze("renovar la suscripción")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Renovar la suscripción", intent.title)
    }

    @Test
    fun renovarTrasPrefijoDeAcuse_capturesTask() {
        val intent = analyze("vale, renovar el contrato el jueves")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Renovar el contrato", intent.title)
    }

    @Test
    fun renovarTrasPrefijoTemporal_capturesTask() {
        val intent = analyze("hoy renovar el DNI")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Renovar el DNI", intent.title)
        assertNotNull(intent.dueAt)
    }

    // --- Controles anti-overreach (deben permanecer NULL) ---

    @Test
    fun noRenovarSeguro_negatedStaysNull() {
        assertNull(analyze("no renovar el seguro"))
    }

    @Test
    fun quizasRenovar_conditionalStaysNull() {
        assertNull(analyze("quizá renovar el seguro mañana"))
    }

    @Test
    fun renovacionDelSeguro_nounDoesNotMatch() {
        assertNull(analyze("la renovación del seguro es mañana"))
    }

    @Test
    fun renovarSinObjeto_bareVerbStaysNull() {
        assertNull(analyze("renovar"))
    }

    // --- Regresión: la envolvente c.613 sigue gobernando ---

    @Test
    fun tengoQueRenovar_wrapperStillWins() {
        val intent = analyze("tengo que renovar el seguro")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Renovar el seguro", intent.title)
    }
}
