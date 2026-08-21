package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.822 (P1 olvido silencioso en captura pasiva — verbo de gestión pública
 * cotidiano nunca sondeado; una forma por ciclo, doctrina anti-overreach):
 * "tramitar <objeto>" ("tramitar el pasaporte mañana") se DESCARTABA
 * (analyze → NULL). Sonda JVM fuente real PRE-fix
 * (`tools/probe/CaptureCoverageProbe.kt`, c.822 — sondeo de cobertura de
 * captura sobre formas no medidas antes): "tramitar el pasaporte mañana"
 * y "tramitar la visa la semana que viene" → NULL; en cambio "tengo que
 * tramitar el pasaporte" ya capturaba por la envolvente (TASK 0.45, título
 * limpio "Tramitar el pasaporte"). Fix: piso de TASK (ancla inicio/acuse/
 * prefijo temporal — patrón c.691…c.709, hermano de "renovar" c.698) +
 * plantilla de título "tramitar X"→"Tramitar X" que despoja el acuse y el
 * prefijo temporal (lección c.616, match arranca en el verbo). Kind
 * decidido en este ciclo: TASK — "tramitar" gobierna el OBJETO
 * (pasaporte/visa/certificado/DNI), no el desplazamiento; muchas
 * tramitaciones son gestión remota/digital y ERRAND está anclado a
 * destinos físicos. Anti-overreach: `\s+\w` exige objeto, `(?<!no )`
 * bloquea la negada, c.649 mantiene "quizá…"→NULL, el sustantivo
 * "tramitación"/"trámite" no casa, el pasado "tramitó" no casa.
 * Determinista (regex), sin random, sin IA fingida.
 */
class ContextIntentEngineTramitarFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: "tramitar <objeto>" es una tarea clara ---

    @Test
    fun tramitarPasaporteManana_capturesTaskWithDueAt() {
        val intent = analyze("tramitar el pasaporte mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Tramitar el pasaporte", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun tramitarVisaSemanaQueViene_capturesTaskWithDueAt() {
        val intent = analyze("tramitar la visa la semana que viene")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Tramitar la visa", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun tramitarCertificadoElLunes_capturesTaskWithDueAt() {
        val intent = analyze("tramitar el certificado el lunes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Tramitar el certificado", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun tramitarLicenciaSinFecha_capturesTaskWithoutDueAt() {
        val intent = analyze("tramitar la licencia")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Tramitar la licencia", intent.title)
    }

    @Test
    fun tramitarTrasPrefijoDeAcuse_capturesTask() {
        val intent = analyze("vale, tramitar el DNI el jueves")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Tramitar el DNI", intent.title)
    }

    @Test
    fun tramitarTrasPrefijoTemporal_capturesTask() {
        val intent = analyze("hoy tramitar el DNI")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Tramitar el DNI", intent.title)
        assertNotNull(intent.dueAt)
    }

    // --- Controles anti-overreach (deben permanecer NULL; verificados en
    // sonda PRE-fix: /tmp/tramitar_guard_probe.kt) ---

    @Test
    fun noTramitarPasaporte_negatedStaysNull() {
        assertNull(analyze("no tramitar el pasaporte"))
    }

    @Test
    fun quizasTramitar_conditionalStaysNull() {
        assertNull(analyze("quizá tramitar el pasaporte mañana"))
    }

    @Test
    fun tramitacionDelPasaporte_nounDoesNotMatch() {
        assertNull(analyze("la tramitación del pasaporte es mañana"))
    }

    @Test
    fun tramiteDelPasaporte_nounDoesNotMatch() {
        assertNull(analyze("el trámite del pasaporte es mañana"))
    }

    @Test
    fun tramitoPasaporteAyer_pastNarrativeStaysNull() {
        assertNull(analyze("tramitó el pasaporte ayer"))
    }

    @Test
    fun tramitarSinObjeto_bareVerbStaysNull() {
        assertNull(analyze("tramitar"))
    }

    // --- Regresión: la envolvente c.613 sigue gobernando (PRE-fix:
    // TASK 0.45, título limpio) ---

    @Test
    fun tengoQueTramitar_wrapperStillWins() {
        val intent = analyze("tengo que tramitar el pasaporte")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Tramitar el pasaporte", intent.title)
    }
}
