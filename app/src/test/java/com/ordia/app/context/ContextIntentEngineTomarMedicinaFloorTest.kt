package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.765: forma "tomar la medicina" (sonda NUEVA
 * `tools/probe/FifthClassLifeProbe.kt`, QUINTA clase de formas cotidianas —
 * salud/autocuidado: 6/6 NULL en sonda PRE sobre HEAD 9815ee2). Es el olvido
 * silencioso de mayor coste del dominio de autocuidado: la medicación diaria
 * (con hora explícita incluida: "tomar la medicina a las 8" → NULL PRE).
 * Piso TASK acotado al objeto `medicinas?|medicamentos?|pastillas?` (el verbo
 * "tomar" es bivalente: el café/el autobús/un vuelo/una decisión quedan
 * FUERA — una forma por ciclo, doctrina de la sonda) + keyword-OBJETO
 * "medicina" (lockstep c.713/c.751) + plantilla "(tomar) <objeto-medicina>".
 * Kind: TASK (deliberación contra APPOINTMENT/HABIT/HOUSEHOLD — no hay
 * profesional sanitario ni consulta [no es cita], no es quehacer doméstico;
 * es autocuidado de vida, hermano de "donar sangre" c.750 y "renovar el DNI"
 * c.698).
 */
class ContextIntentEngineTomarMedicinaFloorTest {

    @Test
    fun `captura base con hora`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "tomar la medicina a las 8", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Tomar la medicina", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "tomar la medicina mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Tomar la medicina", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura pastillas`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "tomar las pastillas hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Tomar las pastillas", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura medicamento con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "esta noche tomar el medicamento", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Tomar el medicamento", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, tomar la medicina mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Tomar la medicina", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `envolvente gobierna task`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame tomar la medicina mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `negada descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no tomar la medicina mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `duda descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá tomar la medicina mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "tomé la medicina ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `objeto bivalente descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "tomar el café mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `verbo suelto descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "tomar", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `sustantivo suelto descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "la medicina está en la mesa", 1000)
        )
        assertNull(intent)
    }
}
