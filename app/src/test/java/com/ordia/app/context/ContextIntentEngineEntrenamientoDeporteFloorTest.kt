package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * TDD (RED→GREEN) c.1235 (lateral (f) «entrenamiento de (deporte|fútbol)f»
 * de la auditoría c.1227 cl.XXX deporte). Gate: objeto cerrado (fútbol|
 * futbol|deporte|pádel|padel|tenis), precedente «partido» c.1231. PRE
 * persistida `tools/probe/EntrenamientoDeporteProbe.kt` (T1–T4 NULL,
 * G1–G3 NULL, R1–R2 HIT). Lockstep: piso nominal-scoped + plantilla.
 */
class ContextIntentEngineEntrenamientoDeporteFloorTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // Targets (RED: 4 fallas exactas)
    @Test
    fun capturaEntrenamientoFutbolDomingo() {
        val i = analyze("entrenamiento de fútbol el domingo")
        assertNotNull(i)
        assertEquals(ContextIntentKind.EXERCISE, i?.kind)
        assertNotNull(i?.title)
    }

    @Test
    fun capturaEntrenamientoFutbolTarde() {
        val i = analyze("entrenamiento de fútbol por la tarde")
        assertNotNull(i)
        assertEquals(ContextIntentKind.EXERCISE, i?.kind)
    }

    @Test
    fun capturaElEntrenamientoFutbol() {
        val i = analyze("el entrenamiento de fútbol el domingo")
        assertNotNull(i)
        assertEquals(ContextIntentKind.EXERCISE, i?.kind)
    }

    @Test
    fun capturaEntrenamientoDeporte() {
        val i = analyze("el entrenamiento de deporte esta tarde")
        assertNotNull(i)
        assertEquals(ContextIntentKind.EXERCISE, i?.kind)
    }

    // Guards NULL (pretérito/negación/prohibición)
    @Test
    fun guardPretéritoNull() {
        assertNull(analyze("el entrenamiento de fútbol fue ayer"))
    }

    @Test
    fun guardNegacionNull() {
        assertNull(analyze("no entrenamiento de fútbol el domingo"))
    }

    @Test
    fun guardObjectoAbiertoNull() {
        assertNull(analyze("el entrenamiento de opereta esta tarde"))
    }

    // Regresiones HIT (entrenar/exercicio preexistentes)
    @Test
    fun regresionEntrenarHit() {
        val i = analyze("entrenar a las 6")
        assertNotNull(i)
        assertEquals(ContextIntentKind.EXERCISE, i?.kind)
    }

    @Test
    fun regresionHacerEjercicioHit() {
        val i = analyze("hacer ejercicio por la mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.EXERCISE, i?.kind)
    }
}
