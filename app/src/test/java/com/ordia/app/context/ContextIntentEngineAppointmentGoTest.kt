package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Cobertura de la forma "ir a(l) <destino médico>" para APPOINTMENT (c.682).
 *
 * Hallazgo c.681 (BACKLOG P1): "ir al médico mañana" se descartaba (NULL)
 * porque APPOINTMENT sólo activaba por keyword "cita" o por los patrones
 * [APPOINTMENT_CITA_PATTERN]/[APPOINTMENT_MEDICAL_PATTERN] sueltos, que con
 * fecha añadida llegaban a ~0.42 (< [MINIMUM_CONFIDENCE]). La familia entera
 * de desplazamiento a destino médico se perdía: "ir al dentista", "ir al
 * médico", "ir al doctor el viernes", "ir a terapia", "ir a la consulta",
 * "ir al psicólogo". El bono [APPOINTMENT_GO_PATTERN] eleva la forma por
 * encima del umbral (determinista, sin IA fingida); el guard de envolvente
 * queda cubierto vía la fuente única [APPOINTMENT_SPECIFIC] (lección
 * c.652/c.653: piso/bono y guard comparten EXACTAMENTE el mismo patrón).
 */
class ContextIntentEngineAppointmentGoTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(
                ContextCaptureSource.NOTIFICATION,
                raw,
                1723939200000L
            )
        )

    // --- Forma c.681: "ir al médico mañana" era NULL (P1: olvido silencioso) ---

    @Test
    fun goToDoctorTomorrowIsCaptured() {
        val intent = analyze("ir al médico mañana")
        assertNotNull("ir al médico mañana debe capturarse", intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
    }

    @Test
    fun goToDentistBareIsCaptured() {
        val intent = analyze("ir al dentista")
        assertNotNull("ir al dentista debe capturarse sin fecha", intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
    }

    @Test
    fun goToDoctorWithWeekdayIsCaptured() {
        val intent = analyze("ir al doctor el viernes")
        assertNotNull("ir al doctor el viernes debe capturarse", intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
    }

    @Test
    fun goToTherapyIsCaptured() {
        val intent = analyze("ir a terapia")
        assertNotNull("ir a terapia debe capturarse", intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
    }

    @Test
    fun goToConsultationIsCaptured() {
        val intent = analyze("ir a la consulta mañana")
        assertNotNull("ir a la consulta mañana debe capturarse", intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
    }

    @Test
    fun goToPsychologistIsCaptured() {
        val intent = analyze("ir al psicólogo")
        assertNotNull("ir al psicólogo debe capturarse", intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
    }

    @Test
    fun goToCheckupIsCaptured() {
        val intent = analyze("ir al chequeo")
        assertNotNull("ir al chequeo debe capturarse", intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
    }

    // --- Anti-overreach: negación inmediata sigue descartada ---

    @Test
    fun negatedGoToDoctorIsNotCaptured() {
        assertNull("'no ir al médico' niega la cita, no debe capturarse",
            analyze("no ir al médico"))
    }

    @Test
    fun temporalNegatedGoToDentistIsNotCaptured() {
        assertNull("'mañana no ir al dentista' niega la cita, no debe capturarse",
            analyze("mañana no ir al dentista"))
    }

    // --- Anti-overreach: envolventes gobiernan (TASK/REMINDER, no APPOINTMENT) ---

    @Test
    fun wrappedGoToDoctorIsTask() {
        val intent = analyze("recuérdame ir al médico")
        assertNotNull(intent)
        assertEquals(
            "el verbo subordinado es contenido del recordatorio, no APPOINTMENT autónoma",
            ContextIntentKind.TASK, intent!!.kind
        )
    }

    @Test
    fun wrappedGoToDentistIsReminder() {
        val intent = analyze("avísame ir al dentista")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.REMINDER, intent!!.kind)
    }

    @Test
    fun obligationGoToDoctorIsTaskWithoutDate() {
        val intent = analyze("tengo que ir al médico")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    // --- Anti-overreach: duda/condición penalizan post-bono (NULL) ---

    @Test
    fun hedgedGoToDoctorIsNotCaptured() {
        assertNull("'quizá ir al médico' es especulación, no debe capturarse",
            analyze("quizá ir al médico"))
    }

    @Test
    fun conditionalGoToDoctorIsNotCaptured() {
        assertNull("'si puedo ir al médico' es condición no resuelta, no debe capturarse",
            analyze("si puedo ir al médico"))
    }
}
