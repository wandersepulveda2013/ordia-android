package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * c.690 (P2 título que MIENTE en captura pasiva, ítem BACKLOG "títulos
 * context" descubierto c.681, un ítem por ciclo): dos defectos de
 * `stripTrailingTemporalResidue` verificados con sonda JVM fuente real
 * (`tools/probe/TitleResidueProbe.kt`, PRE-fix):
 *
 * (a) Residuo "pasado" huérfano — "tengo que entregar el informe pasado
 *     mañana a las 10" → título 'Entregar el informe pasado' (dueAt
 *     correcto, título MIENTE: "el informe pasado" ≠ "pasado mañana").
 *     Causa: el `bareTail` (días relativos desnudos) corta "mañana" ANTES de
 *     que el compuesto "pasado mañana" (alternativa `date` de `tail`) pueda
 *     consumirse; en la iteración siguiente ya no hay compuesto.
 *     Fix: "pasado" entra en el guard del `bareTail` (nunca cortar un
 *     relativo precedido de "pasado": lo gestiona la alternativa de cola).
 *
 * (b) Corte a media palabra — "comprar entradas para el concierto del
 *     viernes" → título 'Comprar entradas para el concierto d'.
 *     Causa: el artículo opcional del weekday de cola (`(?:el|este)\s+`)
 *     casa con el "el" INTERIOR de "del" (leftmost sin límite de palabra):
 *     la coincidencia empieza dentro de "del" y deja la 'd' colgando.
 *     Fix: el artículo opcional admite "del" (genitivo natural ante
 *     weekday: "concierto del viernes"), así la coincidencia leftmost
 *     empieza en el conector completo y el título queda limpio.
 *
 * Controles (ya correctos pre-fix, deben permanecer): "pagar la renta
 * pasado mañana", "comprar leche mañana", "recuérdame llamar a mamá el
 * viernes a las 3", "hacer ejercicio por la mañana" (c.688), genitivo
 * "el diario de hoy" no se despoja (vía guard de `bareTail`).
 * Determinista (regex), sin random, sin IA fingida.
 */
class ContextIntentEngineTitleResidueFixTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- (a) "pasado mañana" compuesto nunca se rompe ---

    @Test
    fun pasadoMananaWithTimeLeavesNoOrphanPasado() {
        val intent = analyze("tengo que entregar el informe pasado mañana a las 10")
        assertNotNull(intent)
        assertEquals("Entregar el informe", intent!!.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun pasadoMananaUnaccentedWithTimeLeavesNoOrphanPasado() {
        // Sin tilde y CON hora: mismo defecto (a) — el bareTail corta
        // "manana" y deja "pasado" huérfano.
        val intent = analyze("tengo que entregar el informe pasado manana a las 8")
        assertNotNull(intent)
        assertEquals("Entregar el informe", intent!!.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun pasadoMananaBareStillClean() {
        // Control: sin hora de cola ya funcionaba; no debe regresar.
        val intent = analyze("pagar la renta pasado mañana")
        assertNotNull(intent)
        assertEquals("Pagar la renta", intent!!.title)
        assertNotNull(intent.dueAt)
    }

    // --- (b) weekday genitivo "del viernes" no deja media palabra ---

    @Test
    fun delWeekdayLeavesNoOrphanLetter() {
        val intent = analyze("comprar entradas para el concierto del viernes")
        assertNotNull(intent)
        assertEquals("Comprar entradas para el concierto", intent!!.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun delWeekdaySabadoLeavesNoOrphanLetter() {
        val intent = analyze("comprar entradas para el cine del sábado")
        assertNotNull(intent)
        assertEquals("Comprar entradas para el cine", intent!!.title)
    }

    // --- Controles / regresión (pasaban pre-fix) ---

    @Test
    fun elWeekdayStillStripsFully() {
        val intent = analyze("recuérdame llamar a mamá el viernes a las 3")
        assertNotNull(intent)
        assertEquals("Llamar a mamá", intent!!.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun bareMananaStillStrips() {
        val intent = analyze("comprar leche mañana")
        assertNotNull(intent)
        assertEquals("Comprar leche", intent!!.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun softBandStillStrips() {
        val intent = analyze("hacer ejercicio por la mañana")
        assertNotNull(intent)
        assertEquals("Hacer ejercicio", intent!!.title)
    }

    @Test
    fun nonWeekdayDelTailUntouched() {
        // "del" sin weekday no es residuo temporal: no se toca.
        val intent = analyze("comprar entradas del concierto")
        assertNotNull(intent)
        assertEquals("Comprar entradas del concierto", intent!!.title)
    }

    @Test
    fun genitiveGuardStillProtectsDeAyer() {
        // Genitivo existente: "de ayer" no se despoja (guard de bareTail).
        val intent = analyze("tengo que revisar el informe de ayer")
        assertNotNull(intent)
        assertEquals("Revisar el informe de ayer", intent!!.title)
    }

    @Test
    fun singularNightBandStripsWithPasadoManana() {
        // c.690b: bandTail sólo listaba "noches" (plural); "por la noche"
        // (singular, caso común) quedaba entero en el título junto al
        // compuesto "pasado mañana" ya protegido por el guard. c.717: el
        // envolvente "recuérdame" gobierna en TASK (bono específico de
        // REMINDER restringido a sinónimos puros de aviso).
        val intent = analyze("recuérdame sacar la basura pasado mañana por la noche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertNotNull(intent.dueAt)
        assertEquals("Sacar la basura", intent.title)
    }

    @Test
    fun singularNightBandAloneStrips() {
        // Ruta greedy ("tengo que ...") para que el título pase por el
        // bucle de residuo: sin el fix quedaba " por la noche" entero.
        val intent = analyze("tengo que regar las plantas por la noche")
        assertNotNull(intent)
        assertEquals("Regar las plantas", intent!!.title)
    }
}
