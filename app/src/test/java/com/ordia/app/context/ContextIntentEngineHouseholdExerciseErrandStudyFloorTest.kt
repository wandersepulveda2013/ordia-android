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

    // --- c.647: olvido silencioso con prefijo temporal ---
    // El ancla `^` original descartaba todo imperativo con prefijo temporal
    // ("mañana correr 5k"/"mañana ir al banco"/"mañana repasar la lección"/
    // "mañana reunión con el equipo") porque la "reunión"/"correr"/"ir"/
    // "repasar" no estaba al INICIO. El bono temporal NO compensaba la base
    // baja, así el caso se DESCARTABA (NULL): el usuario capturaba una
    // intención real y Ordía la olvidaba (P1). c.647 quitó el ancla `^` y
    // añadió el lookbehind `(?<!no )` (negación inmediata sigue bloqueada).
    // Estos tests fijan la regresión: el caso con prefijo temporal debe
    // capturarse, y la negación incrustada con prefijo temporal no.

    @Test
    fun c647_exerciseCorrerConPrefijoTemporalSeCaptura() {
        val intent = analyze("mañana correr 5k")
        assertNotNull("'mañana correr 5k' es ejercicio legítimo con ancla temporal, no debe descartarse (c.647)", intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
    }

    @Test
    fun c647_errandIrAlBancoConPrefijoTemporalSeCaptura() {
        val intent = analyze("mañana ir al banco")
        assertNotNull("'mañana ir al banco' es trámite legítimo con ancla temporal, no debe descartarse (c.647)", intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
    }

    @Test
    fun c647_errandRecogerPaqueteConPrefijoTemporalSeCaptura() {
        val intent = analyze("hoy recoger el paquete")
        assertNotNull("'hoy recoger el paquete' es trámite legítimo con ancla temporal, no debe descartarse (c.647)", intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
    }

    @Test
    fun c647_studyRepasarLeccionConPrefijoTemporalSeCaptura() {
        val intent = analyze("mañana repasar la lección")
        assertNotNull("'mañana repasar la lección' es estudio legítimo con ancla temporal, no debe descartarse (c.647)", intent)
        assertEquals(ContextIntentKind.STUDY, intent!!.kind)
    }

    @Test
    fun c647_studyPrepararExamenConPrefijoTemporalSeCaptura() {
        val intent = analyze("hoy preparar el examen")
        assertNotNull("'hoy preparar el examen' es estudio legítimo con ancla temporal, no debe descartarse (c.647)", intent)
        assertEquals(ContextIntentKind.STUDY, intent!!.kind)
    }

    @Test
    fun c647_meetingReunionConEquipoConPrefijoTemporalSeCaptura() {
        val intent = analyze("mañana reunión con el equipo")
        assertNotNull("'mañana reunión con el equipo' es reunión legítima con ancla temporal, no debe descartarse (c.647)", intent)
        assertEquals(ContextIntentKind.MEETING, intent!!.kind)
    }

    @Test
    fun c647_meetingReunionDeProyectoConPrefijoTemporalSeCaptura() {
        val intent = analyze("hoy reunión de proyecto")
        assertNotNull("'hoy reunión de proyecto' es reunión legítima con ancla temporal, no debe descartarse (c.647)", intent)
        assertEquals(ContextIntentKind.MEETING, intent!!.kind)
    }

    // --- c.647: negación incrustada con prefijo temporal sigue bloqueada ---
    // El lookbehind `(?<!no )` bloquea la negación inmediata incluso con
    // prefijo temporal delante: "mañana no correr 5k"/"mañana no ir al banco"
    // NO deben capturarse (c.616 anti-overreach: capturar lo opuesto a la
    // intención del usuario es peor que no capturar nada).

    @Test
    fun c647_negacionIncrustadaExerciseNoSeCaptura() {
        val intent = analyze("mañana no correr 5k")
        assertNull("'mañana no correr 5k' (negación incrustada) no debe capturarse como EXERCISE (c.616/c.647)", intent)
    }

    @Test
    fun c647_negacionIncrustadaErrandNoSeCaptura() {
        val intent = analyze("mañana no ir al banco")
        assertNull("'mañana no ir al banco' (negación incrustada) no debe capturarse como ERRAND (c.616/c.647)", intent)
    }

    @Test
    fun c647_negacionIncrustadaMeetingNoSeCaptura() {
        val intent = analyze("mañana no reunión con el equipo")
        assertNull("'mañana no reunión con el equipo' (negación incrustada) no debe capturarse como MEETING (c.616/c.647)", intent)
    }

    // Nota c.648 (overreach via bono temporal, NO arreglado por c.647): el caso
    // "mañana no repasar la lección" SÍ se captura (0.49) porque la keyword
    // "lección" + el bono temporal elevan el score por encima de
    // [MINIMUM_CONFIDENCE] SIN necesidad del piso (el lookbehind del piso
    // bloquea correctamente, pero el bono bypassa el piso). Mismo mecanismo que
    // el overreach de SHOPPING/PAYMENT. Es un defecto de clase distinto (c.648),
    // registrado en BACKLOG, fuera del alcance de c.647 (olvido silencioso).
}
