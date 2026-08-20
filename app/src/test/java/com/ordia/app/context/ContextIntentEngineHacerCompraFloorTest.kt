package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.758: forma "hacer la compra" (sonda `FourthClassChoreProbe.kt` c.734,
 * PRE NULL — la palabra suelta "hacer" + base baja 0.35 < 0.45 elude todo
 * piso; piso acotado `SHOPPING_GROCERY_FLOOR` del verbo-hacer + objeto-compra
 * en posición libre (lockstep piso↔guard↔título: el verbo "hacer" es
 * bivalente — el informe/TASK, la cama/HOUSEHOLD — así se acota al objeto
 * compras?; suelo de la primera aproximación por keyword "compra": casa por
 * substring con "comprar" en toda frase con "comprar", elevando SHOPPING
 * 0.49 > piso
 * TASK/REMINDER 0.45 en envolventes, robando el kind — descartada). Cubre
 * la forma 7/7 de la cuarta clase de quehaceres: la compra semanal canónica
 * con "hacer".
 */
class ContextIntentEngineHacerCompraFloorTest {

    @Test
    fun `captura hacer la compra con fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "hacer la compra el sábado", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.SHOPPING, intent!!.kind)
        assertEquals("Hacer la compra", intent.title)
    }

    @Test
    fun `captura plural con articulo determinado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "hacer las compras mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.SHOPPING, intent!!.kind)
        assertEquals("Hacer las compras", intent.title)
    }

    @Test
    fun `captura sin articulo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "hacer compra de la semana el sábado", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.SHOPPING, intent!!.kind)
        assertEquals("Hacer compra de la semana", intent.title)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana hacer la compra", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.SHOPPING, intent!!.kind)
        assertEquals("Hacer la compra", intent.title)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, hacer la compra mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.SHOPPING, intent!!.kind)
        assertEquals("Hacer la compra", intent.title)
    }

    @Test
    fun `envuelta es TASK accionable no SHOPPING`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame hacer la compra mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer la compra", intent.title)
    }

    @Test
    fun `tengo que envuelta es TASK accionable no SHOPPING`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "tengo que hacer la compra mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `negada inmediata se descarta`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no hacer la compra mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `negada temporal incrustada se descarta`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana no hacer la compra", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `duda se descarta`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá hacer la compra mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `condicionada se descarta`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "si hay hueco hacer la compra mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado se descarta`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "hice la compra ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `objeto no compra no dispara el piso acotado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "hacer el informe mañana", 1000)
        )
        assertNull(intent)
    }
}
