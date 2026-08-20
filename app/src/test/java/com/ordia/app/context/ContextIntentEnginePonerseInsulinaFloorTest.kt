package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.766: forma "ponerse la insulina" (sonda `tools/probe/FifthClassLifeProbe.kt`,
 * QUINTA clase — salud/autocuidado; elegida por dispersión epoch-day
 * 20685 % 9 = 3 sobre el pool OPEN). Es autocuidado con medicación diaria:
 * "ponerse la insulina mañana" → NULL PRE (sonda efímera
 * `/tmp/probe-work/InsulinaPreProbe.kt` sobre HEAD 2ece054).
 * Piso TASK acotado al objeto `insulina` (el verbo "ponerse" es bivalente:
 * la chaqueta/enfermo/contento quedan FUERA — una forma por ciclo, doctrina
 * de la sonda) + keyword-OBJETO "insulina" (lockstep c.713/c.751/c.765; NO el
 * verbo) + plantilla "(ponerse) la insulina". Kind: TASK (deliberación contra
 * APPOINTMENT/HABIT/HOUSEHOLD — autocuidado de vida, hermano de "tomar la
 * medicina" c.765 y "donar sangre" c.750). Negación sin cláusula dedicada:
 * keyword 0.12 + bono temporal 0.1 = 0.22 < umbral (hermana c.765).
 */
class ContextIntentEnginePonerseInsulinaFloorTest {

    @Test
    fun `captura base con fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "ponerse la insulina mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Ponerse la insulina", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con hora`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "ponerse la insulina a las 8", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Ponerse la insulina", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, ponerse la insulina esta noche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Ponerse la insulina", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `envolvente gobierna task`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame ponerse la insulina mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `negada descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no ponerse la insulina mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `duda descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá ponerse la insulina mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "me puse la insulina ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `objeto bivalente descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "ponerse la chaqueta mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `verbo suelto descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "ponerse", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `sustantivo suelto descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "la insulina está en la nevera", 1000)
        )
        assertNull(intent)
    }
}
