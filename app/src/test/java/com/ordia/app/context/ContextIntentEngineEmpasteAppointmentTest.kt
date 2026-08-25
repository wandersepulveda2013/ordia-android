package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1136 [renumerado c.1134→c.1136 por colisión de marcador: el hermano
 * fijó c.1134 primero (513c481, «presentar <trámite>») y c.1135
 * (c21723a, campamento) — primer-marcador-gana]: candidata (k) de la
 * clase DECIMOTERCERA (salud/autocuidado, sonda persistida
 * `tools/probe/ThirteenthClassHealthProbeComplement.kt` c.1102, caso
 * C19 «empaste en la muela mañana» NULL) — «empaste». La obturación
 * dental es de las citas sanitarias más cotidianas y su olvido tiene
 * coste real (P1, evitar olvidos: cita perdida, dolor, re-agenda).
 * Medición PRE con sonda efímera `/tmp/PreProbeEmpaste.kt` (motor real
 * vía tools/run_probe.sh) sobre HEAD `c37f62c`: 7/8 formas directas
 * NULL («ir al empaste el jueves», «empaste en el dentista mañana»,
 * «tengo el empaste el lunes a las 10», «el empaste es el viernes a
 * las 5», «tendré el empaste la semana que viene», «ir a que me pongan
 * el empaste el martes», «empaste de la muela el jueves»); sólo «pedir
 * cita para el empaste mañana» capturaba TASK 0.45 genérico (piso
 * «pedir <objeto>»), nunca APPOINTMENT — paridad exacta con c.1126.
 * 5/5 guards NULL correctos y pines HIT (dentista 0.77, limpieza
 * dental 0.77, envolvente TASK 0.45). Causa raíz: «empaste» no era
 * keyword de APPOINTMENT (ContextIntent.kt) ni evidencia específica en
 * los tres patrones cerrados de la familia (MEDICAL/GO/FUTURE). Fix
 * hermano EXACTO de c.1126 (limpieza dental) y c.1110 (dermatólogo):
 * lockstep en CUATRO puntos (lección c.682/c.1110) — keyword +
 * APPOINTMENT_MEDICAL_PATTERN + APPOINTMENT_GO_PATTERN +
 * APPOINTMENT_MEDICAL_FUTURE_PATTERN. La keyword es UNA palabra,
 * «empaste»: sustantivo inequívoco dental (el verbo albañil
 * «empastar» NO lo contiene como substring — difieren en la 7ª letra:
 * empastE vs empastaR). En FUTURE la entrada admite el artículo
 * natural («tendré EL empaste», como «tendré LA limpieza dental»).
 * Acotado deliberado (una forma por ciclo, doctrina anti-overreach):
 * «tengo el empaste…» (posesivo presente; «tengo el médico» también es
 * NULL hoy — gap lateral anotado), «el empaste es el viernes»,
 * «empaste de la muela el jueves» e «ir a que me pongan el empaste»
 * quedan FUERA por consistencia con TODA la familia de sustantivos
 * médicos (igual que «dermatólogo el viernes» o «limpieza dental el
 * jueves» en c.1126).
 */
class ContextIntentEngineEmpasteAppointmentTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Capturas: desplazamiento (GO, c.682) ----

    @Test
    fun goEmpasteJuevesIsCaptured() {
        val intent = analyze("ir al empaste el jueves")
        assertNotNull("'ir al empaste' es desplazamiento a cita", intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
        assertEquals("Ir al empaste", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun goEmpasteMananaIsCaptured() {
        val intent = analyze("ir al empaste mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
        assertNotNull(intent!!.dueAt)
    }

    @Test
    fun voyAlEmpasteIsCaptured() {
        val intent = analyze("voy al empaste el martes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
        assertNotNull(intent!!.dueAt)
    }

    @Test
    fun goEmpasteConHoraIsCaptured() {
        val intent = analyze("ir al empaste el viernes a las 10")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
        assertNotNull(intent!!.dueAt)
    }

    // ---- Capturas: futuro declarativo (FUTURE, c.663) ----

    @Test
    fun futureEmpasteConArticuloIsCaptured() {
        val intent = analyze("tendré el empaste la semana que viene")
        assertNotNull("'tendré el empaste' es promesa en 1ª persona", intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
        assertNotNull(intent!!.dueAt)
    }

    @Test
    fun futureEmpasteSinArticuloIsCaptured() {
        val intent = analyze("tendré empaste mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
        assertNotNull(intent!!.dueAt)
    }

    // ---- Anti-overreach: el albañil NO entra ----

    @Test
    fun empastarLaParedIsNotCaptured() {
        // «empastar» (verbo de albañilería) NO contiene «empaste»
        // (difieren en la 7ª letra): la keyword jamás casa.
        assertNull("'empastar la pared' es bricolaje, no cita dental",
            analyze("empastar la pared del baño mañana"))
    }

    // ---- Anti-overreach: evidencia insuficiente / duda / negación / pasado ----

    @Test
    fun bareNounIsNotCaptured() {
        assertNull("'el empaste' aislado carece de evidencia",
            analyze("el empaste"))
    }

    @Test
    fun nounWithDateAloneIsNotCaptured() {
        // Consistente con TODA la familia de sustantivos médicos
        // («dermatólogo el viernes», «limpieza dental el jueves» c.1126).
        assertNull("'empaste de la muela el jueves' sin desplazamiento/futuro sigue fuera",
            analyze("empaste de la muela el jueves"))
    }

    @Test
    fun hedgedGoIsNotCaptured() {
        assertNull("'quizá ir al empaste' es especulación",
            analyze("quizá ir al empaste"))
    }

    @Test
    fun negatedGoIsNotCaptured() {
        assertNull("'no ir al empaste' niega la cita",
            analyze("no ir al empaste"))
    }

    @Test
    fun negatedVoyIsNotCaptured() {
        assertNull("'no voy al empaste' niega la cita",
            analyze("no voy al empaste mañana"))
    }

    @Test
    fun pastIsNotCaptured() {
        assertNull("'fui al empaste ayer' es pasado",
            analyze("fui al empaste ayer"))
    }

    @Test
    fun wrappedGoIsTaskNotAppointment() {
        // Guard de envolvente (c.653) vía APPOINTMENT_SPECIFIC.
        val intent = analyze("recuérdame ir al empaste el jueves")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    // ---- Pines (regresiones canario de la familia) ----

    @Test
    fun medicoPinStillCaptured() {
        val intent = analyze("ir al médico el lunes a las 5")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
    }

    @Test
    fun dentistaPinStillCaptured() {
        val intent = analyze("cita con el dentista mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
    }

    @Test
    fun limpiezaDentalPinStillCaptured() {
        // Hermana mayor c.1126: byte-idéntica tras el lockstep.
        val intent = analyze("ir a la limpieza dental la semana que viene")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
        assertEquals("Ir a la limpieza dental", intent.title)
    }

    @Test
    fun ecografiaPinStillCaptured() {
        val intent = analyze("hacerse la ecografía el miércoles")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
    }

    @Test
    fun analiticasPinStillCaptured() {
        val intent = analyze("hacerme las analíticas en ayunas la semana que viene")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
    }
}
