package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Actividad de ejercicio inequívoca "hacer ejercicio" en la captura
 * contextual (c.688): última forma OPEN del ítem c.681 del BACKLOG.
 *
 * La sonda de descubrimiento (tools/probe/InterrogativeReminderProbe.kt,
 * sección FORMA B) constató olvido silencioso P1 en
 * [ContextIntentEngine.analyze]: "hacer ejercicio por la mañana",
 * "hacer ejercicio por las mañanas" y "hacer ejercicio mañana por la
 * mañana" se DESCARTABAN (NULL) porque el piso de EXERCISE (c.639/c.647)
 * sólo reconocía "correr|entrenar|natación|pesas", "ir al gimnasio" y
 * "hacer (yoga|pesas|deporte)". "Hacer ejercicio" es la forma más
 * genérica — y más cotidiana — de expresar la actividad física en
 * español, y debe capturar con la sola pista de franja blanda
 * ("por la mañana/tarde/noche") o incluso desnuda.
 *
 * Anti-overreach: el floor exige "ejercicio" en SINGULAR
 * (`ejercicio(?!\p{L})`), porque en plural "hacer ejercicios de
 * matemáticas" es DEBERES, no ejercicio físico. La negación inmediata
 * ("no hacer ejercicio") queda bloqueada por el lookbehind `(?<!no )`
 * del piso.
 */
class ContextIntentEngineHacerEjercicioTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(
                ContextCaptureSource.NOTIFICATION,
                raw,
                1723939200000L
            )
        )

    // --- Captura: "hacer ejercicio (+ franja opcional)" debe ser EXERCISE ---

    @Test
    fun hacerEjercicioSoloIsCapturedAsExercise() {
        val intent = analyze("hacer ejercicio")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Hacer ejercicio", intent.title)
    }

    @Test
    fun hacerEjercicioPorLaMañanaIsCapturedAsExercise() {
        val intent = analyze("hacer ejercicio por la mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        // La franja blanda de cola es residuo temporal: el título queda limpio.
        assertEquals("Hacer ejercicio", intent.title)
    }

    @Test
    fun hacerEjercicioPorLasMañanasIsCapturedAsExercise() {
        val intent = analyze("hacer ejercicio por las mañanas")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
    }

    @Test
    fun hacerEjercicioMañanaPorLaMañanaResolvesDueAt() {
        val intent = analyze("hacer ejercicio mañana por la mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun hacerEjercicioPorLaNocheIsCapturedAsExercise() {
        val intent = analyze("hacer ejercicio por la noche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
    }

    // --- Anti-overreach: el floor exige "ejercicio" en SINGULAR ---

    @Test
    fun pluralEjerciciosDeMatematicasIsNotExercise() {
        val intent = analyze("hacer ejercicios de matemáticas")
        assertNull(intent)
    }

    @Test
    fun bareEjercicioDeMatematicasIsNotExercise() {
        val intent = analyze("ejercicio de matemáticas por la mañana")
        assertNull(intent)
    }

    // --- Anti-overreach: negación inmediata bloqueada por el piso ---

    @Test
    fun negatedHacerEjercicioIsNotCaptured() {
        val intent = analyze("no hacer ejercicio")
        assertNull(intent)
    }

    // --- Regresión: las formas existentes del piso siguen intactas ---

    @Test
    fun correr5kStillCaptured() {
        val intent = analyze("correr 5k")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
    }

    @Test
    fun irAlGimnasioStillCaptured() {
        val intent = analyze("ir al gimnasio")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
    }

    @Test
    fun hacerYogaStillCaptured() {
        val intent = analyze("hacer yoga")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
    }
}
