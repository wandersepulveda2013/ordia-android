package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1162 — lateral (b-bis) de la clase DECIMOQUINTA (burocracia/
 * administración, sonda persistida `tools/probe/FifteenthClassAdminProbe.kt`
 * c.1132 del hermano): extensión ADITIVA del objeto «seguro» en los pisos
 * «dar de alta» c.1139 y «dar de baja» c.895c. Lateral explícitamente
 * abierta en el docstring del piso c.1139 («seguro», contrato, no
 * suministro). NULL PRE medido con sonda efímera (motor real vía
 * `tools/run_probe.sh`, base `3a87bba6`): 6/6 candidatas NULL (3 alta +
 * 3 baja, incl. acuse y artículo indefinido), 5/5 guards NULL, 3/3
 * regresiones HIT + pin «dar de alta el gimnasio» NULL intacto.
 * Olvido silencioso P1: póliza duplicada tras cambio de aseguradora (alta)
 * o cargo mensual/anual fantasma del seguro cancelado (baja).
 *
 * Decisión de dominio heredada: TASK (gestión administrativa SIN
 * desplazamiento; doctrina ERRAND c.842/c.862 solo gobierna el
 * desplazamiento).
 *
 * Lockstep (lección c.616/c.751): CERO keywords nuevas (las frases
 * «dar de alta»/«dar de baja» ya existen, c.1139/c.895c) y CERO
 * plantillas nuevas (matchDarDeAlta/matchDarDeBaja capturan `(.+)`) —
 * la lateral es SOLO extensión de la alternancia de objetos de ambos
 * pisos, byte-conservadora en todo lo demás.
 *
 * Acotado deliberado: «dar de alta un seguro de vida» sigue NULL
 * (artículo indefinido fuera de la familia de la ancla, como en
 * c.895c/c.1139), «dar de alta el gimnasio» sigue NULL (pin c.895c/
 * c.1139). Laterales ABIERTAS (UNA por ciclo): «gimnasio» en el piso
 * de alta (b-ter), «empadronarme», «hacer la mudanza» (d).
 */
class ContextIntentEngineDarSeguroFloorTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ─── Capturas «dar de alta el seguro» ─────────────────────────

    @Test
    fun `dar de alta el seguro captura TASK con titulo limpio`() {
        val intent = analyze("dar de alta el seguro del coche mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Dar de alta el seguro del coche", intent.title)
    }

    @Test
    fun `dar de alta el seguro de hogar captura TASK`() {
        // residuo «esta semana» en el título: familia de colas
        // conocida (mismo criterio que «la semana que viene» c.1139)
        val intent = analyze("dar de alta el seguro de hogar esta semana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `acuse vale dar de alta el seguro captura TASK`() {
        val intent = analyze("vale, dar de alta el seguro médico el lunes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Dar de alta el seguro médico", intent.title)
    }

    // ─── Capturas «dar de baja el seguro» ─────────────────────────

    @Test
    fun `dar de baja el seguro captura TASK`() {
        val intent = analyze("dar de baja el seguro del piso viejo")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Dar de baja el seguro del piso viejo", intent.title)
    }

    @Test
    fun `dar de baja el seguro del coche con dia captura TASK`() {
        val intent = analyze("dar de baja el seguro del coche el viernes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Dar de baja el seguro del coche", intent.title)
    }

    @Test
    fun `acuse vale dar de baja el seguro captura TASK`() {
        val intent = analyze("vale, dar de baja el seguro de hogar mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Dar de baja el seguro de hogar", intent.title)
    }

    // ─── Guards (NULL deliberado) ─────────────────────────────────

    @Test
    fun `negada no dar de alta el seguro no captura`() {
        assertNull(analyze("no dar de alta el seguro mañana"))
    }

    @Test
    fun `pasado di de alta el seguro no captura`() {
        assertNull(analyze("di de alta el seguro ayer"))
    }

    @Test
    fun `pasado di de baja el seguro no captura`() {
        assertNull(analyze("di de baja el seguro ayer"))
    }

    @Test
    fun `sustantivo el seguro vence no captura`() {
        assertNull(analyze("el seguro del coche vence mañana"))
    }

    @Test
    fun `duda quizas dar de baja el seguro no captura`() {
        assertNull(analyze("quizá dar de baja el seguro mañana"))
    }

    @Test
    fun `articulo indefinido dar de alta un seguro no captura acotado`() {
        assertNull(analyze("dar de alta un seguro de vida"))
    }

    // ─── Regresiones (byte-idénticas) ─────────────────────────────

    @Test
    fun `dar de alta la luz sigue TASK c1139`() {
        val intent = analyze("dar de alta la luz mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Dar de alta la luz", intent.title)
    }

    @Test
    fun `dar de baja el gimnasio sigue TASK c895c`() {
        val intent = analyze("dar de baja el gimnasio mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `dar de baja el internet sigue TASK c1139`() {
        val intent = analyze("dar de baja el internet del piso viejo")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `dar de alta el gimnasio sigue NULL acotado c895c`() {
        assertNull(analyze("dar de alta el gimnasio mañana"))
    }

    @Test
    fun `pagar el seguro sigue PAYMENT`() {
        val intent = analyze("pagar el seguro del coche mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
    }

    @Test
    fun `envolvente recuerdame dar de alta el seguro TASK c613`() {
        val intent = analyze("recuérdame dar de alta el seguro mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }
}
