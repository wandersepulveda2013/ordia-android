package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Piso de confianza simétrico para imperativos inequívocos de EJERCICIO,
 * DILIGENCIA y ESTUDIO (c.639, paridad con c.613 TASK / c.619 REMINDER /
 * c.626 COMPRA·REUNIÓN / c.630 PAGO / c.638 DOMÉSTICO).
 *
 * Antes de c.639, notificaciones como "correr 5k"/"ir al banco"/
 * "repasar la lección"/"preparar el examen" quedaban bajo
 * [ContextIntentEngine.MINIMUM_CONFIDENCE] por la sola ausencia de pistas
 * temporales y se DESCARTABAN silenciosamente: el usuario capturaba una
 * actividad real y Ordía la olvidaba (P1 evitar olvidos). Estas frases son
 * intenciones inequívocas con independencia de fecha/hora, igual que los
 * pisos anteriores. El contenido dañino genuino ya fue bloqueado en pasos
 * 1/3, así que llegar aquí es contenido permitido. El ancla `^` + `\s+\w`
 * exige imperativo AFIRMATIVO al inicio + objeto/destino real: así
 * "no correr hoy" (negación), "mañana no limpiar" (negación incrustada) y
 * "correr"/"limpiar" aislados (muletillas) NO activan el piso (c.616
 * anti-overreach). Los casos afirmativos con ancla temporal ("mañana correr
 * 5k") ya superan el umbral vía [extractDateTime].
 *
 * Nota: las pruebas de DOMÉSTICO (HOUSEHOLD) viven en
 * [ContextIntentEngineHouseholdFloorTest] (c.638); este archivo cubre las 3
 * categorías restantes del mismo defecto de clase.
 */
class ContextIntentEngineHouseholdExerciseErrandStudyFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(
                ContextCaptureSource.NOTIFICATION,
                raw,
                1723939200000L
            )
        )

    // --- EJERCICIO: verbo de actividad física, sin anclaje temporal ---

    @Test
    fun exerciseCorrer5kIsCaptured() {
        val intent = analyze("correr 5k")
        assertNotNull("correr 5k es ejercicio legítimo, no debe descartarse", intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
    }

    @Test
    fun exerciseEntrenarPiernasIsCaptured() {
        val intent = analyze("entrenar piernas")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
    }

    @Test
    fun exerciseHacerYogaIsCaptured() {
        val intent = analyze("hacer yoga")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
    }

    @Test
    fun exerciseIrAlGimnasioStillCaptured() {
        val intent = analyze("ir al gimnasio")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
    }

    // --- DILIGENCIA: "ir a <destino de trámite>" o recoger/devolver, sin anclaje ---

    @Test
    fun errandIrAlBancoIsCaptured() {
        val intent = analyze("ir al banco")
        assertNotNull("ir al banco es un trámite legítimo, no debe descartarse", intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
    }

    @Test
    fun errandIrACorreosIsCaptured() {
        val intent = analyze("ir a correos")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
    }

    @Test
    fun errandRecogerPaqueteIsCaptured() {
        val intent = analyze("recoger el paquete")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
    }

    @Test
    fun errandDevolverLibroIsCaptured() {
        val intent = analyze("devolver el libro")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
    }

    @Test
    fun errandIrASucursalIsCaptured() {
        val intent = analyze("ir a la sucursal")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
    }

    // --- ESTUDIO: estudiar/repasar/preparar examen, sin anclaje temporal ---

    @Test
    fun studyRepasarLeccionIsCaptured() {
        val intent = analyze("repasar la lección")
        assertNotNull("repasar la lección es estudio legítimo, no debe descartarse", intent)
        assertEquals(ContextIntentKind.STUDY, intent!!.kind)
    }

    @Test
    fun studyPrepararElExamenIsCaptured() {
        val intent = analyze("preparar el examen")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.STUDY, intent!!.kind)
    }

    @Test
    fun studyEstudiarParaElExamenStillCaptured() {
        val intent = analyze("estudiar para el examen")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.STUDY, intent!!.kind)
    }

    // --- Guards anti-muletilla: el objeto real es obligatorio ---

    @Test
    fun bareCorrerAloneDoesNotTriggerFloor() {
        val intent = analyze("correr")
        assertNull("'correr' aislado (sin objeto) no debe capturarse vía el piso", intent)
    }

    @Test
    fun bareIrAlBancoAloneIsNotErrandMuletilla() {
        // "ir" aislado sin destino NO activa el piso ERRAND (muletilla).
        val intent = analyze("ir")
        assertNull("'ir' aislado (sin destino de trámite) no debe capturarse vía el piso ERRAND", intent)
    }

    // --- Guards anti-overreach (c.616): negación no activa el piso ---

    @Test
    fun negatedExerciseDoesNotTriggerFloor() {
        val intent = analyze("no correr hoy")
        assertNull("negación 'no correr hoy' no debe capturarse como EXERCISE", intent)
    }

    @Test
    fun negatedErrandDoesNotTriggerFloor() {
        val intent = analyze("no ir al banco")
        assertNull("negación 'no ir al banco' no debe capturarse como ERRAND", intent)
    }

    @Test
    fun negatedStudyDoesNotTriggerFloor() {
        val intent = analyze("no repasar la lección")
        assertNull("negación 'no repasar la lección' no debe capturarse como STUDY", intent)
    }

    // --- No-colisión con pisos/categorías previas ---

    @Test
    fun shoppingComprarPanStillShopping() {
        val intent = analyze("comprar pan")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.SHOPPING, intent!!.kind)
    }

    @Test
    fun paymentPagarLaLuzStillPayment() {
        val intent = analyze("pagar la luz")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
    }

    @Test
    fun meetingReunionConEquipoStillMeeting() {
        val intent = analyze("reunión con el equipo")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.MEETING, intent!!.kind)
    }
}
