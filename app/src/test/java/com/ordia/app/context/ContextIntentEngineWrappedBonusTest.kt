package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Guard de imperativo envolvente para los bonus-kinds APPOINTMENT/CALL (c.653).
 *
 * Defecto descubierto por probe JVM fuente real (hallazgo registrado en c.652):
 * extender [ContextIntentEngine.imperativeIsWrapped] a los pisos c.643/c.647
 * dejó a dos kinds alcanzables por capturas envueltas SIN piso: APPOINTMENT y
 * CALL son "bonus-kinds" — su confianza crece por bono específico aditivo
 * ([ContextIntentEngine.scoreSpecificPatterns]) hasta superar el piso de
 * TASK/REMINDER. Así, ante "recuérdame cita con el dentista" ganaba APPOINTMENT
 * (0.69 vs piso TASK 0.45) y ante "recuérdame llamar al banco" ganaba CALL
 * (0.57): el verbo subordinado robaba el kind al envolvente y la semántica de
 * aviso ("recuérdame" = "avísame") se perdía (overreach P1; misma lección de
 * diseño que c.651/c.652: el contenido subordinado NO es una acción autónoma).
 * Además el título se corrompía: "tengo que ir a la cita con el dentista" →
 * APPOINTMENT con título "Cita: que ir a la cita con el dentista".
 *
 * La solución centraliza los patrones de ACTIVACIÓN de los bonus-kinds en
 * constantes ([APPOINTMENT_SPECIFIC]/[CALL_SPECIFIC], misma lección c.648:
 * guards y activadores no deben diverger), las reutiliza en
 * [ContextIntentEngine.scoreSpecificPatterns] y las añade al mapa del guard
 * ([WRAPPABLE_PATTERNS], c.653), de modo que el envolvente descarta el kind
 * subordinado y TASK/REMINDER (pisos c.613/c.619) gobiernan.
 *
 * Cobertura:
 * - 6 casos APPOINTMENT envuelto (RED pre-fix → GREEN), con título limpio.
 * - 6 casos CALL envuelto (RED pre-fix → GREEN), con título limpio.
 * - 5 controles de posición libre (APPOINTMENT/CALL sin envolvente NO cambian).
 * - 2 prefijos declarativos NO bloqueados ("tengo cita...", "voy a llamar...").
 */
class ContextIntentEngineWrappedBonusTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(
                ContextCaptureSource.NOTIFICATION,
                raw,
                1723939200000L
            )
        )

    // --- APPOINTMENT envuelto: el envolvente DEBE gobernar (RED → GREEN) ---

    @Test
    fun recordameCitaDentistaStaysTask() {
        val intent = analyze("recuérdame cita con el dentista")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cita con el dentista", intent.title)
    }

    @Test
    fun recordameLaCitaStaysTask() {
        val intent = analyze("recuérdame la cita con el dentista")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("La cita con el dentista", intent.title)
    }

    @Test
    fun avisameCitaDoctorStaysReminder() {
        val intent = analyze("avísame cita con el doctor")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.REMINDER, intent!!.kind)
        assertEquals("Cita con el doctor", intent.title)
    }

    @Test
    fun noOlvidesCitaMedicaStaysTask() {
        val intent = analyze("no olvides cita médica")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cita médica", intent.title)
    }

    @Test
    fun tengoQueIrCitaStaysTaskWithCleanTitle() {
        // Pre-fix el título se corrompía: "Cita: que ir a la cita con el dentista".
        val intent = analyze("tengo que ir a la cita con el dentista")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Ir a la cita con el dentista", intent.title)
    }

    @Test
    fun avisameConsultaEspecialistaStaysReminder() {
        val intent = analyze("avísame la consulta con el especialista")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.REMINDER, intent!!.kind)
        assertEquals("La consulta con el especialista", intent.title)
    }

    // --- CALL envuelto: el envolvente DEBE gobernar (RED → GREEN) ---

    @Test
    fun recordameLlamarBancoStaysTask() {
        val intent = analyze("recuérdame llamar al banco")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Llamar al banco", intent.title)
    }

    @Test
    fun recordameLlamarMamaStaysTask() {
        val intent = analyze("recuérdame llamar a mamá")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Llamar a mamá", intent.title)
    }

    @Test
    fun avisameLlamarDoctorStaysReminder() {
        val intent = analyze("avísame llamar al doctor")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.REMINDER, intent!!.kind)
        assertEquals("Llamar al doctor", intent.title)
    }

    @Test
    fun noOlvidesLlamarStaysTask() {
        val intent = analyze("no olvides llamar a María")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Llamar a María", intent.title)
    }

    @Test
    fun tengoQueLlamarStaysTask() {
        val intent = analyze("tengo que llamar al electricista")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Llamar al electricista", intent.title)
    }

    @Test
    fun recordameHablarJefeStaysTask() {
        val intent = analyze("recuérdame hablar con el jefe")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hablar con el jefe", intent.title)
    }

    // --- Posición libre SIN envolvente: APPOINTMENT/CALL no cambian ---

    @Test
    fun citaSolaStaysAppointment() {
        val intent = analyze("cita con el dentista")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
    }

    @Test
    fun llamarSoloStaysCall() {
        val intent = analyze("llamar al banco")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.CALL, intent!!.kind)
    }

    @Test
    fun llamarMamaSoloStaysCall() {
        val intent = analyze("llamar a mamá")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.CALL, intent!!.kind)
    }

    @Test
    fun hablarSoloStaysCall() {
        val intent = analyze("hablar con el jefe")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.CALL, intent!!.kind)
    }

    @Test
    fun llamarConFechaStaysCall() {
        // Regresión del bono de objeto explícito (P1): CALL debe vencer a
        // APPOINTMENT cuando el verbo "llamar" gobierna el contenido.
        val intent = analyze("llamar al doctor el viernes a las 4")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.CALL, intent!!.kind)
    }

    @Test
    fun tengoCitaDeclarativeNotBlocked() {
        val intent = analyze("tengo cita con el dentista")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
    }

    @Test
    fun voyALlamarDeclarativeNotBlocked() {
        val intent = analyze("voy a llamar al banco")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.CALL, intent!!.kind)
    }
}
