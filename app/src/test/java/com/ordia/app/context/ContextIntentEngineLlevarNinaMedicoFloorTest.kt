package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.776: forma "llevar a la niña al médico" (sonda
 * `tools/probe/FifthClassLifeProbe.kt`, QUINTA clase — familia/salud; elegida
 * por dispersión determinista epoch-day 20685 % 2 = 1 sobre el pool OPEN
 * residual de 2 ítems). NULL PRE verificado por la sonda sobre HEAD c5031be.
 * La diligencia familiar de salud (llevar al hijo/a a su cita médica) es la
 * hermana del desplazamiento escolar c.773: capturarla evita el olvido de un
 * compromiso con coste real (cita perdida, re-agendarse semanas después).
 * Piso ERRAND acotado al MISMO objeto `niñ[oa]s?` del piso escolar + destino
 * médico inequívoco `médico|doctor|dentista|hospital|consulta`: el verbo
 * "llevar" es bivalente (el coche al taller c.684, al perro al veterinario
 * —HOUSEHOLD c.747—, a María al médico —otro objeto— quedan FUERA; una forma
 * por ciclo, doctrina de la sonda). Kind: ERRAND (deliberación contra
 * APPOINTMENT: la cita es de la niña; para el usuario es un desplazamiento
 * familiar, hermano del piso escolar; el APPOINTMENT propio ya cubre
 * "ir al médico" —bono c.682—). Lockstep keyword-OBJETO "niños" PREEXISTENTE
 * en ERRAND (c.773) → coste-cero (hermana c.770). Negación sin cláusula
 * dedicada: keyword 0.12 + bono temporal 0.1 = 0.22 < umbral (hermana
 * c.765→c.772); el piso además lleva el guard `(?<!no )` de la familia.
 */
class ContextIntentEngineLlevarNinaMedicoFloorTest {

    @Test
    fun `captura base el lunes`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a la niña al médico el lunes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a la niña al médico", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura doctor manana`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los niños al doctor mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños al doctor", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, llevar a la niña al médico mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a la niña al médico", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con prefijo temporal`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana llevar a la niña al médico", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a la niña al médico", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura primera persona posesivo singular dentista`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevo a mi niña al dentista mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevo a mi niña al dentista", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `envolvente gobierna task`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame llevar a la niña al médico el lunes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `negada descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no llevar a la niña al médico mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `duda descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá llevar a la niña al médico mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevé a la niña al médico ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `objeto bivalente otra persona descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a María al médico mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `sin destino descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a la niña mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `declarativo suelto descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "la niña va al médico mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `regresion llevar a los ninos al colegio intacto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los niños al colegio mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
    }

    @Test
    fun `regresion hacer copia de seguridad intacto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "hacer copia de seguridad hoy", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}
