package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1118: extensión de destino del piso transportativo médico familiar
 * (c.776, [ContextIntentEngine.ERRAND_MEDICAL_RUN_FLOOR]) — lateral
 * REGISTRADA por ambos lados al cerrar c.1116 («pediatra», `d4b38ff`):
 * «llevar al niño al dermatólogo el viernes» seguía NULL pese a que
 * «dermatólog[oa]» ya era keyword/listas APPOINTMENT desde c.1110
 * (`33017dc9`) para la forma propia «ir al dermatólogo», porque el piso
 * c.776 cierra el destino con lista propia. NULL PRE medido por sonda
 * efímera (motor real, tools/run_probe.sh) sobre HEAD f0b9a9f:
 * «llevar al niño al dermatólogo el viernes» / «llevo a mi niño al
 * dermatólogo el lunes» / «llevar a los niños al dermatólogo» /
 * «vale, llevar a los niños al dermatólogo mañana» / «mañana llevar al
 * niño a la dermatóloga» → NULL ×5. Olvido silencioso P1: la visita del
 * niño al dermatólogo (dermatitis, alergias, lunares) es un caso de uso
 * real de esta diligencia familiar. Fix lockstep piso↔plantilla (lección
 * c.616; hermana EXACTA de c.1116): `dermatólog[oa]` añadido a la
 * alternancia de destino del piso (~l.604) y de la plantilla
 * matchMedicalRun de [ContextIntentEngine.extractTitle] (~l.4924). CERO
 * keywords nuevas (keyword-OBJETO «niños» preexistente c.773). Kind:
 * ERRAND (deliberación c.776: la cita es del niño; para el usuario es un
 * desplazamiento familiar). UNA forma por ciclo: psicólogo/ginecólogo
 * como destinos quedan laterales.
 */
class ContextIntentEngineLlevarDermatologoFloorTest {

    @Test
    fun `captura base el viernes`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar al niño al dermatólogo el viernes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar al niño al dermatólogo", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura primera persona posesivo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevo a mi niño al dermatólogo el lunes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevo a mi niño al dermatólogo", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura plural sin fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los niños al dermatólogo", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños al dermatólogo", intent.title)
    }

    @Test
    fun `captura con acuse y plural`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, llevar a los niños al dermatólogo mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a los niños al dermatólogo", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura prefijo temporal y destino femenino`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana llevar al niño a la dermatóloga", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar al niño a la dermatóloga", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `envolvente gobierna task`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame llevar al niño al dermatólogo el viernes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `negada descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no llevar al niño al dermatólogo mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `duda descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá llevar al niño al dermatólogo mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevé al niño al dermatólogo ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `objeto bivalente otra persona descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a María al dermatólogo mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `regresion cita propia dermatologo appointment intacta`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "ir al dermatólogo el viernes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
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

    @Test
    fun `regresion piso c773 colegio intacto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a los niños al colegio mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
    }
}
