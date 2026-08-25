package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1116: extensión de destino del piso transportativo médico familiar
 * (c.776, [ContextIntentEngine.ERRAND_MEDICAL_RUN_FLOOR]) — residual
 * REGISTRADO en el RUN_LOG de c.1106: «llevar al niño al pediatra el
 * viernes» seguía NULL pese a que «pediatra» ya era keyword APPOINTMENT
 * (c.1106), porque el piso c.776 cierra el destino con lista propia
 * (`médico|doctor|dentista|hospital|consulta`). NULL PRE re-verificado por
 * sonda efímera (motor real, tools/run_probe.sh) sobre HEAD 780620a:
 * «llevar al niño al pediatra el viernes» / «llevo a mi niña al pediatra
 * mañana» / «llevar a los niños al pediatra» → NULL, NULL, NULL.
 * Olvido silencioso P1: la cita del niño con el pediatra es el caso de uso
 * real más frecuente de este piso (el pediatra es el médico de cabecera de
 * los menores). Fix lockstep piso↔plantilla (lección c.616): `pediatra`
 * añadido a la alternancia de destino del piso (~l.596) y de la plantilla
 * matchMedicalRun de [ContextIntentEngine.extractTitle] (~l.4913). CERO
 * keywords nuevas (keyword-OBJETO «niños» preexistente c.773). Kind: ERRAND
 * (deliberación c.776: la cita es del niño; para el usuario es un
 * desplazamiento familiar). UNA forma por ciclo: dermatólogo/psicólogo/
 * ginecólogo como destinos quedan laterales.
 */
class ContextIntentEngineLlevarPediatraFloorTest {

    @Test
    fun `captura base el viernes`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar al niño al pediatra el viernes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar al niño al pediatra", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura primera persona posesivo singular`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevo a mi niña al pediatra mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevo a mi niña al pediatra", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura plural sin fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los niños al pediatra", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños al pediatra", intent.title)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, llevar al niño al pediatra mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar al niño al pediatra", intent.title)
    }

    @Test
    fun `envolvente gobierna task`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame llevar al niño al pediatra el viernes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `negada descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no llevar al niño al pediatra mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `duda descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá llevar al niño al pediatra mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `objeto bivalente otra persona descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a María al pediatra mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `regresion cita propia pediatra appointment intacta`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "tengo cita con el pediatra mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
    }

    @Test
    fun `regresion piso c776 medico intacto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a la niña al médico el lunes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a la niña al médico", intent.title)
    }

    @Test
    fun `regresion piso c773 colegio intacto`() {
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
