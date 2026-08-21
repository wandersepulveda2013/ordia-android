package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * c.830 (P1 olvido silencioso en captura pasiva — verbo de gestión
 * administrativa sin piso; una forma por ciclo, doctrina anti-overreach):
 * "gestionar <objeto>" ("gestionar la reclamación el lunes") se DESCARTABA
 * (analyze → NULL). Sonda JVM efímera fuente real PRE-fix (metodología de
 * `tools/probe/CaptureCoverageProbe.kt`, c.822; pool de dispersión por
 * epoch-day elige una forma por ciclo): "gestionar el alta en el registro
 * mañana", "gestionar la reclamación el lunes", "gestionar la beca esta
 * semana", desnuda "gestionar el envío", con acuse "vale, …", con prefijo
 * temporal "hoy …" → NULL; en cambio "tengo que gestionar la reclamación
 * mañana" ya capturaba por la envolvente (TASK 0.45, título limpio
 * "Gestionar la reclamación"). Fix: piso de TASK (ancla inicio/acuse/
 * prefijo temporal — patrón c.691…c.825, hermano de "tramitar" c.822,
 * "mandar" c.823 y "encargar" c.825) + plantilla de título
 * "gestionar X"→"Gestionar X" que despoja el acuse y el prefijo temporal
 * (lección c.616, match arranca en el verbo) + keyword "gestionar" en TASK
 * (lockstep c.639/c.751: sin ella una notificación "gestionar el envío"
 * sin palabra gatillo NI LLEGA al análisis en producción;
 * [ContextIntent.TRIGGER_WORDS]). Kind decidido en este ciclo: TASK —
 * "gestionar" gobierna el OBJETO (alta/reclamación/beca) como acción de
 * gestión administrativa; hermano semántico directo de "tramitar"(c.822).
 * Anti-overreach: `\s+\w` exige objeto, `(?<!no )` bloquea la negada,
 * c.649 mantiene "quizá…"→NULL, el sustantivo "gestión" no casa, la 1ª
 * persona "te gestiono" no casa, el pasado "gestionó" no casa, el verbo
 * suelto no casa. Determinista (regex), sin random, sin IA fingida.
 */
class ContextIntentEngineGestionarFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: "gestionar <objeto>" es una tarea clara ---

    @Test
    fun gestionarAltaRegistroManana_capturesTaskWithDueAt() {
        val intent = analyze("gestionar el alta en el registro mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Gestionar el alta en el registro", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun gestionarReclamacionElLunes_capturesTaskWithDueAt() {
        val intent = analyze("gestionar la reclamación el lunes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Gestionar la reclamación", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun gestionarBecaEstaSemana_capturesTask() {
        val intent = analyze("gestionar la beca esta semana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Gestionar la beca esta semana", intent.title)
    }

    @Test
    fun gestionarEnvioSinFecha_capturesTaskWithoutDueAt() {
        val intent = analyze("gestionar el envío")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Gestionar el envío", intent.title)
    }

    @Test
    fun gestionarTrasPrefijoDeAcuse_capturesTask() {
        val intent = analyze("vale, gestionar la reclamación mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Gestionar la reclamación", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun gestionarTrasPrefijoTemporal_capturesTask() {
        val intent = analyze("hoy gestionar el alta")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Gestionar el alta", intent.title)
        assertNotNull(intent.dueAt)
    }

    // --- Guards anti-overreach: formas que NO deben capturar ---

    @Test
    fun gestionarNegada_permaneceNull() {
        assertNull(analyze("no gestionar la reclamación"))
    }

    @Test
    fun gestionarConDuda_permaneceNull() {
        assertNull(analyze("quizá gestionar el alta mañana"))
    }

    @Test
    fun gestionarEnPasado_permaneceNull() {
        assertNull(analyze("gestionó la reclamación ayer"))
    }

    @Test
    fun gestionarSustantivo_permaneceNull() {
        assertNull(analyze("la gestión de la reclamación"))
    }

    @Test
    fun gestionarPrimeraPersona_permaneceNull() {
        assertNull(analyze("te gestiono el envío"))
    }

    @Test
    fun gestionarVerboSuelto_permaneceNull() {
        assertNull(analyze("gestionar"))
    }

    // --- Lockstep: la palabra gatillo llega al pipeline de producción ---

    @Test
    fun gestionarEsPalabraGatillo() {
        assertTrue(ContextIntentKind.TRIGGER_WORDS.contains("gestionar"))
    }

    // --- Regresiones: envolvente y pisos hermanos intactos ---

    @Test
    fun gestionarBajoEnvolvente_sigueCapturando() {
        val intent = analyze("tengo que gestionar la reclamación mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Gestionar la reclamación", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun regresionTramitar_intacta() {
        val intent = analyze("tramitar el pasaporte mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Tramitar el pasaporte", intent.title)
        assertNotNull(intent.dueAt)
    }
}
