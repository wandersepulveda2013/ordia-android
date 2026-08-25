package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Cobertura de «pediatra» como destino/profesional médico de APPOINTMENT (c.1106,
 * candidata (b) de la auditoría de descubrimiento OH3 c.1102, clase
 * DECIMOTERCERA salud/autocuidado).
 *
 * Hallazgo: «pediatra» estaba ausente de TODAS las listas médicas cerradas
 * (keyword del kind en ContextIntent.kt, [APPOINTMENT_MEDICAL_PATTERN],
 * [APPOINTMENT_GO_PATTERN], [APPOINTMENT_MEDICAL_FUTURE_PATTERN]) pese a ser
 * el profesional sanitario más frecuente en la vida familiar — «ir al
 * pediatra mañana» se descartaba (NULL, olvido silencioso P1) mientras sus
 * hermanas «ir al médico»/«ir al psicólogo» capturaban (c.682). Lockstep
 * keyword↔patrón (lección c.639/c.682): se añade en todos los puntos a la vez.
 * El guard de envolvente sigue cubierto vía la fuente única
 * [APPOINTMENT_SPECIFIC] (lección c.653).
 */
class ContextIntentEnginePediatraAppointmentTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(
                ContextCaptureSource.NOTIFICATION,
                raw,
                1723939200000L
            )
        )

    // --- Forma c.1106: desplazamiento «ir a(l) pediatra» era NULL ---

    @Test
    fun goToPediatricianTomorrowIsCaptured() {
        val intent = analyze("ir al pediatra mañana")
        assertNotNull("ir al pediatra mañana debe capturarse", intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
    }

    @Test
    fun goToPediatricianBareIsCaptured() {
        val intent = analyze("ir al pediatra")
        assertNotNull("ir al pediatra debe capturarse sin fecha", intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
    }

    @Test
    fun goToPediatricianFeminineArticleIsCaptured() {
        val intent = analyze("ir a la pediatra el jueves")
        assertNotNull("ir a la pediatra el jueves debe capturarse", intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
    }

    // --- Futuro declarativo (hermana de «tendré dentista el viernes», c.663) ---

    @Test
    fun futurePediatricianIsCaptured() {
        val intent = analyze("tendré pediatra el viernes")
        assertNotNull("tendré pediatra el viernes debe capturarse", intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
    }

    // --- «cita con el pediatra» ya capturaba por «cita con»; no debe romperse ---

    @Test
    fun appointmentWithPediatricianStaysCaptured() {
        val intent = analyze("cita con el pediatra el jueves")
        assertNotNull("cita con el pediatra debe seguir capturándose", intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
    }

    // --- Anti-overreach: negación inmediata sigue descartada ---

    @Test
    fun negatedGoToPediatricianIsNotCaptured() {
        assertNull("'no ir al pediatra' niega la cita, no debe capturarse",
            analyze("no ir al pediatra"))
    }

    // --- Anti-overreach: duda/condición penalizan post-bono (NULL) ---

    @Test
    fun hedgedGoToPediatricianIsNotCaptured() {
        assertNull("'quizá ir al pediatra' es especulación, no debe capturarse",
            analyze("quizá ir al pediatra"))
    }

    // --- Anti-overreach: envolvente gobierna (TASK, no APPOINTMENT) ---

    @Test
    fun wrappedGoToPediatricianIsTask() {
        val intent = analyze("recuérdame ir al pediatra")
        assertNotNull(intent)
        assertEquals(
            "el verbo subordinado es contenido del recordatorio, no APPOINTMENT autónoma",
            ContextIntentKind.TASK, intent!!.kind
        )
    }

    // --- Anti-overreach: estado/atributo y sustantivo derivado ---

    @Test
    fun pediatricianAttributeStatementIsNotCaptured() {
        assertNull("'el pediatra es muy bueno' es un estado, no una cita",
            analyze("el pediatra es muy bueno"))
    }

    @Test
    fun pediatriaNounIsNotCaptured() {
        assertNull("'pediatría' (especialidad/servicio) no es el profesional",
            analyze("la pediatría del hospital abre los lunes"))
    }
}
