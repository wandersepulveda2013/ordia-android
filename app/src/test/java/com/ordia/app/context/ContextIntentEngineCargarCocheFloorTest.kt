package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.853: «cargar el COCHE» (candidata de la sonda persistida c.845
 * `tools/probe/SeventhClassErrandProbe.kt`; NULL PRE re-verificado por la
 * sonda: «cargar el coche antes del viaje» → NULL mientras «cargar el
 * celular/móvil» (c.751/c.851) capturaba TASK).
 * DELIBERACIÓN de la bivalencia (acotada en c.851 como «requiere
 * deliberación propia»): «cargar el coche» es bivalente REAL — (a) meter
 * el equipaje antes del viaje, (b) cargar el vehículo eléctrico — pero
 * AMBAS lecturas son deberes genuinos del usuario y el título PRESERVA las
 * palabras exactas del usuario («Cargar el coche») sin necesidad de
 * desambiguar: no hay corrupción de datos, ni capacidad fingida, ni
 * agenda inventada (el ancla temporal sale sólo de las palabras
 * temporales). El error benigno es capturar un deber real; el maligno era
 * el olvido silencioso. Kind heredado del piso: TASK (deber de
 * mantenimiento/preparación del vehículo, consistente con celular/móvil).
 * Fix barato: el objeto del piso admite «coche» en los 2 puntos lockstep
 * (piso + plantilla de título, lección c.717) + keyword-OBJETO «coche»
 * (lección c.751; la subcadena de «cochera»/«cochecito» suma 0.12 inerte
 * < umbral — mismo argumento que «automóvil» c.851/«extensión» c.772).
 * Siguen FUERA (objetos bivalentes cuya segunda lectura NO es un deber
 * personal): «cargar la tarjeta» (recarga/pago), «cargar el archivo»
 * (acción informática). Acotado deliberado (una forma por ciclo):
 * «cargar el carro» (diagonal dialectal LatAm) queda como candidata
 * propia.
 */
class ContextIntentEngineCargarCocheFloorTest {

    // ---- Capturas directas (piso, objeto «coche») ----

    @Test
    fun `captura sonda antes del viaje`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cargar el coche antes del viaje", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cargar el coche antes del viaje", intent.title)
    }

    @Test
    fun `captura esta noche`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cargar el coche esta noche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cargar el coche", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura posesivo mi coche`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cargar mi coche mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cargar mi coche", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, cargar el coche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cargar el coche", intent.title)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana cargar el coche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cargar el coche", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura plural coches`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cargar los coches mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cargar los coches", intent.title)
        assertNotNull(intent.dueAt)
    }

    // ---- Envolvente: c.613 gobierna TASK (TASK no está en WRAPPABLE_PATTERNS) ----

    @Test
    fun `envolvente c613 gobierna TASK`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame cargar el coche esta noche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cargar el coche", intent.title)
    }

    // ---- Guards (contratos anti-overreach / anti-falsos-positivos) ----

    @Test
    fun `no cargar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no cargar el coche esta noche", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quizá cargar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá cargar el coche", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado cargue descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cargué el coche anoche", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `objeto bivalente tarjeta descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cargar la tarjeta mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `objeto bivalente archivo descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cargar el archivo mañana", 1000)
        )
        assertNull(intent)
    }

    // ---- Regresiones del piso (objetos «celular» c.751 / «móvil» c.851 intactos) ----

    @Test
    fun `regresión celular intacta`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cargar el celular hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cargar el celular", intent.title)
    }

    @Test
    fun `regresión movil intacta`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cargar el móvil esta noche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Cargar el móvil", intent.title)
    }
}
