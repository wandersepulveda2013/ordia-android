package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1126: candidata (f) de la clase DECIMOTERCERA (salud/autocuidado,
 * sonda persistida `tools/probe/ThirteenthClassHealthProbe.kt` c.1102,
 * caso C12) — «limpieza dental». La higiene dental profesional es el
 * compromiso sanitario preventivo más cotidiano (semestral/anual) y su
 * olvido tiene coste real (P1, evitar olvidos: cita perdida, sarro,
 * reorganización de agenda). Medición PRE con sonda efímera
 * `/tmp/probe1126/Probe.kt` (motor real vía tools/run_probe.sh) sobre
 * HEAD `6b37f231`: 7/8 candidatas NULL («ir a la limpieza dental…»,
 * «limpieza dental el jueves», «tengo limpieza dental mañana»,
 * «hacerse la limpieza dental…», «…es el viernes a las 10», «tendré la
 * limpieza dental…», «…con el higienista el martes»); sólo «pedir cita
 * para la limpieza dental» capturaba como TASK 0.45 genérico (piso
 * «pedir <objeto>»), nunca APPOINTMENT. 7/7 guards NULL correctos y
 * 6/6 pines HIT. Causa raíz: «limpieza dental» no era keyword de
 * APPOINTMENT (ContextIntent.kt) ni evidencia específica en los tres
 * patrones cerrados de la familia (MEDICAL/GO/FUTURE). Fix hermana
 * EXACTA de c.1110 (dermatólogo): lockstep en CUATRO puntos (lección
 * c.682/c.1110) — keyword + APPOINTMENT_MEDICAL_PATTERN +
 * APPOINTMENT_GO_PATTERN + APPOINTMENT_MEDICAL_FUTURE_PATTERN. La
 * keyword es la frase de DOS palabras «limpieza dental»: «limpieza» a
 * secas (de casa/del piso/del coche) jamás entra (anti-overreach; el
 * substring exige la frase completa). En FUTURE la entrada admite el
 * artículo natural («tendré LA limpieza dental», a diferencia de
 * «tendré dentista» desnudo). Acotado deliberado (una forma por ciclo,
 * doctrina anti-overreach): el reflexivo «hacerse la limpieza dental»
 * sigue FUERA (región del piso reflexivo TASK en curso del hermano
 * c.1125; su aritmética queda 0.42 < umbral) y el sustantivo con fecha
 * sin desplazamiento/futuro («limpieza dental el jueves») sigue FUERA
 * por consistencia con TODA la familia de sustantivos médicos
 * (0.12+0.2+0.1 = 0.42 < 0.45, igual que «dermatólogo el viernes»).
 */
class ContextIntentEngineLimpiezaDentalAppointmentTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Capturas: desplazamiento (GO, c.682) ----

    @Test
    fun goLimpiezaDentalSemanaQueVieneIsCaptured() {
        // Caso C12 exacto de la sonda persistida c.1102.
        val intent = analyze("ir a la limpieza dental la semana que viene")
        assertNotNull("'ir a la limpieza dental' es desplazamiento a cita", intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
        assertEquals("Ir a la limpieza dental", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun goLimpiezaDentalMananaIsCaptured() {
        val intent = analyze("ir a la limpieza dental mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
        assertNotNull(intent!!.dueAt)
    }

    @Test
    fun voyALimpiezaDentalIsCaptured() {
        val intent = analyze("voy a la limpieza dental el jueves")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
        assertNotNull(intent!!.dueAt)
    }

    @Test
    fun goLimpiezaDentalConHoraIsCaptured() {
        val intent = analyze("ir a la limpieza dental el viernes a las 10")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
        assertNotNull(intent!!.dueAt)
    }

    // ---- Capturas: futuro declarativo (FUTURE, c.663) ----

    @Test
    fun futureLimpiezaDentalConArticuloIsCaptured() {
        val intent = analyze("tendré la limpieza dental el mes que viene")
        assertNotNull("'tendré la limpieza dental' es promesa en 1ª persona", intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
        assertNotNull(intent!!.dueAt)
    }

    @Test
    fun futureLimpiezaDentalSinArticuloIsCaptured() {
        val intent = analyze("tendré limpieza dental mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
        assertNotNull(intent!!.dueAt)
    }

    // ---- Anti-overreach: el doméstico NO entra (frase de dos palabras) ----

    @Test
    fun limpiezaDeCasaIsNotCaptured() {
        assertNull("'hacer la limpieza de casa' es doméstico, no cita",
            analyze("hacer la limpieza de casa el sábado"))
    }

    @Test
    fun limpiezaDelPisoIsNotCaptured() {
        assertNull("'limpieza a fondo del piso' es doméstico, no cita",
            analyze("limpieza a fondo del piso este finde"))
    }

    @Test
    fun limpiezaDelCocheIsNotCaptured() {
        assertNull("'la limpieza del coche' es del vehículo, no cita",
            analyze("la limpieza del coche mañana"))
    }

    // ---- Anti-overreach: evidencia insuficiente / duda / negación / pasado ----

    @Test
    fun bareNounIsNotCaptured() {
        assertNull("'limpieza dental' aislado carece de evidencia",
            analyze("limpieza dental"))
    }

    @Test
    fun nounWithDateAloneIsNotCaptured() {
        // Consistente con TODA la familia de sustantivos médicos
        // («dermatólogo el viernes»): 0.12+0.2+0.1 = 0.42 < 0.45.
        assertNull("'limpieza dental el jueves' sin desplazamiento/futuro sigue fuera",
            analyze("limpieza dental el jueves"))
    }

    @Test
    fun hedgedGoIsNotCaptured() {
        assertNull("'quizá ir a la limpieza dental' es especulación",
            analyze("quizá ir a la limpieza dental"))
    }

    @Test
    fun negatedGoIsNotCaptured() {
        assertNull("'no ir a la limpieza dental' niega la cita",
            analyze("no ir a la limpieza dental"))
    }

    @Test
    fun negatedVoyIsNotCaptured() {
        assertNull("'no voy a la limpieza dental' niega la cita",
            analyze("no voy a la limpieza dental mañana"))
    }

    @Test
    fun pastIsNotCaptured() {
        assertNull("'ayer tuve la limpieza dental' es pasado",
            analyze("ayer tuve la limpieza dental"))
    }

    @Test
    fun wrappedGoIsTaskNotAppointment() {
        // Guard de envolvente (c.653) vía APPOINTMENT_SPECIFIC.
        val intent = analyze("recuérdame ir a la limpieza dental mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun reflexiveStaysOutOfScope() {
        // Acotado deliberado: «hacerse la limpieza dental» es región del
        // piso reflexivo (hermano c.1125 EN CURSO); aritmética 0.42 < umbral.
        assertNull("'hacerse la limpieza dental' queda para el piso reflexivo",
            analyze("hacerse la limpieza dental el lunes"))
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

    @Test
    fun lavarCochePinStillHousehold() {
        val intent = analyze("lavar el coche esta tarde")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
    }
}
