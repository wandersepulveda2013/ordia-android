package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * c.1064: doctrina de la lateral transversal «envolvente + no» (abierta en
 * c.1012/c.1018/c.1059, resuelta para CALL en c.1063). La medición
 * transversal (sondas efímeras `/tmp/probe1061/Probe2..8.kt`, motor real
 * vía `tools/run_probe.sh`) separó DOS clases:
 *
 *  1. wrapper + «no» + INFINITIVO → captura FIEL como TASK con la negación
 *     en el título («tengo que no darle la pastilla al perro» → «No darle
 *     la pastilla al perro»): recordatorio de prohibición real, evita
 *     errores. Doctrina: CONSERVAR (pin estable, c.1063).
 *  2. wrapper + «no» + NO-infinitivo → captura JUNK (P2): el piso
 *     [hasStrongTaskImperative] / [hasStrongReminderImperative] sólo exige
 *     `\s+\w` tras el envolvente, así la palabra «no» MISMA satisface el
 *     piso y el resto del enunciado se persiste como tarea basura:
 *     «tengo que no sé qué hacer» → TASK «No sé qué hacer», «tengo que no
 *     mañana» → TASK «No mañana» ¡con dueAt!, «avísame no sé qué» →
 *     REMINDER «No sé qué», «cancelar no sé qué» → TASK «Cancelar no sé
 *     qué», «tengo que no, mejor mañana» → TASK «No, mejor» con dueAt.
 *     Doctrina: SUPRIMIR (NULL conservador) — el usuario no está
 *     comprometiendo ninguna acción; persistir ruido degrada la inbox.
 *
 * Fix mínimo (UN guard, dos sitios de piso): [wrapperNegationLacksInfinitive]
 * detecta el span «envolvente + no + palabra» y exige que la palabra sea
 * INFINITIVO-like (`\w*(?:ar|er|ir)` + enclíticos `{0,2}`: «darle»,
 * «cortarme», «decírselo», «ir»). Si no lo es, el piso TASK/REMINDER no
 * dispara. Los afirmativos y los fieles con infinitivo quedan intactos
 * (pins medidos PRE→POST).
 *
 * Acotado deliberado: el junk AFIRMATIVO de envolvente («tengo que es
 * eso», «tengo que sí, claro», «tengo que mañana») es sub-lateral hermana
 * ABIERTA (medida en Probe4) — exigir infinitivo tras TODO envolvente
 * rompería capturas legítimas («avísame mañana de la reunión»), doctrina
 * aparte. La duda «no sé si llamar a mamá» → CALL 0.57 también queda
 * ABIERTA (marcador de duda no cubierto por c.649).
 */
class ContextIntentEngineWrapperNoJunkDeltaTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Junk: wrapper + «no» + NO-infinitivo → NULL (piso suprimido) ----

    @Test
    fun `tengo que no se que hacer queda NULL`() {
        assertNull(analyze("tengo que no sé qué hacer"))
    }

    @Test
    fun `hay que no es eso queda NULL`() {
        assertNull(analyze("hay que no es eso"))
    }

    @Test
    fun `recuerdame no se que queda NULL`() {
        assertNull(analyze("recuérdame no sé qué"))
    }

    @Test
    fun `no olvides no es eso queda NULL`() {
        assertNull(analyze("no olvides no es eso"))
    }

    @Test
    fun `habria que no se que queda NULL`() {
        assertNull(analyze("habría que no sé qué"))
    }

    @Test
    fun `tengo que no manana queda NULL`() {
        assertNull(analyze("tengo que no mañana"))
    }

    @Test
    fun `tengo que no hay pan queda NULL`() {
        assertNull(analyze("tengo que no hay pan"))
    }

    @Test
    fun `tengo que no tengo tiempo queda NULL`() {
        assertNull(analyze("tengo que no tengo tiempo"))
    }

    @Test
    fun `avisame no se que queda NULL`() {
        assertNull(analyze("avísame no sé qué"))
    }

    @Test
    fun `notificame no es eso queda NULL`() {
        assertNull(analyze("notifícame no es eso"))
    }

    @Test
    fun `cancelar no se que queda NULL`() {
        assertNull(analyze("cancelar no sé qué"))
    }

    @Test
    fun `tengo que no coma mejor manana queda NULL`() {
        assertNull(analyze("tengo que no, mejor mañana"))
    }

    // ---- Fieles: wrapper + «no» + INFINITIVO → TASK con «No» (pins) ----

    @Test
    fun `tengo que no darle la pastilla sigue TASK fiel`() {
        val i = analyze("tengo que no darle la pastilla al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("No darle la pastilla al perro", i.title)
    }

    @Test
    fun `habria que no llamar sigue TASK fiel`() {
        val i = analyze("habría que no llamar a mamá")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("No llamar a mamá", i.title)
    }

    @Test
    fun `tengo que no ir al medico sigue TASK fiel`() {
        val i = analyze("tengo que no ir al médico")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("No ir al médico", i.title)
    }

    @Test
    fun `tengo que no cortarme el pelo sigue TASK fiel`() {
        val i = analyze("tengo que no cortarme el pelo")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("No cortarme el pelo", i.title)
    }

    @Test
    fun `habria que no reunirme sigue TASK fiel`() {
        val i = analyze("habría que no reunirme con el equipo")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("No reunirme con el equipo", i.title)
    }

    @Test
    fun `tengo que no decirselo sigue TASK fiel`() {
        val i = analyze("tengo que no decírselo a nadie")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertTrue(i.title.startsWith("No decírselo"))
    }

    // ---- Afirmativos de control (pins: NO deben cambiar) ----

    @Test
    fun `tengo que llamar sigue TASK`() {
        val i = analyze("tengo que llamar a mamá")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
    }

    @Test
    fun `recuerdame comprar pan sigue TASK`() {
        val i = analyze("recuérdame comprar pan")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
    }

    @Test
    fun `habria que comprar leche sigue TASK`() {
        val i = analyze("habría que comprar leche")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
    }

    @Test
    fun `no olvides pagar la luz sigue TASK`() {
        val i = analyze("no olvides pagar la luz")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
    }

    @Test
    fun `avisame manana de la reunion sigue REMINDER`() {
        val i = analyze("avísame mañana de la reunión")
        assertNotNull(i)
        assertEquals(ContextIntentKind.REMINDER, i!!.kind)
    }

    @Test
    fun `cancelar la cita sigue TASK`() {
        val i = analyze("cancelar la cita del dentista")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
    }
}
