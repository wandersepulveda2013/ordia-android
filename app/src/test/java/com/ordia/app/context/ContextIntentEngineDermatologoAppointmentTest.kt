package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Cobertura de «dermatólogo/dermatóloga» como destino/profesional médico de
 * APPOINTMENT (c.1110, candidata (i) complementaria de la auditoría de
 * descubrimiento OH3 c.1102, clase DECIMOTERCERA salud/autocuidado — la
 * asimetría ya medida para «pediatra» en c.1106 se repite idéntica).
 *
 * Hallazgo medido con probe efímero sobre el motor real (PRE): «dermatólogo»
 * estaba ausente de TODAS las listas médicas cerradas (keyword del kind en
 * ContextIntent.kt, [APPOINTMENT_MEDICAL_PATTERN], [APPOINTMENT_GO_PATTERN],
 * [APPOINTMENT_MEDICAL_FUTURE_PATTERN]) — «ir al dermatólogo el viernes» /
 * «ir a la dermatóloga mañana» / «tendré dermatólogo el viernes» se
 * descartaban (NULL, olvido silencioso P1) mientras sus hermanas «ir al
 * médico»/«ir al psicólogo»/«ir al pediatra» capturaban con 0.77 (c.682,
 * c.1106). Lockstep keyword↔patrón (lección c.639/c.682): se añade en todos
 * los puntos a la vez. El guard de envolvente sigue cubierto vía la fuente
 * única [APPOINTMENT_SPECIFIC] (lección c.653).
 */
class ContextIntentEngineDermatologoAppointmentTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(
                ContextCaptureSource.NOTIFICATION,
                raw,
                1723939200000L
            )
        )

    // --- Forma c.1110: desplazamiento «ir a(l) dermatólogo/a» era NULL ---

    @Test
    fun goToDermatologistWithDayIsCaptured() {
        val intent = analyze("ir al dermatólogo el viernes")
        assertNotNull("ir al dermatólogo el viernes debe capturarse", intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
    }

    @Test
    fun goToDermatologistBareIsCaptured() {
        val intent = analyze("ir al dermatólogo")
        assertNotNull("ir al dermatólogo debe capturarse sin fecha", intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
    }

    @Test
    fun goToDermatologistFeminineIsCaptured() {
        val intent = analyze("ir a la dermatóloga mañana")
        assertNotNull("ir a la dermatóloga mañana debe capturarse", intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
    }

    // --- Futuro declarativo (hermana de «tendré dentista el viernes», c.663) ---

    @Test
    fun futureDermatologistIsCaptured() {
        val intent = analyze("tendré dermatólogo el viernes")
        assertNotNull("tendré dermatólogo el viernes debe capturarse", intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
    }

    // --- «cita con el dermatólogo» ya capturaba por «cita con»; no debe romperse ---

    @Test
    fun appointmentWithDermatologistStaysCaptured() {
        val intent = analyze("cita con el dermatólogo el jueves")
        assertNotNull("cita con el dermatólogo debe seguir capturándose", intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
    }

    // --- Anti-overreach: negación inmediata sigue descartada ---

    @Test
    fun negatedGoToDermatologistIsNotCaptured() {
        assertNull("'no ir al dermatólogo' niega la cita, no debe capturarse",
            analyze("no ir al dermatólogo"))
    }

    // --- Anti-overreach: duda/condición penalizan post-bono (NULL) ---

    @Test
    fun hedgedGoToDermatologistIsNotCaptured() {
        assertNull("'quizá ir al dermatólogo' es especulación, no debe capturarse",
            analyze("quizá ir al dermatólogo"))
    }

    // --- Anti-overreach: envolvente gobierna (TASK, no APPOINTMENT) ---

    @Test
    fun wrappedGoToDermatologistIsTask() {
        val intent = analyze("recuérdame ir al dermatólogo")
        assertNotNull(intent)
        assertEquals(
            "el verbo subordinado es contenido del recordatorio, no APPOINTMENT autónoma",
            ContextIntentKind.TASK, intent!!.kind
        )
    }

    // --- Anti-overreach: estado/atributo y sustantivo derivado ---

    @Test
    fun dermatologistAttributeStatementIsNotCaptured() {
        assertNull("'el dermatólogo es muy bueno' es un estado, no una cita",
            analyze("el dermatólogo es muy bueno"))
    }

    @Test
    fun dermatologiaNounIsNotCaptured() {
        assertNull("'dermatología' (especialidad/servicio) no es el profesional",
            analyze("la dermatología del hospital abre los lunes"))
    }
}
