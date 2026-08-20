package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.763: ancla
 * temporal compartida "que viene" — extensión de
 * [ContextIntentEngine.TASK_FLOOR_TEMPORAL] con el sufijo opcional
 * `(?:\s+que\s+viene)?` tras el día de la semana y las alternativas
 * `la semana que viene` / `el mes que viene`.
 *
 * Defecto de CLASE (residual OPEN de c.746): el calificador "que viene"
 * entre el temporal y el verbo rompía TODOS los pisos que comparten el
 * ancla — el regex exigía el verbo INMEDIATO al temporal, así "el lunes
 * que viene pagar el arriendo" (y "la semana que viene pagar la renta",
 * y cualquier "el <día>/la semana/el mes que viene <verbo-piso>") se
 * DESCARTABA silenciosamente (NULL, olvido silencioso del pago de mayor
 * coste del dominio). [ContextIntentEngine.extractDateTime] ya resolvía
 * esos períodos (c.598) y `NaturalTaskParser.weekdayPattern` ya consumía
 * la frase completa (paridad: "lunes que viene" = próximo lunes estricto
 * en ambos) — la brecha era exclusiva del ancla de los pisos. La
 * extensión se hace UNA vez en la constante compartida y propaga en
 * lockstep a todos los pisos, plantillas de título y guard de envolvente
 * `WRAPPABLE_PATTERNS` (lección de clase c.643/c.647/c.746).
 *
 * Acotamientos verificados: la negación intermedia sigue bloqueando (el
 * "no " entre ancla y verbo impide el match del piso), la duda casa el
 * piso pero la penalización post-pisos [ContextIntentEngine.HEDGE_PENALTY]
 * la descarta (0.45−0.3→NULL), el pasado ("pagué") no casa el verbo del
 * piso y el temporal sin verbo no casa ningún piso.
 */
class ContextIntentEngineQueVieneAnchorTest {

    private fun analyze(raw: String): ContextIntent? =
        ContextIntentEngine.analyze(
            ContextEvent(ContextCaptureSource.NOTIFICATION, raw, 1723939200000L)
        )

    // --- Capturas: temporal + "que viene" + verbo de piso ---

    @Test
    fun `captura el lunes que viene pagar el arriendo`() {
        val intent = analyze("el lunes que viene pagar el arriendo")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
        assertNotNull("el temporal que viene debe fijar dueAt", intent.dueAt)
        assertEquals("Pagar el arriendo", intent.title)
    }

    @Test
    fun `captura la semana que viene pagar la renta`() {
        val intent = analyze("la semana que viene pagar la renta")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
        assertNotNull(intent.dueAt)
        assertEquals("Pagar la renta", intent.title)
    }

    @Test
    fun `captura el mes que viene pagar el arriendo`() {
        val intent = analyze("el mes que viene pagar el arriendo")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
        assertNotNull(intent.dueAt)
        assertEquals("Pagar el arriendo", intent.title)
    }

    @Test
    fun `captura el viernes que viene pagar el recibo de la luz`() {
        val intent = analyze("el viernes que viene pagar el recibo de la luz")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.PAYMENT, intent!!.kind)
        assertNotNull(intent.dueAt)
        assertEquals("Pagar el recibo de la luz", intent.title)
    }

    @Test
    fun `captura el lunes que viene revisar el informe`() {
        val intent = analyze("el lunes que viene revisar el informe")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertNotNull(intent.dueAt)
        assertEquals("Revisar el informe", intent.title)
    }

    @Test
    fun `captura el lunes que viene enviar el paquete`() {
        val intent = analyze("el lunes que viene enviar el paquete")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertNotNull(intent.dueAt)
        assertEquals("Enviar el paquete", intent.title)
    }

    @Test
    fun `captura la semana que viene entregar la tarea`() {
        val intent = analyze("la semana que viene entregar la tarea")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertNotNull(intent.dueAt)
        assertEquals("Entregar la tarea", intent.title)
    }

    @Test
    fun `captura el sabado que viene llamar al banco`() {
        val intent = analyze("el sábado que viene llamar al banco")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.CALL, intent!!.kind)
        assertNotNull(intent.dueAt)
        assertEquals("Llamar al banco", intent.title)
    }

    @Test
    fun `captura la semana que viene cita con el dentista`() {
        val intent = analyze("la semana que viene cita con el dentista")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.APPOINTMENT, intent!!.kind)
        assertNotNull(intent.dueAt)
        assertEquals("Cita con el dentista", intent.title)
    }

    @Test
    fun `captura el lunes que viene limpiar la cocina`() {
        val intent = analyze("el lunes que viene limpiar la cocina")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertNotNull(intent.dueAt)
        assertEquals("Limpiar la cocina", intent.title)
    }

    @Test
    fun `envolvente recuerdame el lunes que viene pagar resta TASK`() {
        // Lockstep c.648/c.652: el guard de envolvente comparte el ancla,
        // así "recuérdame <temporal que viene> <pago>" sigue siendo TASK.
        val intent = analyze("recuérdame el lunes que viene pagar el arriendo")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    // --- Descartes anti-overreach ---

    @Test
    fun `descarta negacion intermedia el lunes que viene no pagar`() {
        assertNull(analyze("el lunes que viene no pagar el arriendo"))
    }

    @Test
    fun `descarta duda quiza el lunes que viene pagar`() {
        assertNull(analyze("quizá el lunes que viene pagar el arriendo"))
    }

    @Test
    fun `descarta pasado el lunes que viene pague`() {
        assertNull(analyze("el lunes que viene pagué el arriendo"))
    }

    @Test
    fun `descarta temporal que viene sin verbo`() {
        assertNull(analyze("el lunes que viene"))
        assertNull(analyze("la semana que viene"))
    }
}
