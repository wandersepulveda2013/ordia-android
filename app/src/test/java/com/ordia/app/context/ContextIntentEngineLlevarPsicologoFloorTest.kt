package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1120: extensión de destino del piso transportativo médico familiar
 * (c.776, [ContextIntentEngine.ERRAND_MEDICAL_RUN_FLOOR]) — lateral
 * ABIERTA registrada al cerrar c.1118 («dermatólog[oa]», `a94a31f`):
 * «llevar al niño al psicólogo el jueves» seguía NULL pese a que
 * «psicólog[oa]» ya era keyword/listas APPOINTMENT de larga data para la
 * forma propia «ir al psicólogo», porque el piso c.776 cierra el destino
 * con lista propia. NULL PRE medido por sonda efímera (motor real,
 * tools/run_probe.sh) sobre HEAD 7b983f8: «llevar al niño al psicólogo el
 * jueves» / «llevar a la niña al psicólogo mañana» / «llevo a mi niño al
 * psicólogo el martes» / «vale, llevar a los niños al psicólogo mañana» /
 * «mañana llevar al niño a la psicóloga» → NULL ×5. Olvido silencioso P1:
 * la terapia infantil (psicólogo escolar/infanto-juvenil) es un caso de
 * uso real de esta diligencia familiar. Fix lockstep piso↔plantilla
 * (lección c.616; hermana EXACTA de c.1116 «pediatra» y c.1118
 * «dermatólog[oa]»): `psicólog[oa]` añadido a la alternancia de destino
 * del piso (~l.609) y de la plantilla matchMedicalRun de
 * [ContextIntentEngine.extractTitle] (~l.4926). CERO keywords nuevas
 * (keyword-OBJETO «niños» preexistente c.773). Kind: ERRAND (deliberación
 * c.776: la cita es del niño; para el usuario es un desplazamiento
 * familiar). UNA forma por ciclo: ginecólogo como destino queda lateral.
 */
class ContextIntentEngineLlevarPsicologoFloorTest {

    @Test
    fun `captura base el jueves`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar al niño al psicólogo el jueves", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar al niño al psicólogo", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura primera persona posesivo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevo a mi niño al psicólogo el martes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevo a mi niño al psicólogo", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura plural sin fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los niños al psicólogo", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños al psicólogo", intent.title)
    }

    @Test
    fun `captura con acuse y plural`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, llevar a los niños al psicólogo mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños al psicólogo", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura prefijo temporal y destino femenino`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana llevar al niño a la psicóloga", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar al niño a la psicóloga", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `envolvente gobierna task`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame llevar al niño al psicólogo el jueves", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `negada descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no llevar al niño al psicólogo mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `duda descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá llevar al niño al psicólogo mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevé al niño al psicólogo ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `objeto bivalente otra persona descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a María al psicólogo mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `regresion cita propia psicologo appointment intacta`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "ir al psicólogo el jueves", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
    }

    @Test
    fun `regresion piso c1118 dermatologo intacto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar al niño al dermatólogo el viernes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar al niño al dermatólogo", intent.title)
    }

    @Test
    fun `regresion piso c1116 pediatra intacto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar al niño al pediatra el viernes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar al niño al pediatra", intent.title)
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
}
