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
 * (c.616 anti-overreach).
 *
 * c.643 corrigió una afirmación FALSA de c.638: se asumía que los imperativos
 * del hogar con ancla temporal ("mañana limpiar la cocina") ya superaban el
 * umbral vía [ContextIntentEngine.extractDateTime], pero el bono temporal NO
 * eleva la confianza por encima de [ContextIntentEngine.MINIMUM_CONFIDENCE]
 * para NINGÚN verbo del hogar — todos se descartaban silenciosamente. El
 * ancla `^` fue la causa: exigía el verbo al inicio, así cualquier prefijo
 * temporal ("mañana"/"hoy"/"el lunes"/"esta noche") lo bloqueaba. c.643 quita
 * el ancla `^` y la sustituye por un lookbehind `(?<!no )` que bloquea sólo
 * la negación inmediata, admitiendo el verbo en cualquier posición. Así el
 * piso activa con prefijo temporal ("mañana limpiar la cocina"), verbo al
 * inicio ("limpiar la cocina") y verbo en mitad ("voy a limpiar la cocina"),
 * todos legítimos, sin abrir la negación (c.616 anti-overreach intacto).
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

    // --- c.643: prefijo temporal. Antes de c.643 el ancla `^` del piso exigía el
    // verbo al INICIO, así cualquier prefijo temporal ("mañana"/"hoy"/"el lunes"/
    // "esta noche") lo bloqueaba y TODO imperativo del hogar con ancla temporal
    // se descartaba silenciosamente. La afirmación de c.638 ("ya superan el
    // umbral vía extractDateTime") era FALSA: el bono temporal NO eleva la
    // confianza por encima de MINIMUM_CONFIDENCE para ningún verbo. c.643 quita
    // el ancla `^` (lookbehind `(?<!no )` en su lugar) para admitir el verbo en
    // cualquier posición, sin abrir la negación (c.616 anti-overreach intacto).

    @Test
    fun temporalPrefixLimpiarCocinaIsCaptured_c640() {
        // Antes: null (OLVIDO). El bono temporal no elevaba la confianza al umbral.
        val intent = analyze("mañana limpiar la cocina")
        assertNotNull("'mañana limpiar la cocina' es una tarea del hogar con fecha, no debe descartarse", intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
    }

    @Test
    fun temporalPrefixBarrerPatioIsCaptured_c640() {
        val intent = analyze("mañana barrer el patio")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
    }

    @Test
    fun temporalPrefixRegarPlantasIsCaptured_c640() {
        val intent = analyze("mañana regar las plantas")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
    }

    @Test
    fun temporalPrefixCocinarCenaIsCaptured_c640() {
        val intent = analyze("mañana cocinar la cena")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
    }

    @Test
    fun temporalPrefixTodayBarrerPatioIsCaptured_c640() {
        // "hoy" como prefijo temporal: antes descartado silenciosamente por el ancla `^`.
        val intent = analyze("hoy barrer el patio")
        assertNotNull("'hoy barrer el patio' no debe descartarse", intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
    }

    @Test
    fun temporalPrefixWeekdayBarrerPatioIsCaptured_c640() {
        // "el lunes": prefijo temporal con día de semana. Antes descartado.
        val intent = analyze("el lunes barrer el patio")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
    }

    @Test
    fun temporalPrefixTonightTrapearPisoIsCaptured_c640() {
        // "esta noche": prefijo temporal compuesto. Antes descartado.
        val intent = analyze("esta noche trapear el piso")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
    }

    @Test
    fun temporalPrefixFormsCleanTitle_c640() {
        // El prefijo temporal se consume en el título: "mañana" no debe aparecer.
        val intent = analyze("mañana limpiar la cocina")
        assertNotNull(intent)
        assertEquals("Limpiar la cocina", intent!!.title)
    }

    @Test
    fun temporalPrefixResolvesDueAt_c640() {
        // El verbo del hogar con ancla temporal produce una tarea AGENDADA (dueAt
        // resuelto a mañana), no una tarea sin vencimiento: antes se olvidaba del
        // todo (null), ahora sobrevive con su fecha.
        val intent = analyze("mañana limpiar la cocina")
        assertNotNull(intent)
        assertNotNull("la tarea con 'mañana' debe tener dueAt resuelto", intent!!.dueAt)
    }

    @Test
    fun midSentenceVerbIsCaptured_c640() {
        // El verbo en mitad ("voy a limpiar la cocina") también es legítimo tras
        // quitar el ancla `^`: el lookbehind `(?<!no )` admite cualquier prefijo
        // afirmativo.
        val intent = analyze("voy a limpiar la cocina")
        assertNotNull("'voy a limpiar la cocina' es una intención del hogar legítima", intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
    }

    // --- Guards c.643: la negación con prefijo temporal sigue bloqueada ---

    @Test
    fun temporalPrefixNegatedLimpiarDoesNotTriggerFloor_c640() {
        // "mañana no limpiar la cocina": negación incrustada tras prefijo temporal.
        // El lookbehind `(?<!no )` bloquea el verbo precedido por "no " → no activa.
        val intent = analyze("mañana no limpiar la cocina")
        assertNull("negación incrustada 'mañana no limpiar la cocina' no debe capturarse como HOUSEHOLD", intent)
    }

    @Test
    fun temporalPrefixNegatedBarrerDoesNotTriggerFloor_c640() {
        val intent = analyze("mañana no barrer el patio")
        assertNull("negación 'mañana no barrer el patio' no debe capturarse como HOUSEHOLD", intent)
    }
}
