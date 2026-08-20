package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.752: forma "votar" (sonda `tools/probe/FourthClassVerbDiscoveryProbe.kt`,
 * c.740; candidato cívico único no anunciado tras el re-fetch — dispersión
 * anti-colisión). Piso TASK sobre el verbo unívoco "votar" (no admite objeto
 * bivalente: votar = sufragio) + keyword TASK "votar" (lockstep c.713/c.750) +
 * plantilla "(votar) <complemento>".
 * Kind: TASK (deliberación contra EVENT/APPOINTMENT/ERRAND — es deber cívico
 * de vida, hermano de "donar sangre" c.750 y "renovar el DNI" c.698; no hay
 * encuentro social ni profesional).
 */
class ContextIntentEngineVotarFloorTest {

    @Test
    fun `captura votar plus fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "votar el domingo", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Votar", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, votar el domingo", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Votar", intent.title)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "el domingo votar en el colegio", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Votar en el colegio", intent.title)
    }

    @Test
    fun `reordenado sin complemento descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "el domingo votar", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `captura con complemento sin pista temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "votar en las elecciones", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Votar en las elecciones", intent.title)
        assertNull(intent.dueAt)
    }

    @Test
    fun `envolvente recuerdame gobierna task`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame votar el domingo", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Votar", intent.title)
    }

    @Test
    fun `no votar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no votar el domingo", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `quizá votar descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá votar el domingo", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado voté descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "voté el domingo pasado", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `sustantivo votación descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "la votación fue ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `votar suelto descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "votar", 1000)
        )
        assertNull(intent)
    }
}
