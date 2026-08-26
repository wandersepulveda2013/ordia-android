package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1250: «clase(s) de <disciplina fitness>» (lateral (g) DISJUNTA de
 * MI auditoría c.1227, sonda persistida `tools/probe/ClaseFitnessProbe.kt`
 * PRE 7/7 NULL targets — olvido silencioso; la clase programada suena a
 * VAGA pese a ser un compromiso determinista). «clase» es nominal
 * bivalente (escuela), pero con objeto acotado a disciplinas fitness es
 * monosemántica → EXERCISE (gate c.751; CERO keyword nueva — «yoga» ya
 * keyword heredada, precedente «partido» c.1231). Lockstep DOS puntos
 * (lección c.616): piso acotado EXERCISE_CLASS_FLOOR + plantilla
 * matchClase en extractTitle. Kind: EXERCISE (TASK solo en envolvente
 * «recuérdame»/«tengo que», lección de archivo del wrapper).
 */
class ContextIntentEngineClaseFitnessFloorTest {

    @Test
    fun `captura yoga`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "clase de yoga mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Clase de yoga", intent.title)
    }

    @Test
    fun `captura pilates`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "clase de pilates el lunes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Clase de pilates", intent.title)
    }

    @Test
    fun `captura spinning`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "clase de spinning por la tarde", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Clase de spinning", intent.title)
    }

    @Test
    fun `captura aerobico con tilde`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "la clase de aeróbic mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Clase de aeróbic", intent.title)
    }

    @Test
    fun `captura zumba`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "clase de zumba el martes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Clase de zumba", intent.title)
    }

    @Test
    fun `captura gimnasia plural`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "clases de gimnasia el sábado", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Clases de gimnasia", intent.title)
    }

    @Test
    fun `captura posesivo yoga`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mi clase de yoga el jueves", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Clase de yoga", intent.title)
    }

    // Guards (NULL esperado — anti-overreach)
    @Test
    fun `guardia clase escolar es null`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "clase de matemáticas mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `guardia negacion no clases es null`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no clases de yoga mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `guardia pasado copulativo es null`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "la clase de zumba fue ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `guardia sustantivo neutro es null`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "clases de retórica el lunes", 1000)
        )
        assertNull(intent)
    }

    // Regresiones (fórmulas heredadas intactas)
    @Test
    fun `regresion hacer yoga hermana`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "hacer yoga", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Hacer yoga", intent.title)
    }

    @Test
    fun `regresion partido hermano`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "partido de tenis el domingo", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Partido de tenis", intent.title)
    }

    // Envolvente (TASK por la policy envolvente)
    @Test
    fun `envolvente recordame es task`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame clase de yoga mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}
