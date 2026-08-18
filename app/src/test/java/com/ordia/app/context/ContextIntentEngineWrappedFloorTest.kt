package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guard de imperativo envolvente para los pisos c.643/c.647 (c.652).
 *
 * Defecto descubierto por probe JVM fuente real (hallazgo secundario c.651):
 * los pisos de posición libre — MEETING/EXERCISE/ERRAND/STUDY (c.647) y
 * HOUSEHOLD (c.643), con ancla `\b` — se activan aunque el verbo esté
 * SUBORDINADO a un imperativo envolvente ("recuérdame/no olvides/tengo que/
 * hay que/avísame/notifícame/acordarme"). Así el kind subordinado le ROBABA el
 * kind al envolvente: "avísame reunión con el equipo"→MEETING (empate a 0.45
 * resuelto por orden de enum; REMINDER queda después de MEETING), "recuérdame
 * ir al gimnasio"→EXERCISE 0.59 (base alta que supera el 0.45 de TASK) — y la
 * semántica de aviso ("avísame" = notifícame) se perdía: overreach P1, misma
 * lección de diseño que c.651 (el verbo subordinado es CONTENIDO del
 * recordatorio, no una acción autónoma).
 *
 * La solución ([ContextIntentEngine.imperativeIsWrapped]) descarta el kind
 * subordinado cuando un envolvente PRECEDE a su verbo de piso, dejando que
 * TASK/REMINDER (pisos c.613/c.619) gobiernen. Los regex de los pisos se
 * centralizan en constantes compartidas (lección c.648: guards y pisos no
 * deben diverger).
 *
 * Cobertura:
 * - 6 casos de robo de kind (RED pre-fix → GREEN post-fix), con título limpio
 *   (sin prefijo envolvente filtrado).
 * - 6 envolventes que ya resolvían bien por empate de enum (regresión).
 * - 8 prefijos temporales c.647 siguen capturando.
 * - 5 verbos al inicio siguen capturando.
 * - 5 guards anti-overreach intactos (negación c.648, duda c.649, condición
 *   c.650).
 * - 2 prefijos declarativos NO bloqueados ("tengo una reunión", "voy a correr").
 */
class ContextIntentEngineWrappedFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(
                ContextCaptureSource.NOTIFICATION,
                raw,
                1723939200000L
            )
        )

    // --- Robo de kind: el envolvente DEBE gobernar (RED pre-fix → GREEN) ---

    @Test
    fun avisameMeetingStaysReminder() {
        val intent = analyze("avísame reunión con el equipo")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.REMINDER, intent!!.kind)
        assertEquals("Reunión con el equipo", intent.title)
    }

    @Test
    fun avisameCorrerStaysReminder() {
        val intent = analyze("avísame correr 5k")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.REMINDER, intent!!.kind)
        assertEquals("Correr 5k", intent.title)
    }

    @Test
    fun avisameRecogerStaysReminder() {
        val intent = analyze("avísame recoger el paquete")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.REMINDER, intent!!.kind)
        assertEquals("Recoger el paquete", intent.title)
    }

    @Test
    fun avisameRepasarStaysReminder() {
        val intent = analyze("avísame repasar la lección")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.REMINDER, intent!!.kind)
        assertEquals("Repasar la lección", intent.title)
    }

    @Test
    fun avisameGimnasioStaysReminder() {
        val intent = analyze("avísame ir al gimnasio")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.REMINDER, intent!!.kind)
        assertEquals("Ir al gimnasio", intent.title)
    }

    @Test
    fun recuerdameGimnasioStaysTask() {
        val intent = analyze("recuérdame ir al gimnasio")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Ir al gimnasio", intent.title)
    }

    // --- Envolventes que ya resolvían bien por empate de enum (regresión) ---

    @Test
    fun recuerdameCorrerStaysTask() {
        val intent = analyze("recuérdame correr 5k")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun recuerdameHacerYogaStaysTask() {
        val intent = analyze("recuérdame hacer yoga")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun tengoQueIrAlBancoStaysTask() {
        val intent = analyze("tengo que ir al banco")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun noOlvidesRecogerStaysTask() {
        val intent = analyze("no olvides recoger el paquete")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun hayQueLimpiarStaysTask() {
        val intent = analyze("hay que limpiar la cocina")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun avisameLimpiarStaysReminder() {
        val intent = analyze("avísame limpiar la cocina")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.REMINDER, intent!!.kind)
    }

    // --- Prefijos temporales c.647 intactos (regresión) ---

    @Test
    fun temporalMeetingStillCaptured() {
        val intent = analyze("mañana reunión con el equipo")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.MEETING, intent!!.kind)
    }

    @Test
    fun temporalExerciseStillCaptured() {
        val intent = analyze("mañana correr 5k")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
    }

    @Test
    fun temporalErrandStillCaptured() {
        val intent = analyze("mañana ir al banco")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
    }

    @Test
    fun temporalRecogerStillCaptured() {
        val intent = analyze("hoy recoger el paquete")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
    }

    @Test
    fun temporalStudyStillCaptured() {
        val intent = analyze("mañana repasar la lección")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.STUDY, intent!!.kind)
    }

    @Test
    fun temporalPrepararExamenStillCaptured() {
        val intent = analyze("hoy preparar el examen")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.STUDY, intent!!.kind)
    }

    @Test
    fun temporalLimpiarStillCaptured() {
        val intent = analyze("mañana limpiar la cocina")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
    }

    @Test
    fun temporalEntrenarStillCaptured() {
        val intent = analyze("hoy entrenar piernas")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
    }

    // --- Verbos al inicio intactos (regresión) ---

    @Test
    fun bareMeetingStillCaptured() {
        val intent = analyze("reunión con el equipo")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.MEETING, intent!!.kind)
    }

    @Test
    fun bareCorrerStillCaptured() {
        val intent = analyze("correr 5k")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
    }

    @Test
    fun bareIrAlBancoStillCaptured() {
        val intent = analyze("ir al banco")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
    }

    @Test
    fun bareRecogerStillCaptured() {
        val intent = analyze("recoger el paquete")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
    }

    @Test
    fun bareRepasarStillCaptured() {
        val intent = analyze("repasar la lección")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.STUDY, intent!!.kind)
    }

    // --- Guards anti-overreach previos intactos (regresión) ---

    @Test
    fun negationMeetingStillBlocked() {
        assertNull(analyze("mañana no reunión con el equipo"))
    }

    @Test
    fun negationExerciseStillBlocked() {
        assertNull(analyze("mañana no correr 5k"))
    }

    @Test
    fun negationErrandStillBlocked() {
        assertNull(analyze("no ir al banco"))
    }

    @Test
    fun hedgeExerciseStillPenalized() {
        assertNull(analyze("quizá correr 5k"))
    }

    @Test
    fun conditionalExerciseStillPenalized() {
        assertNull(analyze("si puedo correr 5k"))
    }

    // --- Prefijos declarativos NO bloqueados (no son imperativos envolventes) ---

    @Test
    fun declarativeTengoUnaReunionStillMeeting() {
        val intent = analyze("tengo una reunión con el equipo")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.MEETING, intent!!.kind)
    }

    @Test
    fun declarativeVoyACorrerStillExercise() {
        val intent = analyze("voy a correr 5k")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
    }
}
