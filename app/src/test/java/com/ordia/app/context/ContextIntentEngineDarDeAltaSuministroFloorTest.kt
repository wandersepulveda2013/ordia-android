package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1139 — candidata (b) de la clase DECIMOQUINTA (burocracia/administración,
 * sonda persistida `tools/probe/FifteenthClassAdminProbe.kt` c.1132 del
 * hermano, C3/C4): «dar de alta/baja <suministro>» (luz/agua/gas/internet).
 * NULL PRE medido con sonda efímera (motor real vía `tools/run_probe.sh`,
 * base `67b7e7e`): 8/8 candidatas NULL, 7/7 guards NULL, regresiones HIT.
 * Olvido silencioso P1: mudanza sin suministro (alta) o cargo mensual
 * fantasma del piso viejo (baja).
 *
 * Decisión de dominio: TASK (gestión administrativa SIN desplazamiento
 * físico; hermana EXACTA de «dar de baja el gimnasio/la suscripción»
 * TASK c.895c — la doctrina ERRAND c.842/c.862 gobierna solo el
 * desplazamiento).
 *
 * Lockstep TRES puntos (lección c.616/c.751): (1) keyword-frase
 * «dar de alta» en TASK (cuasi-monosemántica: alta administrativa; el
 * verbo «dar» solo NO se añade — polivalente); (2) piso «dar de alta»
 * acotado a objeto-suministro + extensión aditiva del piso «dar de baja»
 * c.895c con `luz|agua|gas|internet` (ancla ^|acuse|temporal y guard
 * `(?<!no )` heredados de la familia); (3) plantilla hermana
 * matchDarDeAlta en [extractTitle] (doctrina c.653: verbo-frase
 * preservado).
 *
 * Acotado deliberado: «dar de alta el gimnasio» sigue NULL (alta sólo
 * suministros; pin heredado del test c.895c), «dar de alta a un paciente»
 * (bivalente médico) NULL, «dar de baja la línea telefónica» NULL
 * (deliberado c.895c). Laterales ABIERTAS (UNA por ciclo): «seguro»
 * (contrato, no suministro), «empadronarme», «sellar el paro».
 */
class ContextIntentEngineDarDeAltaSuministroFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ─── Capturas «dar de alta <suministro>» ──────────────────────

    @Test
    fun `dar de alta la luz captura TASK con titulo limpio`() {
        val intent = analyze("dar de alta la luz mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Dar de alta la luz", intent.title)
    }

    @Test
    fun `dar de alta la luz del piso nuevo captura TASK`() {
        val intent = analyze("dar de alta la luz del piso nuevo mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Dar de alta la luz del piso nuevo", intent.title)
    }

    @Test
    fun `dar de alta el agua captura TASK`() {
        val intent = analyze("dar de alta el agua del apartamento la semana que viene")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `dar de alta el internet sin fecha captura TASK sin dueAt`() {
        val intent = analyze("dar de alta el internet en el piso nuevo")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals(null, intent.dueAt)
    }

    // ─── Capturas «dar de baja <suministro>» (extensión c.895c) ────

    @Test
    fun `dar de baja el internet captura TASK`() {
        val intent = analyze("dar de baja el internet del piso viejo")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Dar de baja el internet del piso viejo", intent.title)
    }

    @Test
    fun `dar de baja el gas captura TASK`() {
        val intent = analyze("dar de baja el gas mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `dar de baja la luz con dia de semana captura TASK`() {
        val intent = analyze("dar de baja la luz del piso viejo el lunes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `acuse vale dar de baja el internet captura TASK`() {
        val intent = analyze("vale, dar de baja el internet el viernes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Dar de baja el internet", intent.title)
    }

    // ─── Guards (NULL deliberado) ─────────────────────────────────

    @Test
    fun `negada no dar de alta no captura`() {
        assertNull(analyze("no dar de alta la luz mañana"))
    }

    @Test
    fun `duda quizas dar de baja el internet no captura`() {
        assertNull(analyze("quizá dar de baja el internet mañana"))
    }

    @Test
    fun `pasado di de alta no captura`() {
        assertNull(analyze("di de alta la luz ayer"))
    }

    @Test
    fun `pasado di de baja no captura`() {
        assertNull(analyze("di de baja el internet ayer"))
    }

    @Test
    fun `bivalente dar de alta a un paciente no captura`() {
        assertNull(analyze("dar de alta a un paciente mañana"))
    }

    @Test
    fun `sustantivo el alta de la luz no captura`() {
        assertNull(analyze("el alta de la luz del piso"))
    }

    @Test
    fun `objeto fuera de suministro linea telefonica no captura`() {
        assertNull(analyze("dar de baja la línea telefónica mañana"))
    }

    // ─── Regresiones (byte-idénticas) ─────────────────────────────

    @Test
    fun `dar de baja el gimnasio sigue TASK c895c`() {
        val intent = analyze("dar de baja el gimnasio mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Dar de baja el gimnasio", intent.title)
    }

    @Test
    fun `dar de alta el gimnasio sigue NULL acotado`() {
        assertNull(analyze("dar de alta el gimnasio mañana"))
    }

    @Test
    fun `dar las gracias sigue TASK c901`() {
        val intent = analyze("dar las gracias a Ana por el regalo mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `pagar la luz sigue PAYMENT`() {
        val intent = analyze("pagar la luz mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
    }

    @Test
    fun `envolvente recuerdame dar de baja el internet TASK c613`() {
        val intent = analyze("recuérdame dar de baja el internet mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `envolvente recuerdame dar de alta la luz TASK c613`() {
        val intent = analyze("recuérdame dar de alta la luz mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Dar de alta la luz", intent.title)
    }
}
