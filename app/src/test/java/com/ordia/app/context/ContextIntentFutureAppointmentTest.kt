package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Captura del futuro declarativo de APPOINTMENT (c.663): "tendré (una/la )?cita",
 * "tendré <sustantivo médico>".
 *
 * Defecto descubierto por probe JVM fuente real (/tmp/ProbeApFuture.kt): el futuro
 * declarativo de 1ª persona para citas se DESCARTABA (kind = null): los patrones
 * específicos de APPOINTMENT sólo contemplan presente/infinitivo ("tengo cita",
 * "cita con", sustantivo médico desnudo) — la forma "tendré dentista el viernes"
 * quedaba en ~0.42 (< [MINIMUM_CONFIDENCE]) aunque la evidencia (promesa en
 * indefinido, 1ª persona) es MÁS firme que el presente. ANALOGO al olvido P1 que
 * c.656 cerró para CALL ("llamaré/hablaré + objeto").
 *
 * La solución reutiliza la fuente única [APPOINTMENT_SPECIFIC] (c.653): dos
 * patrones de futuro ("tendré (una |la )?cita" y "tendré dentista|doctor|médico|
 * especialista|consulta|revisión|chequeo|terapia") alimentan el bono específico
 * [scoreSpecificPatterns] y el guard de envolvente [imperativeIsWrapped]
 * ("recuérdame tendré dentista" sigue siendo TASK). La negación inmediata queda
 * protegida con lookbehind `(?<!no )` ("no tendré dentista" NO se captura). El
 * bono fusionado (+0.45) alcanza [MINIMUM_CONFIDENCE] sin fecha. La extracción
 * de título añade la alternativa "tendré" a la rama APPOINTMENT, así el prefijo
 * verbal no ensucia el resto; [sanitizeTitle] retira los anclajes de cola.
 *
 * Cobertura:
 * - 5 casos futuro AP (RED pre-fix → GREEN), con títulos limpios.
 * - 2 negación inmediata ("no tendré dentista/cita") → NO APPOINTMENT.
 * - 1 envolvente ("recuérdame tendré dentista") → TASK o REMINDER (guard).
 * - 2 artículo opcional ("tendré una cita", "tendré la cita") → APPOINTMENT.
 * - 2 controles presente ("tengo cita con el dentista", "cita con el dentista")
 *   sin cambio.
 * - 1 obligación "tendré que ir a la cita" → NO APPOINTMENT (sin sustantivo).
 */
class ContextIntentFutureAppointmentTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(
                ContextCaptureSource.NOTIFICATION,
                raw,
                1723939200000L
            )
        )

    // --- Futuro AP: RED pre-fix → GREEN ---

    @Test
    fun tendreDentistaElViernesIsAppointment() {
        val intent = analyze("tendré dentista el viernes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
    }

    @Test
    fun tendreCitaEstaTardeIsAppointment() {
        val intent = analyze("tendré cita esta tarde")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
    }

    @Test
    fun tendreDoctorMananaIsAppointment() {
        val intent = analyze("tendré doctor mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
    }

    @Test
    fun tendreRevisionMedicaIsAppointment() {
        val intent = analyze("tendré revisión médica la semana que viene")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
    }

    @Test
    fun tendreDentistaTitleIsClean() {
        val intent = analyze("tendré dentista el viernes")
        assertNotNull(intent)
        assertEquals("Cita: Dentista", intent!!.title)
    }

    // --- Negación inmediata: NO APPOINTMENT ---

    @Test
    fun noTendreDentistaIsNotAppointment() {
        val intent = analyze("no tendré dentista el viernes")
        assertNotEquals(ContextIntentKind.APPOINTMENT, intent?.kind)
    }

    @Test
    fun noTendreCitaIsNotAppointment() {
        val intent = analyze("no tendré una cita mañana")
        assertNotEquals(ContextIntentKind.APPOINTMENT, intent?.kind)
    }

    // --- Envolvente: el guard lo saca de APPOINTMENT ---

    @Test
    fun wrappedTendreDentistaIsNotAppointment() {
        val intent = analyze("recuérdame tendré dentista el viernes")
        assertNotNull(intent)
        // El guard debe entregar el texto al envolvente: TASK o REMINDER ("recuérdame"
        // es keyword de REMINDER; el desempate exacto no es propiedad del guard).
        assertTrue(
            intent!!.kind == ContextIntentKind.TASK || intent.kind == ContextIntentKind.REMINDER
        )
    }

    // --- Artículo opcional ---

    @Test
    fun tendreUnaCitaIsAppointment() {
        val intent = analyze("tendré una cita con el dentista")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
        assertEquals("Cita con el dentista", intent.title)
    }

    @Test
    fun tendreLaCitaIsAppointment() {
        val intent = analyze("tendré la cita el viernes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
    }

    // --- Controles presente/infinitivo (regresión) ---

    @Test
    fun tengoCitaConElDentistaStillAppointment() {
        val intent = analyze("tengo cita con el dentista")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
        assertEquals("Cita con el dentista", intent.title)
    }

    @Test
    fun citaConElDentistaStillAppointment() {
        val intent = analyze("cita con el dentista")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
    }

    // --- Obligación sin sustantivo de cita: NO APPOINTMENT ---

    @Test
    fun tendreQueIrALaCitaIsNotAppointment() {
        val intent = analyze("tendré que ir a la cita")
        assertNotEquals(ContextIntentKind.APPOINTMENT, intent?.kind)
    }
}
