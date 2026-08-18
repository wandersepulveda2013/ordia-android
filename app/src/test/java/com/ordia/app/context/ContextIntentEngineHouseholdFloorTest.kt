package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Piso de confianza simétrico para imperativos inequívocos de HOGAR
 * (c.638, paridad con c.613 TASK / c.619 REMINDER / c.626 SHOPPING+MEETING /
 * c.630 PAYMENT).
 *
 * Antes de c.638, los imperativos del hogar sin anclaje temporal ni "tengo
 * que/hay que" ("limpiar la cocina", "lavar los platos", "cocinar la cena",
 * "ordenar el cuarto", "arreglar el grifo", "planchar las camisas") quedaban
 * bajo [ContextIntentEngine.MINIMUM_CONFIDENCE] (0.27) y se DESCARTABAN
 * silenciosamente en la captura de contexto: el usuario capturaba una tarea
 * del hogar real y Ordía la olvidaba. La vía de captura manual
 * ([UniversalCaptureEngine]) SÍ promovía estos verbos a TASK (c.583), así que
 * la asimetría pasiva↔manual era una rendija de olvido P1. "limpiar/lavar/
 * cocinar/ordenar/arreglar/planchar <objeto>" son intenciones inequívocas con
 * independencia de fecha/hora, igual que "comprar <producto>" (c.626) o
 * "pagar <factura>" (c.630). El contenido dañino genuino ya fue bloqueado en
 * el paso 1 ([ContextPrivacyFilter]) o en el paso 3 (insultos), así que llegar
 * aquí es contenido permitido. El ancla `^` + `\s+\w` exige imperativo
 * AFIRMATIVO al inicio + objeto real: así "no limpiar la cocina" (negación,
 * capta lo opuesto a la intención del usuario), "mañana no limpiar la cocina"
 * (negación incrustada) y "limpiar" aislado (muletilla) NO activan el piso
 * (c.616 anti-overreach). Los casos afirmativos con ancla temporal
 * ("mañana limpiar la cocina") ya superan el umbral vía [extractDateTime].
 */
class ContextIntentEngineHouseholdFloorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(
                ContextCaptureSource.NOTIFICATION,
                raw,
                1723939200000L
            )
        )

    // --- HOGAR: imperativo + objeto, sin anclaje temporal ---

    @Test
    fun householdLimpiarCocinaIsCaptured() {
        val intent = analyze("limpiar la cocina")
        assertNotNull("limpiar la cocina es una tarea del hogar legítima, no debe descartarse", intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
    }

    @Test
    fun householdLavarPlatosIsCaptured() {
        val intent = analyze("lavar los platos")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
    }

    @Test
    fun householdCocinarCenaIsCaptured() {
        val intent = analyze("cocinar la cena")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
    }

    @Test
    fun householdOrdenarCuartoIsCaptured() {
        val intent = analyze("ordenar el cuarto")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
    }

    @Test
    fun householdArreglarGrifoIsCaptured() {
        val intent = analyze("arreglar el grifo")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
    }

    @Test
    fun householdPlancharCamisasIsCaptured() {
        val intent = analyze("planchar las camisas")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
    }

    @Test
    fun householdRepararLavadoraIsCaptured() {
        val intent = analyze("reparar la lavadora")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
    }

    @Test
    fun householdLimpiarCocinaFormsCleanTitle() {
        // El título capitaliza el verbo y conserva el objeto sin residuo.
        val intent = analyze("limpiar la cocina")
        assertNotNull(intent)
        assertEquals("Limpiar la cocina", intent!!.title)
    }

    // --- Guards anti-muletilla: el objeto real es obligatorio ---

    @Test
    fun bareLimpiarAloneDoesNotTriggerFloor() {
        // "limpiar" sin objeto NO activa el piso (muletilla). Su confianza queda
        // bajo el umbral por sí sola; el piso exige objeto.
        val intent = analyze("limpiar")
        assertNull("'limpiar' aislado no debe capturarse como tarea del hogar", intent)
    }

    // --- Guards anti-overreach (c.616): negación no activa el piso ---

    @Test
    fun negatedLimpiarDoesNotTriggerFloor() {
        // "no limpiar la cocina": el usuario declina la tarea. Capturarla como
        // HOUSEHOLD sería un falso positivo grave (datos erróneos: la app crea una
        // tarea que el usuario NO quiere). El piso de c.638 NO debe activar.
        val intent = analyze("no limpiar la cocina")
        assertNull("negación 'no limpiar la cocina' no debe capturarse como HOUSEHOLD", intent)
    }

    @Test
    fun negatedCocinarDoesNotTriggerFloor() {
        val intent = analyze("no cocinar la cena")
        assertNull("negación 'no cocinar la cena' no debe capturarse como HOUSEHOLD", intent)
    }

    // --- c.639: verbos domésticos comunes que faltaban en la cobertura léxica
    // (fregar/barrer/trapear/regar/sacudir/desempolvar). Antes de c.639 estos
    // verbos no estaban en la lista de palabras clave de HOUSEHOLD ni en
    // scoreSpecificPatterns ni en el piso ni en extractTitle, así que
    // "fregar los platos"/"barrer el patio"/"trapear el piso"/"regar las
    // plantas"/"sacudir los muebles"/"desempolvar la estantería" se descartaban
    // silenciosamente — misma rendija de olvido que c.638 cubrió para los otros
    // verbos. c.639 los añade en lockstep a las 4 listas para cerrar la brecha.

    @Test
    fun householdFregarPlatosIsCaptured() {
        val intent = analyze("fregar los platos")
        assertNotNull("fregar los platos es una tarea del hogar legítima, no debe descartarse", intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
    }

    @Test
    fun householdBarrerPatioIsCaptured() {
        val intent = analyze("barrer el patio")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
    }

    @Test
    fun householdTrapearPisoIsCaptured() {
        val intent = analyze("trapear el piso")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
    }

    @Test
    fun householdRegarPlantasIsCaptured() {
        val intent = analyze("regar las plantas")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
    }

    @Test
    fun householdSacudirMueblesIsCaptured() {
        val intent = analyze("sacudir los muebles")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
    }

    @Test
    fun householdDesempolvarEstanteriaIsCaptured() {
        val intent = analyze("desempolvar la estantería")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
    }

    @Test
    fun householdFregarPlatosFormsCleanTitle() {
        val intent = analyze("fregar los platos")
        assertNotNull(intent)
        assertEquals("Fregar los platos", intent!!.title)
    }

    // --- Guards: los verbos metafóricos/casuales NO se capturan por la
    // negación incrustada o el uso fuera-de-hogar (c.616 anti-overreach). El
    // piso exige ancla `^` + objeto, así que las negaciones y los usos
    // conversacionales quedan fuera. ---

    @Test
    fun negatedRegarDoesNotTriggerFloor() {
        val intent = analyze("no regar las plantas")
        assertNull("negación 'no regar las plantas' no debe capturarse como HOUSEHOLD", intent)
    }
}
