package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guard anti-overreach para obligación/posesión PASADA (c.824).
 *
 * Hermano de la familia c.648-c.653, descubierto por sonda JVM fuente real:
 * los envolventes en imperfecto/pretérito ("tenía que", "tuve/tuvo que",
 * "había que", "tenía/tuve cita|reunión") NO están en el wrapper de presente
 * (c.613), así la acción subordinada activaba los pisos/bonos fuertes y se
 * persistía como compromiso FUTURO firme lo que el usuario describió como
 * obligación YA PASADA (¿cumplida? ambiguo — afirmar el pasado no compromete
 * el futuro). Peor: la forma NEGADA pasada ("no tenía que ir al médico")
 * capturaba también — rendija entre c.648 (exige "no" inmediato al verbo del
 * kind) y c.681 (sólo envolvente de obligación en presente).
 *
 * Decisión de diseño (documentada, honesta, determinista):
 * - Marcador pasado QUE PRECEDE al match del kind ⇒ la acción es contenido de
 *   una afirmación sobre el pasado, no compromiso ⇒ se DESCARTA ese kind
 *   (mecánica posicional idéntica a [ContextIntentEngine.imperativeIsWrapped]).
 * - Envoltura PRESENTE que gobierna contenido pasado ("recuérdame que tenía
 *   que llamar al banco", "avísame si había que pagar la luz") ⇒ el usuario
 *   SÍ pide el recordatorio ⇒ se CAPTURA (TASK/REMINDER no están en el mapa
 *   posicional del guard).
 * - Presente idéntico ("tengo que ir al médico", "tengo cita con el
 *   dentista") ⇒ compromiso vigente ⇒ se CAPTURA igual que antes.
 * - "tendré que" (futuro) no casa el patrón; "llamar al banco porque tenía
 *   que ir al médico" (la acción precede al marcador) sigue capturando CALL.
 * - c.826 (hermano): el pasado de «deber» («debía/debí/debió/debíamos/
 *   debieron …», con «que» opcional por dequeísmo) tenía el mismo overreach;
 *   se añadió como segunda rama del patrón. «debido a…», «deberá/deberás»
 *   y «debes» no casan (frontera \b y alternancias explícitas).
 */
class ContextIntentEnginePastObligationGuardTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(
                ContextCaptureSource.NOTIFICATION,
                raw,
                1723939200000L
            )
        )

    // --- Obligación pasada gobernando la acción: NULL (overreach) ---

    @Test
    fun pastObligationAppointmentIsNotCaptured() {
        assertNull("'tenía que ir al médico' describe el pasado, no compromiso", analyze("tenía que ir al médico"))
    }

    @Test
    fun pastObligationCallIsNotCaptured() {
        assertNull("'tenía que llamar al banco' describe el pasado, no compromiso", analyze("tenía que llamar al banco"))
    }

    @Test
    fun pastObligationHabiaQueCallIsNotCaptured() {
        assertNull("'había que llamar al fontanero' describe el pasado, no compromiso", analyze("había que llamar al fontanero"))
    }

    @Test
    fun pastObligationPreteriteIsNotCaptured() {
        assertNull("'tuve que ir al médico' describe el pasado, no compromiso", analyze("tuve que ir al médico"))
        assertNull("'tuvo que ir al médico' describe el pasado, no compromiso", analyze("tuvo que ir al médico"))
    }

    @Test
    fun pastObligationPluralIsNotCaptured() {
        assertNull("'tenían que llamar al banco' describe el pasado, no compromiso", analyze("tenían que llamar al banco"))
    }

    @Test
    fun pastPossessionAppointmentIsNotCaptured() {
        assertNull("'tenía cita con el dentista' describe posesión pasada", analyze("tenía cita con el dentista"))
        assertNull("'tenía una cita con el dentista' describe posesión pasada", analyze("tenía una cita con el dentista"))
    }

    @Test
    fun pastPossessionMeetingIsNotCaptured() {
        assertNull("'tenía reunión con el equipo' describe posesión pasada", analyze("tenía reunión con el equipo"))
        assertNull("'tenía una reunión con el equipo' describe posesión pasada", analyze("tenía una reunión con el equipo"))
    }

    @Test
    fun negatedPastObligationIsNotCaptured() {
        assertNull(
            "'no tenía que ir al médico' niega una obligación pasada: nada capturable",
            analyze("no tenía que ir al médico")
        )
    }

    @Test
    fun pastObligationStudyIsNotCaptured() {
        assertNull("'había que estudiar para el examen' describe el pasado", analyze("había que estudiar para el examen"))
    }

    @Test
    fun pastObligationWithFutureAnchorIsNotCaptured() {
        assertNull(
            "'mañana tenía que llamar al banco' es un plan que cambió, no compromiso",
            analyze("mañana tenía que llamar al banco")
        )
    }

    // --- Envoltura PRESENTE sobre contenido pasado: se CAPTURA (legítimo) ---

    @Test
    fun wrappedReminderOverPastContentIsCaptured() {
        val r = analyze("recuérdame que tenía que llamar al banco")
        assertNotNull("la envoltura presente 'recuérdame' gobierna: TASK sobrevive", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }

    @Test
    fun wrappedNotifyOverPastContentIsCaptured() {
        val r = analyze("avísame si había que pagar la luz")
        assertNotNull("la envoltura presente 'avísame' gobierna: REMINDER sobrevive", r)
        assertEquals(ContextIntentKind.REMINDER, r!!.kind)
    }

    // --- Presente idéntico: se CAPTURA igual que antes (controles) ---

    @Test
    fun presentObligationStillCaptured() {
        val r = analyze("tengo que ir al médico")
        assertNotNull("'tengo que ir al médico' es compromiso vigente", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
        assertEquals("Ir al médico", r.title)
    }

    @Test
    fun presentPossessionAppointmentStillCaptured() {
        val r = analyze("tengo cita con el dentista")
        assertNotNull("'tengo cita con el dentista' es compromiso vigente", r)
        assertEquals(ContextIntentKind.APPOINTMENT, r!!.kind)
    }

    @Test
    fun presentPossessionMeetingStillCaptured() {
        val r = analyze("tengo reunión con el equipo")
        assertNotNull("'tengo reunión con el equipo' es compromiso vigente", r)
        assertEquals(ContextIntentKind.MEETING, r!!.kind)
    }

    @Test
    fun presentActionBeforePastMarkerStillCaptured() {
        val r = analyze("llamar al banco porque tenía que ir al médico")
        assertNotNull("la acción PRECEDE al marcador pasado: CALL sobrevive", r)
        assertEquals(ContextIntentKind.CALL, r!!.kind)
    }

    // --- Pasado de «deber» (c.826, hermano: sonda ConditionalNecessityProbe) ---

    @Test
    fun pastDeberCallIsNotCaptured() {
        assertNull("'debía llamar al banco' describe el pasado, no compromiso", analyze("debía llamar al banco"))
        assertNull("'debí llamar al banco' describe el pasado, no compromiso", analyze("debí llamar al banco"))
        assertNull("'debiste llamar al médico' describe el pasado, no compromiso", analyze("debiste llamar al médico"))
    }

    @Test
    fun pastDeberPluralFormsAreNotCaptured() {
        assertNull("'debíamos ir al médico' describe el pasado, no compromiso", analyze("debíamos ir al médico"))
        assertNull("'debieron recoger el paquete' describe el pasado, no compromiso", analyze("debieron recoger el paquete"))
        assertNull("'debías estudiar más' describe el pasado, no compromiso", analyze("debías estudiar más"))
        assertNull("'debió pagar la factura' describe el pasado", analyze("debió pagar la factura"))
    }

    @Test
    fun pastDeberDequeismoIsNotCaptured() {
        assertNull("'debía que llamar al banco' (dequeísmo) describe el pasado", analyze("debía que llamar al banco"))
    }

    @Test
    fun negatedPastDeberIsNotCaptured() {
        assertNull(
            "'no debía llamar al banco' niega una obligación pasada: nada capturable",
            analyze("no debía llamar al banco")
        )
    }

    @Test
    fun wrappedReminderOverPastDeberContentIsCaptured() {
        val r = analyze("recuérdame que debía llamar al banco")
        assertNotNull("la envoltura presente 'recuérdame' gobierna: TASK sobrevive", r)
        assertEquals(ContextIntentKind.TASK, r!!.kind)
    }

    @Test
    fun futureDeberStillCaptured() {
        val r = analyze("deberá llamar al banco")
        assertNotNull("'deberá llamar al banco' es compromiso futuro", r)
        assertEquals(ContextIntentKind.CALL, r!!.kind)
        val r2 = analyze("deberás llamar al banco")
        assertNotNull("'deberás llamar al banco' es compromiso futuro", r2)
        assertEquals(ContextIntentKind.CALL, r2!!.kind)
    }

    @Test
    fun debidoAdverbDoesNotSuppressLegitCapture() {
        val r = analyze("debido a la lluvia tengo cita con el médico mañana")
        assertNotNull("'debido a' no es marcador de obligación pasada: APPOINTMENT sobrevive", r)
        assertEquals(ContextIntentKind.APPOINTMENT, r!!.kind)
    }

    @Test
    fun pastDebtStaysNull() {
        assertNull("'me debía dinero' es una deuda pasada ajena, no compromiso", analyze("me debía dinero"))
    }
}
