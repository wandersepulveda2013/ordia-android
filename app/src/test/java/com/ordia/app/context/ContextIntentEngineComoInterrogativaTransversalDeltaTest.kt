package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1071 (P2 precisión — TRANSVERSAL interrogativa «cómo + infinitivo» →
 * captura). Lateral ABIERTA documentada en c.1062/c.1066 y medida por SU
 * c.1066 en 4 pisos de mascota (pastilla c.1012, vacuna c.1011, uñas
 * c.1015, sacar c.740) + medicamento (c.1066), pineada ESTABLE a la espera
 * de doctrina: una pregunta how-to («cómo darle la pastilla al perro») NO
 * es un compromiso, pero se persistía como tarea firme (HOUSEHOLD 0.45,
 * CALL 0.57, APPOINTMENT 0.67, EXERCISE 0.59, SHOPPING/ERRAND 0.45) —
 * bandeja degradada con items que el usuario nunca se comprometió a hacer
 * (misma clase P1/P2 que la duda c.649). Medición PRE c.1071 (sonda
 * efímera `/tmp/probe1071/Probe.kt`, motor real vía run_probe.sh): 15
 * capturas de interrogativa confirmadas en TODA la familia de pisos de
 * posición libre (mascota ×9 con variantes ¿?/fecha/hora, CALL,
 * APPOINTMENT, SHOPPING «hacer la compra», EXERCISE «ir al gimnasio»,
 * ERRAND «cortarse el pelo»); los pisos anclados `^` (comprar pan c.626,
 * pagar, tomar la pastilla humana c.859) ya eran NULL estructuralmente.
 * Doctrina: la pregunta how-to no expresa intención de hacer la acción
 * (a diferencia de la duda c.649, que no niega la intención y penaliza),
 * así que se BLOQUEA (NULL conservador, hermano de los guards de negación
 * c.648/c.681/c.1009), no se penaliza. Acotado posicionalmente: sólo
 * «cómo» AL INICIO del mensaje (tras «¿» opcional) + infinitivo español
 * (con enclíticos, hermano de [INFINITIVE_LIKE] c.1064); el «cómo»
 * subordinado de contenido («recuérdame cómo hacer la compra» → TASK,
 * «tengo que pensar cómo sacar al perro» → TASK) es compromiso legítimo
 * y NO se toca. CERO keywords nuevas (lección c.751): un guard transversal
 * en [scoreKind], un punto, todos los kinds. Determinista (regex), sin
 * random, sin IA fingida.
 */
class ContextIntentEngineComoInterrogativaTransversalDeltaTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Interrogativa how-to «cómo + infinitivo» → NULL (15 capturas medidas) ---

    @Test
    fun comoDarlePastilla_quedaNull() {
        assertNull(analyze("cómo darle la pastilla al perro"))
    }

    @Test
    fun comoDarleMedicamento_quedaNull() {
        assertNull(analyze("cómo darle el medicamento al perro"))
    }

    @Test
    fun comoPonerleVacuna_quedaNull() {
        assertNull(analyze("cómo ponerle la vacuna al gato"))
    }

    @Test
    fun comoCortarleUnas_quedaNull() {
        assertNull(analyze("cómo cortarle las uñas al gato"))
    }

    @Test
    fun comoSacarPerro_quedaNull() {
        assertNull(analyze("cómo sacar al perro"))
    }

    @Test
    fun comoDarleUnaPastilla_quedaNull() {
        assertNull(analyze("cómo darle una pastilla al gato"))
    }

    @Test
    fun comoLlamarMama_quedaNull() {
        assertNull(analyze("cómo llamar a mamá"))
    }

    @Test
    fun comoIrAlMedico_quedaNull() {
        assertNull(analyze("cómo ir al médico"))
    }

    @Test
    fun comoHacerLaCompra_quedaNull() {
        assertNull(analyze("cómo hacer la compra"))
    }

    @Test
    fun comoIrAlGimnasio_quedaNull() {
        assertNull(analyze("cómo ir al gimnasio"))
    }

    @Test
    fun comoCortarseElPelo_quedaNull() {
        assertNull(analyze("cómo cortarse el pelo"))
    }

    @Test
    fun comoConSignosInterrogacion_quedaNull() {
        assertNull(analyze("¿cómo darle la pastilla al perro?"))
    }

    @Test
    fun comoConFecha_quedaNull() {
        assertNull(analyze("cómo sacar al perro mañana"))
    }

    @Test
    fun comoBanarPerro_quedaNull() {
        assertNull(analyze("cómo bañar al perro"))
    }

    @Test
    fun comoConHora_quedaNull() {
        assertNull(analyze("cómo darle la pastilla al perro a las 9"))
    }

    // --- Regresión: compromiso directo idéntico SIN «cómo» sigue capturando ---

    @Test
    fun regresionDarlePastilla_sigueHousehold() {
        val i = analyze("darle la pastilla al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Darle la pastilla al perro", i.title)
    }

    @Test
    fun regresionLlamarMama_sigueCall() {
        val i = analyze("llamar a mamá")
        assertNotNull(i)
        assertEquals(ContextIntentKind.CALL, i!!.kind)
    }

    @Test
    fun regresionSacarPerro_sigueHousehold() {
        val i = analyze("sacar al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
    }

    @Test
    fun regresionComprarPan_sigueShopping() {
        val i = analyze("comprar pan")
        assertNotNull(i)
        assertEquals(ContextIntentKind.SHOPPING, i!!.kind)
    }

    @Test
    fun regresionIrAlGimnasio_sigueExercise() {
        val i = analyze("ir al gimnasio")
        assertNotNull(i)
        assertEquals(ContextIntentKind.EXERCISE, i!!.kind)
    }

    // --- «cómo» subordinado de contenido (NO inicial): compromiso legítimo intacto ---

    @Test
    fun recordameComoHacerLaCompra_sigueTask() {
        val i = analyze("recuérdame cómo hacer la compra")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
    }

    @Test
    fun tengoQuePensarComoSacar_sigueTask() {
        val i = analyze("tengo que pensar cómo sacar al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
    }

    // --- Guards/NULL estructurales que no deben cambiar ---

    @Test
    fun comoEstas_sigueNull() {
        assertNull(analyze("cómo estás"))
    }

    @Test
    fun comoVaElProyecto_sigueNull() {
        assertNull(analyze("cómo va el proyecto"))
    }

    @Test
    fun comoLlegoAlTrabajo_sigueNull() {
        assertNull(analyze("cómo llego al trabajo"))
    }

    @Test
    fun comoTomarUnaPastilla_sigueNull() {
        // Pin c.1062 FUERA3: el piso humano anclado ya era NULL estructuralmente.
        assertNull(analyze("cómo tomar una pastilla"))
    }

    @Test
    fun comoComprarPan_sigueNull() {
        // Piso de compra anclado (c.626/c.651): ya NULL estructuralmente.
        assertNull(analyze("cómo comprar pan"))
    }

    @Test
    fun comoPagarLaLuz_sigueNull() {
        assertNull(analyze("cómo pagar la luz"))
    }
}
