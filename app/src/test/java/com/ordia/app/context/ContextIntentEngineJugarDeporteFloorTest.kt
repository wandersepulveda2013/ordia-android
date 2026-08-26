package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1228: forma transitiva «jugar (al|bare) <deporte>» (lateral (a) FUERTE
 * ABIERTA por la auditoría c.1227, clase TRIGÉSIMA deporte — sonda
 * persistida `tools/probe/JugarDeporteProbe.kt`, PRE 6/6 NULL targets —
 * olvido silencioso; el partido semanal con amigos = el compromiso
 * deportivo más olvidable). «Jugar» es BIVALENTE (cartas/niños/
 * videojuegos/escondite), así keyword-VERBO queda PROHIBIDA (gate c.751)
 * y la vía es keyword-OBJETO monosemántica (precedente «mueble» c.1224 /
 * «mancha» c.1221 — keyword alimenta TRIGGER_WORDS gates c.751, piso
 * captura, plantilla titula: TRES puntos, lección c.616; doble literal por
 * la tilde-rompe-subcadena «fútbol»/«futbol», «pádel»/«padel», lección
 * c.1217). Determinista (regex), sin IA fingida. Kind: EXERCISE
 * (hermana de «correr»/«ir al gimnasio»; TASK solo en envolvente
 * «recuérdame»/«tengo que» — determina el vientre kind al envolvente,
 * lección de codificación del wrapper).
 */
class ContextIntentEngineJugarDeporteFloorTest {

    @Test
    fun `captura futbol con tilde mas fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "jugar al fútbol el sábado", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Jugar al fútbol", intent.title)
    }

    @Test
    fun `captura tenis`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "jugar al tenis el domingo", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Jugar al tenis", intent.title)
    }

    @Test
    fun `captura padel con tilde`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "jugar al pádel con los colegas", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Jugar al pádel con los colegas", intent.title)
    }

    @Test
    fun `captura baloncesto con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana jugar al baloncesto", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Jugar al baloncesto", intent.title)
    }

    @Test
    fun `captura voleibol`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "jugar al voleibol el lunes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Jugar al voleibol", intent.title)
    }

    @Test
    fun `captura bare sin articulo latam`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "jugar futbol mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Jugar futbol", intent.title)
    }

    @Test
    fun `captura balonmano`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "jugar al balonmano hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Jugar al balonmano", intent.title)
    }

    @Test
    fun `captura golf`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "jugar al golf el sábado", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
        assertEquals("Jugar al golf", intent.title)
    }

    @Test
    fun `nulo negacion no jugar`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no jugar al fútbol el sábado", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `nulo preterito jugue`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "jugué al fútbol ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `nulo objeto cartas bivalente`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "jugar a las cartas con los abuelos", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `nulo objeto escondite bivalente`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "jugar al escondite con los niños", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `nulo con proposito ninos`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "jugar con los niños en el parque", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `nulo primera persona plural jugamos`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "jugamos al fútbol el sábado", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `nulo declarativa futbol solo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "el fútbol de mañana se cancela", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `regresion correr sigue exercise`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "correr por el parque mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.EXERCISE, intent!!.kind)
    }

    @Test
    fun `envolvente recuerdame gobierna kind task`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame jugar al fútbol el sábado", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}
