package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Wrapper "falta <infinitivo>" / "hace falta <infinitivo>" (construcción
 * impersonal de obligación) en la captura contextual (c.685).
 *
 * La sonda de descubrimiento de c.681 constató olvido silencioso P1 en
 * [ContextIntentEngine.analyze]: "falta comprar detergente" se descartaba
 * (NULL) porque el piso de TASK (c.613) no reconocía el envolvente "falta".
 * En español "falta comprar X" equivale a "hay que comprar X": una tarea
 * clara aunque no mencione fecha (una forma por ciclo, doctrina
 * anti-overreach de c.681).
 *
 * Anti-overreach: el verbo que sigue a "falta" debe ser INFINITIVO
 * ("falta comprar…", "falta pagar…"). Así el uso temporal ("falta una hora
 * para la reunión", "faltan cinco minutos"), el sustantivo ("cometió una
 * falta grave") y la forma personal ("me falta tu apoyo") NO se capturan.
 * La negación ("no falta comprar detergente" = no hace falta, lo opuesto de
 * la intención) queda bloqueada por el lookbehind `(?<!no )`.
 *
 * Las formas con kind subordinado ceden el kind al envolvente (lección
 * c.653): "falta llamar al banco" es la TAREA de llamar, no una llamada
 * autónoma (vía [ContextIntentEngine]'s WRAPPER_PATTERN + imperativeIsWrapped).
 */
class ContextIntentEngineFaltaWrapperTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(
                ContextCaptureSource.NOTIFICATION,
                raw,
                1723939200000L
            )
        )

    // --- Captura: "falta <infinitivo>" debe ser TASK ---

    @Test
    fun faltaComprarDetergenteIsCapturedAsTask() {
        val intent = analyze("falta comprar detergente")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Comprar detergente", intent.title)
    }

    @Test
    fun faltaPagarLaRentaIsCapturedAsTask() {
        val intent = analyze("falta pagar la renta")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Pagar la renta", intent.title)
    }

    @Test
    fun haceFaltaComprarLecheIsCapturedAsTask() {
        val intent = analyze("hace falta comprar leche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Comprar leche", intent.title)
    }

    @Test
    fun haceFaltaRenovarElSeguroIsCapturedAsTask() {
        val intent = analyze("hace falta renovar el seguro")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Renovar el seguro", intent.title)
    }

    @Test
    fun faltaHacerLaCompraIsCapturedAsTask() {
        val intent = analyze("falta hacer la compra")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer la compra", intent.title)
    }

    // --- El envolvente gobierna el kind del verbo subordinado (c.653) ---

    @Test
    fun faltaLlamarAlBancoMananaIsTaskNotCall() {
        val intent = analyze("falta llamar al banco mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Llamar al banco", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun faltaIrAlGimnasioIsTaskNotExercise() {
        val intent = analyze("falta ir al gimnasio")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun faltaLlevarElCocheAlTallerIsTaskNotErrand() {
        val intent = analyze("falta llevar el coche al taller")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Llevar el coche al taller", intent.title)
    }

    // --- Anti-overreach: usos no-obligación de "falta" NO se capturan ---

    @Test
    fun faltaUnaHoraTemporalStaysNull() {
        assertNull(analyze("falta una hora para la reunión"))
    }

    @Test
    fun faltanCincoMinutosTemporalStaysNull() {
        assertNull(analyze("faltan cinco minutos"))
    }

    @Test
    fun unaFaltaGraveNounStaysNull() {
        assertNull(analyze("cometió una falta grave"))
    }

    @Test
    fun noFaltaComprarNegatedStaysNull() {
        assertNull(analyze("no falta comprar detergente"))
    }

    @Test
    fun meFaltaTuApoyoPersonalStaysNull() {
        assertNull(analyze("me falta tu apoyo"))
    }

    // --- Regresión: los wrappers existentes siguen intactos ---

    @Test
    fun recuerdaComprarLecheStillTask() {
        val intent = analyze("recuerda comprar leche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Comprar leche", intent.title)
    }
}
