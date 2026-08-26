package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1231: «partido de <deporte>» (lateral (c) DISJUNTA de c.1230,
 * sonda persistida `tools/probe/PartidoDeporteProbe.kt`, PRE 6/6 NULL
 * targets — olvido silencioso; el partido programado suena a VAGA pese
 * a ser un compromiso determinista). «Partido» es BIVALENTE (político),
 * así keyword-VERBO queda PROHIBIDA (gate c.751); vía keyword-OBJETO
 * monosemántica ya presente (deportes c.1228), precedente «mueble»
 * c.1224 / «mancha» c.1221. Lockstep TRES puntos (lección c.616):
 * piso acotado a «partido de <deporte>» + plantilla matchPartido en
 * extractTitle. Grafías preservadas doble literal tilde (c.1217).
 * Kind: EXERCISE (hermana de «jugar»; TASK solo en envolvente
 * «recuérdame»/«tengo que», lección de archivo del wrapper).
 */
class ContextIntentEnginePartidoDeporteFloorTest {

    @Test
    fun `captura tenis`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "partido de tenis el domingo", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Partido de tenis", intent.title)
    }

    @Test
    fun `captura baloncesto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "partido de baloncesto mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Partido de baloncesto", intent.title)
    }

    @Test
    fun `captura futbol con tilde`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "partido de fútbol el sábado", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Partido de fútbol", intent.title)
    }

    @Test
    fun `captura padel con tilde`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "partido de pádel el lunes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Partido de pádel", intent.title)
    }

    @Test
    fun `captura voleibol`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "partido de voleibol el jueves", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Partido de voleibol", intent.title)
    }

    @Test
    fun `captura balonmano`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "partido de balonmano el viernes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Partido de balonmano", intent.title)
    }

    // Guards (NULL esperado — anti-overreach)
    @Test
    fun `guardia partido politico es null`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "el partido político es mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `guardia negacion no partido es null`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no partido de tenis el domingo", 1000)
        )
        assertNull(intent)
    }

    // Regresiones (fórmulas heredadas intactas)
    @Test
    fun `regresion jugar hermana`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "jugar al tenis mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Jugar al tenis", intent.title)
    }

    @Test
    fun `regresion entrenar hermana`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "entrenar mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Entrenar", intent.title)
    }

    // Envolvente (TASK por la policy envolvente)
    @Test
    fun `envolvente recordame es task`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame que tengo partido de tenis el domingo", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}
