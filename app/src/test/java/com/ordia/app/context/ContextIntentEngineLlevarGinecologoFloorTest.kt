package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1124: extensión de destino del piso transportativo médico familiar
 * (c.776, [ContextIntentEngine.ERRAND_MEDICAL_RUN_FLOOR]) — lateral
 * ABIERTA registrada al cerrar c.1120 («psicólog[oa]», `1a20b84`):
 * «llevar a la niña a la ginecóloga el jueves» seguía NULL porque el piso
 * c.776 cierra el destino con lista propia (misma causa raíz medida en
 * c.1116/c.1118/c.1120). NULL PRE medido por sonda efímera (motor real,
 * tools/run_probe.sh) sobre HEAD ce0b5dc: «llevar a la niña a la
 * ginecóloga el jueves» / «llevar a la niña al ginecólogo mañana» /
 * «llevo a mi niña a la ginecóloga el martes» / «vale, llevar a las
 * niñas al ginecólogo mañana» / «mañana llevar a la niña a la
 * ginecologa» → NULL ×5. Olvido silencioso P1: la primera revisión
 * ginecológica de la hija adolescente es un caso de uso real de esta
 * diligencia familiar. A diferencia de sus hermanas, «ginecólog[oa]» NO
 * era keyword APPOINTMENT (esa región es del marcador c.1113, NO TOCAR):
 * el alcance se limita a la alternancia del piso (y su plantilla), que es
 * autocontenida — igual que el piso ya hace con «hospital»/«consulta»,
 * que tampoco son keywords APPOINTMENT. Fix lockstep piso↔plantilla
 * (lección c.616; hermana EXACTA de c.1116/c.1118/c.1120):
 * `ginec[oó]log[oa]` añadido a la alternancia de destino del piso
 * (~l.613) y de la plantilla matchMedicalRun de
 * [ContextIntentEngine.extractTitle] (~l.4982). La grafía sin tilde
 * («ginecologa») se admite igual que `m[ée]dico` ya hace. CERO keywords
 * nuevas (keyword-OBJETO «niños» preexistente c.773). Kind: ERRAND
 * (deliberación c.776: la cita es de la niña; para el usuario es un
 * desplazamiento familiar). UNA forma por ciclo (doctrina
 * anti-overreach): AGOTA la familia de destinos laterales del piso c.776.
 */
class ContextIntentEngineLlevarGinecologoFloorTest {

    @Test
    fun `captura base el jueves`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a la niña a la ginecóloga el jueves", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a la niña a la ginecóloga", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura primera persona posesivo`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevo a mi niña a la ginecóloga el martes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevo a mi niña a la ginecóloga", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura plural masculino sin fecha`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a las niñas al ginecólogo", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a las niñas al ginecólogo", intent.title)
    }

    @Test
    fun `captura con acuse y plural`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, llevar a las niñas al ginecólogo mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a las niñas al ginecólogo", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura prefijo temporal sin tilde`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "mañana llevar a la niña a la ginecologa", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar a la niña a la ginecologa", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `envolvente gobierna task`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame llevar a la niña a la ginecóloga el jueves", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `negada descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no llevar a la niña a la ginecóloga mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `duda descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá llevar a la niña al ginecólogo mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `pasado descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevé a la niña a la ginecóloga ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `objeto bivalente otra persona descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar a María a la ginecóloga mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `regresion piso c1120 psicologo intacto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "llevar al niño al psicólogo el jueves", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Llevar al niño al psicólogo", intent.title)
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
