package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.768: forma "pasar la ITV" (sonda `tools/probe/FifthClassLifeProbe.kt`,
 * QUINTA clase — vida cotidiana/trámites; elegida por dispersión epoch-day
 * 20685 % 9 = 3 sobre el pool OPEN). Trámite periódico obligatorio:
 * "pasar la ITV este mes" → NULL PRE (sonda efímera
 * `/tmp/probe-work/ItvPreProbe.kt` sobre HEAD 4142660; también NULL
 * "pasar la ITV mañana" y "vale, pasar la ITV el viernes").
 * Piso TASK acotado al objeto `itv` (el verbo "pasar" es bivalente: la
 * tarde/el rato/la película quedan FUERA — una forma por ciclo, doctrina
 * de la sonda) + keyword-OBJETO "itv" (lockstep c.713/c.751/c.765; NO el
 * verbo) + plantilla "(pasar) la ITV". Kind: TASK (deliberación contra
 * APPOINTMENT — no hay cita con profesional; hermano del deber cívico
 * "votar" c.752 y del documento "renovar el DNI" c.698). Negación sin
 * cláusula dedicada: keyword 0.12 + bono temporal 0.1 = 0.22 < umbral
 * (hermana c.765/c.766). "este mes" es plazo blando que extractDateTime
 * NO resuelve a propósito: captura sin dueAt y con el residuo en el
 * título (hermano c.757 "vacunar al perro este mes").
 */
class ContextIntentEnginePasarItvFloorTest {

    @Test
    fun `captura base con plazo blando`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "pasar la ITV este mes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Pasar la ITV este mes", intent.title)
    }

    @Test
    fun `captura con fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "pasar la ITV mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Pasar la ITV", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse y dia de semana`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, pasar la ITV el viernes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Pasar la ITV", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana pasar la ITV", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Pasar la ITV", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con motivo de cola`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "pasar la ITV antes de que caduque", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Pasar la ITV antes de que caduque", intent.title)
    }

    @Test
    fun `envolvente gobierna task`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame pasar la ITV mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `negada descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no pasar la ITV mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `duda descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá pasar la ITV mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "pasé la ITV ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `objeto bivalente tarde descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "pasar la tarde", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `objeto bivalente pelicula descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "pasar la película mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `sustantivo suelto descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "la ITV del coche está cara", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `regresion ponerse la insulina intacto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "ponerse la insulina mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `regresion tomar la medicina intacto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "tomar la medicina a las 8", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}
