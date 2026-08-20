package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.760: forma "hacer la colada" (sonda `FourthClassChoreProbe.kt` c.734,
 * PRE NULL — última forma OPEN del pool de quehaceres; piso acotado
 * `HOUSEHOLD_COLADA_FLOOR`: verbo bivalente "hacer" + objeto `coladas?`
 * acotado, posición libre con candado envolvente vía [WRAPPABLE_PATTERNS]
 * (lockstep piso↔guard↔título: c.648/c.652). Interop: "poner la lavadora"
 * (c.729) y "hacer la cama" (c.728) siguen ganando su propio piso — objetos
 * disjuntos. Cubre la última forma de la cuarta clase de quehaceres (Chore
 * 7/7).
 */
class ContextIntentEngineHacerColadaFloorTest {

    @Test
    fun `captura hacer la colada con fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "hacer la colada mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Hacer la colada", intent.title)
    }

    @Test
    fun `captura plural con articulo determinado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "hacer las coladas hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Hacer las coladas", intent.title)
    }

    @Test
    fun `captura sin articulo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "hacer colada esta tarde", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Hacer colada", intent.title)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "esta noche hacer la colada", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Hacer la colada", intent.title)
    }

    @Test
    fun `envuelta es TASK accionable no HOUSEHOLD`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame hacer la colada mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Hacer la colada", intent.title)
    }

    @Test
    fun `negada es NULL`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no hacer la colada mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `duda es NULL`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá hacer la colada mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasada es NULL`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "hice la colada ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `interop objetos cercanos conservan su piso`() {
        val lavadora = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "poner la lavadora mañana", 1000)
        )
        assertNotNull(lavadora)
        assertEquals(ContextIntentKind.HOUSEHOLD, lavadora!!.kind)
        assertEquals("Poner la lavadora", lavadora.title)
        val cama = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "hacer la cama mañana", 1000)
        )
        assertNotNull(cama)
        assertEquals(ContextIntentKind.HOUSEHOLD, cama!!.kind)
        assertEquals("Hacer la cama", cama.title)
    }

    @Test
    fun `guard verbo hacer no captura otros objetos`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "hacer la limpieza mañana", 1000)
        )
        assertNull(intent)
    }
}
