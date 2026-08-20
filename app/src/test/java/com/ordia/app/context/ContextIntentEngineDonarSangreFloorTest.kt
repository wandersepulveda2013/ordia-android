package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.750: forma "donar sangre" (sonda `tools/probe/FourthClassVerbDiscoveryProbe.kt`,
 * c.740; familia salud/cívica). Piso TASK acotado al objeto `sangre` (el verbo
 * "donar" es bivalente: dinero/ropa/muebles quedan fuera — anti-overreach, una
 * forma por ciclo) + keyword TASK (lockstep c.713/c.726) + plantilla
 * "(donar) sangre"→"Donar sangre".
 * Kind: TASK (deliberación contra APPOINTMENT/ERRAND/HOUSEHOLD — no hay "cita"
 * ni profesional sanitario explícito; el objeto gobernado es la sangre, no el
 * destino; tampoco es quehacer doméstico. Es quehacer de vida, hermano de
 * "renovar el DNI" c.698).
 */
class ContextIntentEngineDonarSangreFloorTest {

    @Test
    fun `captura donar sangre plus fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "donar sangre el sábado", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Donar sangre", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, donar sangre mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Donar sangre", intent.title)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "el sábado donar sangre", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Donar sangre", intent.title)
    }

    @Test
    fun `captura sin pista temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "donar sangre", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Donar sangre", intent.title)
        assertNull(intent.dueAt)
    }

    @Test
    fun `envolvente c613 gobierna`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame donar sangre", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Donar sangre", intent.title)
    }

    @Test
    fun `no donar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no donar sangre el sábado", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quizá donar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá donar sangre el sábado", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado doné descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "doné sangre el sábado pasado", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `objeto bivalente donar dinero descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "donar dinero a la ONG el sábado", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `sustantivo donación descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "la donación de sangre fue ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `donar suelto descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "donar", 1000)
        )
        assertNull(intent)
    }
}
