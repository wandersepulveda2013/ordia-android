package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * c.1063: lateral ABIERTA transversal «tengo que no <piso>» (documentada
 * en c.1012/c.1018/c.1059, pin G4 estable de
 * [ContextIntentEngineDaleUnaPastillaIndefinidoDeltaTest]) — medición
 * transversal con sondas efímeras `/tmp/probe1061/Probe2..7.kt` (motor
 * real vía `tools/run_probe.sh`, HEAD c.1060 `6e27610`):
 *
 *  - «tengo que no <infinitivo>» captura como TASK 0.45 con la negación
 *    PRESERVADA en el título («No llamar a mamá») en los 7 pisos medidos
 *    (pastilla/pasear/sacar/llamar/pagar/comprar/cortar el pelo/regar):
 *    captura FIEL (recordatorio de prohibición, evita errores) — NO es
 *    «afirmar una negación», el título conserva el «No». PIN estable.
 *  - PERO el kind CALL escapa a TODA la familia de guards de negación:
 *    [imperativeIsNegated] (c.648) devuelve `false` para CALL (sólo
 *    cubre SHOPPING/PAYMENT/MEETING/HOUSEHOLD/EXERCISE/ERRAND/STUDY) y
 *    los patrones de [CALL_SPECIFIC] carecen de lookbehind `(?<!no )`.
 *    Resultado medido (P1: persiste exactamente lo OPUESTO, clase
 *    c.648/c.681/c.1009): «habría que no llamar a mamá» → CALL 0.57
 *    «Llamar a mamá»; «mañana no llamar a mamá» → CALL 0.67 con dueAt;
 *    «no hablar con mamá mañana» → CALL 0.62; «no llamaré a mamá
 *    mañana» → CALL 0.63. Las formas con envolvente NO condicional
 *    («tengo que no llamar…») se salvaban por azar: el guard de
 *    envolvente [imperativeIsWrapped] (c.653) descarta el CALL
 *    subordinado y el piso TASK c.613 recoge la frase con el «No» en
 *    el título. Los demás kinds de bono están protegidos (APPOINTMENT
 *    vía lookbehind de sus patrones: «habría que no ir al médico» →
 *    TASK «No ir al médico»; PAYMENT/HOUSEHOLD/SHOPPING/ERRAND vía
 *    cláusulas c.648).
 *
 * Fix mínimo (UN punto, hermano de c.648): cláusula CALL en
 * [imperativeIsNegated] con los verbos alineados a [CALL_SPECIFIC]
 * (llamar|hablar + futuros declarativos llamaré|hablaré c.656 +
 * telefonear keyword). Efecto medido POST: las formas con envolvente
 * condicional caen al piso TASK c.835 con la negación en el título
 * («habría que no llamar a mamá» → TASK «No llamar a mamá», fiel y
 * consistente con «tengo que no llamar…»); las formas desnudas con
 * bono temporal quedan NULL (conservador, pin simétrico de «mañana no
 * comprar pan» c.648). El «no» de OTRO verbo no bloquea (posicional,
 * inmediato): «no puedo, llamar a mamá mañana» sigue CALL.
 *
 * Acotado deliberado (UNA por ciclo): la duda «no sé si llamar a
 * mamá» → CALL 0.57 (marcador de duda no cubierto por c.649) y el
 * junk de envolvente + «no» + NO-infinitivo («tengo que no sé qué
 * hacer» → TASK «No sé qué hacer») son laterales hermanas ABIERTAS
 * ya medidas (sondas Probe3/Probe4/Probe7) — doctrina y fix aparte.
 */
class ContextIntentEngineNoLlamarDeltaTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- P1: envolvente condicional + «no» + verbo CALL (opuesto persistido) ----
    // POST: caen al piso TASK c.835 con la negación PRESERVADA en el título
    // (consistente con «tengo que no llamar…», pin G4 estable).

    @Test
    fun `habria que no llamar captura TASK con negacion en el titulo`() {
        val i = analyze("habría que no llamar a mamá")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("No llamar a mamá", i.title)
    }

    @Test
    fun `tendria que no llamar captura TASK con negacion en el titulo`() {
        val i = analyze("tendría que no llamar a mamá")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("No llamar a mamá", i.title)
    }

    @Test
    fun `deberia no llamar captura TASK con negacion en el titulo`() {
        val i = analyze("debería no llamar a mamá")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("No llamar a mamá", i.title)
    }

    @Test
    fun `habria que no hablar captura TASK con negacion en el titulo`() {
        val i = analyze("habría que no hablar con el banco")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("No hablar con el banco", i.title)
    }

    // ---- P1: bono temporal + «no» + verbo CALL (opuesto persistido) ----
    // POST: NULL conservador (pin simétrico de «mañana no comprar pan» c.648).

    @Test
    fun `manana no llamar queda NULL`() {
        assertNull(analyze("mañana no llamar a mamá"))
    }

    @Test
    fun `no llamar manana queda NULL`() {
        assertNull(analyze("no llamar a mamá mañana"))
    }

    @Test
    fun `manana no hablar queda NULL`() {
        assertNull(analyze("mañana no hablar con mamá"))
    }

    @Test
    fun `no hablar manana queda NULL`() {
        assertNull(analyze("no hablar con mamá mañana"))
    }

    @Test
    fun `no llamare manana queda NULL`() {
        assertNull(analyze("no llamaré a mamá mañana"))
    }

    @Test
    fun `manana no llamare queda NULL`() {
        assertNull(analyze("mañana no llamaré a mamá"))
    }

    @Test
    fun `no hablare manana queda NULL`() {
        assertNull(analyze("no hablaré con el cliente mañana"))
    }

    // ---- Regresiones afirmativas CALL (pin: NO deben cambiar) ----

    @Test
    fun `llamar a mama sigue CALL`() {
        val i = analyze("llamar a mamá")
        assertNotNull(i)
        assertEquals(ContextIntentKind.CALL, i!!.kind)
    }

    @Test
    fun `llamar al doctor con fecha sigue CALL`() {
        val i = analyze("llamar al doctor el viernes a las 4")
        assertNotNull(i)
        assertEquals(ContextIntentKind.CALL, i!!.kind)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `hablar con Maria sigue CALL`() {
        val i = analyze("hablar con María")
        assertNotNull(i)
        assertEquals(ContextIntentKind.CALL, i!!.kind)
    }

    @Test
    fun `llamare futuro declarativo sigue CALL`() {
        val i = analyze("llamaré a mamá el viernes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.CALL, i!!.kind)
    }

    @Test
    fun `hablare futuro declarativo sigue CALL`() {
        val i = analyze("hablaré con el cliente mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.CALL, i!!.kind)
    }

    @Test
    fun `recordame llamar sigue TASK`() {
        val i = analyze("recuérdame llamar al banco")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Llamar al banco", i.title)
    }

    @Test
    fun `tengo que llamar sigue TASK`() {
        val i = analyze("tengo que llamar a mamá")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
    }

    @Test
    fun `habria que llamar afirmativo sigue CALL pin c835`() {
        val i = analyze("habría que llamar al fontanero")
        assertNotNull(i)
        assertEquals(ContextIntentKind.CALL, i!!.kind)
    }

    // ---- Borde posicional: el «no» pertenece a OTRO verbo (no bloquear) ----

    @Test
    fun `no de otro verbo no bloquea la llamada`() {
        val i = analyze("no puedo, llamar a mamá mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.CALL, i!!.kind)
    }

    // ---- Pins de negación ya cubierta (no deben cambiar) ----

    @Test
    fun `no tengo que llamar sigue NULL pin c681`() {
        assertNull(analyze("no tengo que llamar a mamá"))
    }

    @Test
    fun `manana no comprar pan sigue NULL pin c648`() {
        assertNull(analyze("mañana no comprar pan"))
    }

    // ---- Pin hermano estable: envolvente + «no» + infinitivo (captura fiel) ----

    @Test
    fun `tengo que no llamar sigue TASK con negacion en el titulo`() {
        val i = analyze("tengo que no llamar a mamá")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertTrue(i.title.startsWith("No "))
    }
}
