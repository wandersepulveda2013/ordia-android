package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1117: piso TASK «sacar cita/turno/hora» — candidata (c) del complemento
 * c.1102 (clase DECIMOTERCERA, salud): «sacar cita para el oftalmólogo
 * mañana» seguía NULL pese a la clase completa de citas médicas, porque
 * «sacar» sólo pisaba acotado a mascota (c.740/c.1050), basura (c.717) y
 * dinero (c.893), ninguno aplicable a «cita». NULL PRE re-verificado por
 * sonda efímera (motor real, tools/run_probe.sh) sobre HEAD 92b0ffe:
 * «sacar cita para el oftalmólogo mañana» / «sacar una cita para el médico
 * mañana» / «sacar turno para el médico mañana» / «sacar turno la semana
 * que viene» / «sacar hora para el dentista mañana» / «sacar hora mañana» /
 * «saco cita para el médico el lunes» → NULL ×7. Olvido silencioso P1:
 * «sacar cita» es LA forma coloquial LatAm de comprometerse a agendar.
 * Kind: TASK — «sacar cita» es la ACCIÓN de agendar, no la cita (hermano
 * EXACTO de «pedir hora/turno» → TASK 0.45 medido c.1102; la cita enunciada
 * ya captura APPOINTMENT por sí misma). Lockstep piso↔plantilla (lección
 * c.616): piso en hasStrongTaskImperative + plantilla matchSacarCita en
 * [ContextIntentEngine.extractTitle]. CERO keywords nuevas («cita» ya es
 * keyword APPOINTMENT; el piso no depende de keywords). CERO guards nuevos:
 * ancla ^|acuse|temporal + lookbehind «no » de la familia; el pretérito
 * «saqué» queda fuera por alternancia de verbo cerrada.
 */
class ContextIntentEngineSacarCitaFloorTest {

    @Test
    fun `captura base para oftalmologo manana`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "sacar cita para el oftalmólogo mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Sacar cita para el oftalmólogo", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura indefinido una cita`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "sacar una cita para el médico mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Sacar una cita para el médico", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura objeto turno`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "sacar turno para el médico mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Sacar turno para el médico", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura objeto hora`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "sacar hora para el dentista mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Sacar hora para el dentista", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura pelada sin destinatario`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "sacar turno la semana que viene", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Sacar turno", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura primera persona saco`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "saco cita para el médico el lunes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Saco cita para el médico", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `captura con acuse`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "vale, sacar cita para el médico mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Sacar cita para el médico", intent.title)
    }

    @Test
    fun `envolvente gobierna task`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "recuérdame sacar cita para el médico mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `negada descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "no sacar cita para el médico mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `preterito descartado`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "saqué la cita ayer", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `duda descartada`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "quizá sacar cita mañana", 1000)
        )
        assertNull(intent)
    }

    @Test
    fun `regresion cita con dentista sigue appointment`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "sacar cita con el dentista el viernes", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
        assertEquals("Cita con el dentista", intent.title)
    }

    @Test
    fun `regresion piso basura c717 intacto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "sacar la basura esta noche", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Sacar la basura", intent.title)
    }

    @Test
    fun `regresion piso perro c740 intacto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "sacar al perro a las 8", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
    }

    @Test
    fun `regresion piso dinero c893 intacto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "sacar dinero del cajero mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.ERRAND, intent!!.kind)
        assertEquals("Sacar dinero del cajero", intent.title)
    }

    @Test
    fun `regresion pedir hora hermano intacto`() {
        val intent = ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, "pedir hora al dentista mañana", 1000)
        )
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}
