package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.691 (P1 olvido silencioso en captura pasiva, ítem BACKLOG OPEN
 * descubierto c.690 con `tools/probe/KindCheckProbe.kt`, un ítem por
 * ciclo): el verbo cotidiano "revisar" con objeto ("revisar el informe
 * (pasado) mañana") se DESCARTABA (analyze → NULL): ningún piso cubre
 * "revisar" y el bono temporal no alcanza el umbral. Sonda JVM fuente
 * real PRE-fix (`tools/probe/RevisarFloorProbe.kt`): 6 formas de
 * captura → NULL; controles ya NULL; envolvente c.613 ("tengo que
 * revisar…") → TASK.
 *
 * Fix: piso de TASK (mismo patrón que SHOPPING c.626/c.651): el verbo
 * "revisar" + objeto se reconoce al inicio o tras prefijo de acuse
 * ([ACK_PREFIX]: "vale, revisar el informe"). Anti-overreach: exige
 * objeto (`\s+\w`, "revisar" aislado no captura), `(?<!no )` bloquea la
 * negada, la penalización de condicional (c.649) sigue aplicando
 * ("quizá revisar…" → NULL), el sustantivo "revisión" no casa.
 * Determinista (regex), sin random, sin IA fingida.
 */
class ContextIntentEngineRevisarFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: "revisar <objeto>" es una tarea clara ---

    @Test
    fun revisarInformePasadoManana_capturesTaskWithDueAt() {
        val intent = analyze("revisar el informe pasado mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Revisar el informe", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun revisarInformeManana_capturesTaskWithDueAt() {
        val intent = analyze("revisar el informe mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Revisar el informe", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun revisarPresentacionElViernes_capturesTaskWithDueAt() {
        val intent = analyze("revisar la presentación el viernes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Revisar la presentación", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun revisarContratoALas5_capturesTaskWithDueAt() {
        val intent = analyze("revisar el contrato a las 5")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Revisar el contrato", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun revisarApuntesSinFecha_capturesTaskWithoutDueAt() {
        val intent = analyze("revisar los apuntes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Revisar los apuntes", intent.title)
    }

    @Test
    fun revisarTrasPrefijoDeAcuse_capturesTask() {
        val intent = analyze("vale, revisar el informe mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Revisar el informe", intent.title)
    }

    // --- Controles anti-overreach (deben permanecer NULL) ---

    @Test
    fun noRevisarInforme_negatedStaysNull() {
        assertNull(analyze("no revisar el informe"))
    }

    @Test
    fun quizasRevisar_conditionalStaysNull() {
        assertNull(analyze("quizá revisar el informe mañana"))
    }

    @Test
    fun revisionDelCoche_nounDoesNotMatch() {
        assertNull(analyze("la revisión del coche es mañana"))
    }

    @Test
    fun revisarSinObjeto_bareVerbStaysNull() {
        assertNull(analyze("revisar"))
    }

    // --- Regresión: la envolvente c.613 sigue gobernando ---

    @Test
    fun tengoQueRevisar_wrapperStillWins() {
        val intent = analyze("tengo que revisar el informe")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Revisar el informe", intent.title)
    }
}
