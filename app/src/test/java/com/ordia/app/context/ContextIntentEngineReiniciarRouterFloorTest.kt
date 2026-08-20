package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.771: forma "reiniciar el router" (sonda `tools/probe/FifthClassLifeProbe.kt`,
 * QUINTA clase — hogar-tecnología; elegida por dispersión determinista
 * epoch-day 20685 % 6 = 3 sobre el pool OPEN residual de 6 ítems). NULL PRE
 * verificado por la sonda sobre HEAD 3e155c4 (también NULL todas las demás
 * variantes). El fallo del router es de los cortes de conexión más
 * cotidianos: capturarlo evita el olvido. Piso TASK acotado al objeto
 * `routers?` (el verbo "reiniciar" es bivalente: el ordenador/la app/el
 * móvil quedan FUERA — una forma por ciclo, doctrina de la sonda) +
 * keyword-OBJETO "router" (lockstep c.713/c.751/c.765; NO el verbo) +
 * plantilla "(reiniciar) el router". Kind: TASK (hermano del dispositivo
 * "cargar el celular" c.751 y del depósito "llenar el tanque" c.720;
 * deliberación contra HOUSEHOLD: no es quehacer doméstico de la lista
 * histórica, es mantenimiento de dispositivo). Negación sin cláusula
 * dedicada: keyword 0.12 + bono temporal 0.1 = 0.22 < umbral (hermana
 * c.765/c.766/c.768).
 */
class ContextIntentEngineReiniciarRouterFloorTest {

    @Test
    fun `captura base esta noche`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "reiniciar el router esta noche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Reiniciar el router", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "reiniciar el router mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Reiniciar el router", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse y dia`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, reiniciar el router hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Reiniciar el router", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana reiniciar el router", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Reiniciar el router", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura plural`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "reiniciar routers mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Reiniciar routers", intent.title)
    }

    @Test
    fun `envolvente gobierna task`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame reiniciar el router esta noche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `negada descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no reiniciar el router mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `duda descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá reiniciar el router mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "reinicié el router ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `objeto bivalente ordenador descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "reiniciar el ordenador mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `suelto descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "reiniciar", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `sustantivo suelto descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "el router está apagado", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `regresion cargar el celular intacto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "cargar el celular hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `regresion pasar la itv intacto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "pasar la ITV mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}
