package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Captura del futuro declarativo de CALL (c.656): "llamaré/hablaré + objeto".
 *
 * Defecto descubierto por probe JVM fuente real: el futuro declarativo de
 * 1ª persona con objeto explícito ("llamaré a mamá el viernes", "hablaré con
 * el banco la semana que viene") se DESCARTABA (kind = null): las palabras
 * clave y los patrones específicos de CALL sólo contemplaban el infinitivo
 * ("llamar a", "hablar con") y el futuro se marcaba únicamente con un bono
 * genérico +0.08, insuficiente. Es el MISMO olvido (P1) que c.654 cerró para
 * APPOINTMENT: una promesa explícita en indefinido, 1ª persona, es evidencia
 * MÁS firme de llamada que el infinitivo.
 *
 * La solución reutiliza la fuente única de [CALL_SPECIFIC] (c.653): dos
 * patrones de futuro con objeto explícito ("llamaré a/al/a la/..." —
 * "hablaré con") alimentan el bono específico [scoreSpecificPatterns] y el
 * guard de envolvente [imperativeIsWrapped] (así "recuérdame llamaré a mamá"
 * sigue siendo TASK). El bono fusionado (patrón+objeto, 0.45) alcanza
 * [MINIMUM_CONFIDENCE] sin fecha y crece con la fecha. La extracción de
 * título de CALL arranca también en el verbo de futuro, así la fecha prefija
 * ("mañana llamaré...") no ensucia el título — iguales a los del infinitivo.
 *
 * Cobertura:
 * - 4 casos futuro CALL (RED pre-fix → GREEN), con título limpio.
 * - 1 solo-objeto-sin-futuro → NULL (no muletillas).
 * - 1 envolvente → TASK (guard en el futuro).
 * - 2 controles infinitivo (CALL) sin cambio.
 */
class ContextIntentEngineFutureCallTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(
                ContextCaptureSource.NOTIFICATION,
                raw,
                1723939200000L
            )
        )

    // --- Futuro CALL: RED pre-fix → GREEN ---

    @Test
    fun hablareConElBancoLaSemanaQueVieneIsCall() {
        val intent = analyze("hablaré con el banco la semana que viene")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.CALL, intent!!.kind)
        assertEquals("Hablaré con el banco", intent.title)
    }

    @Test
    fun mananaLlamareAMamaIsCall() {
        val intent = analyze("mañana llamaré a mamá")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.CALL, intent!!.kind)
        assertEquals("Llamaré a mamá", intent.title)
    }

    @Test
    fun llamareAMamaElViernesIsCall() {
        val intent = analyze("llamaré a mamá el viernes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.CALL, intent!!.kind)
        assertEquals("Llamaré a mamá", intent.title)
    }

    @Test
    fun hablareConMiJefeMananaIsCall() {
        val intent = analyze("hablaré con mi jefe mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.CALL, intent!!.kind)
        assertEquals("Hablaré con mi jefe", intent.title)
    }

    // --- Futuro SIN objeto: siguen descartadas (no muletillas) ---

    @Test
    fun bareFutureWithoutObjectDiscarded() {
        assertNull(analyze("llamaré el viernes"))
    }

    // --- Envolvente: TASK gobierna incluso sobre el futuro ---

    @Test
    fun recordameLlamareAMamaStaysTask() {
        val intent = analyze("recuérdame llamaré a mamá")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    // --- Controles infinitivo: sin cambio ---

    @Test
    fun llamarAlBancoInfinitiveStillCall() {
        val intent = analyze("llamar al banco el jueves")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.CALL, intent!!.kind)
        assertEquals("Llamar al banco", intent.title)
    }

    @Test
    fun hablarConMiJefeInfinitivStillCall() {
        val intent = analyze("hablar con mi jefe mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.CALL, intent!!.kind)
        assertEquals("Hablar con mi jefe", intent.title)
    }
}
