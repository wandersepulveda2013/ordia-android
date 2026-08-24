package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1006: lateral DATIVA del piso ERRAND «cortar el pelo» c.842 —
 * «cortarle el pelo al niño» (la peluquería del hijo, de los recados
 * familiares más cotidianos). Candidata documentada ABIERTA en la
 * propia fila del piso c.842 (acotado deliberado, UNA por ciclo):
 * plural «los pelos», dativo «cortarle el pelo al niño» y objeto
 * «cabello» quedaban FUERA; esta unidad resuelve SOLO el dativo.
 * NULL PRE medido con sonda efímera `/tmp/probe1006/Probe.kt` sobre
 * HEAD `b3eb86e` (motor real vía `tools/run_probe.sh`): las 4 formas
 * dativas NULL (olvido silencioso P1 — el enclítico dativo «le/les»
 * rompía el verbo literal `cortar(?:me|te|se|nos)?` del piso, misma
 * clase de defecto que la sexta clase c.834/c.836/c.840), mientras
 * las regresiones c.842 intactas HIT y los guards NULL correctos
 * (negación, pasado «le corté…», césped con dativo «cortarle el
 * césped al vecino» — objeto distinto — y bivalente «comunicación»).
 * Fix mínimo (lockstep TRES puntos, lección c.616/c.751): el grupo
 * enclítico del piso pasa a `(?:me|te|se|nos|le|les)?` (precedente
 * dativo «llevarle/devolverle» c.854/c.855 y «echarle/echarles»
 * c.833) + la MISMA extensión en la cláusula de negación dedicada de
 * [imperativeIsNegated] (cinturón y tirantes, precedente c.829/c.842)
 * + la MISMA extensión en la plantilla de título de [extractTitle]
 * (pronombre conservado, doctrina c.653). La keyword-OBJETO «pelo»
 * ya existe (c.842): cero cambios en `ContextIntent.kt`. El
 * destinatario («al niño»/«a mi hijo») NO se despoja del título —
 * es contenido (a quién se le corta), no residuo temporal;
 * [sanitizeTitle] sigue depurando la cola temporal («…el sábado»).
 * Anti-overreach intacto: el ancla-objeto `pelo` blinda la
 * bivalencia («cortarle el césped al vecino» NULL medido), la
 * negación inmediata la bloquean el lookbehind del piso y la
 * cláusula, el pasado («le corté el pelo») no casa el infinitivo
 * literal y el hedge «quizá…» sigue NULL. Acotado deliberado (UNA
 * forma por ciclo): plural «los pelos» y objeto «cabello» quedan
 * FUERA — candidatas documentadas c.842.
 */
class ContextIntentEngineCortarlePeloFloorTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Capturas dativas (piso) ----

    @Test
    fun `captura dativo le con destinatario y fecha`() {
        val i = analyze("cortarle el pelo al niño el sábado")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Cortarle el pelo al niño", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura dativo le con posesivo y fecha`() {
        val i = analyze("cortarle el pelo a mi hijo mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Cortarle el pelo a mi hijo", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura dativo plural les`() {
        val i = analyze("cortarles el pelo a los niños el viernes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Cortarles el pelo a los niños", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura dativo le sin destinatario`() {
        val i = analyze("cortarle el pelo mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Cortarle el pelo", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura dativo sin fecha sigue capturando`() {
        val i = analyze("cortarle el pelo al niño")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Cortarle el pelo al niño", i.title)
    }

    @Test
    fun `captura dativo tras acuse`() {
        val i = analyze("vale, cortarle el pelo al niño el sábado")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Cortarle el pelo al niño", i.title)
        assertNotNull(i.dueAt)
    }

    // ---- Guards (NULL correctos) ----

    @Test
    fun `negacion dativa no captura`() {
        assertNull(analyze("no cortarle el pelo al niño mañana"))
    }

    @Test
    fun `pasado no captura`() {
        assertNull(analyze("le corté el pelo al niño ayer"))
    }

    @Test
    fun `objeto distinto con dativo no captura`() {
        assertNull(analyze("cortarle el césped al vecino mañana"))
    }

    @Test
    fun `hedge dativo no captura`() {
        assertNull(analyze("quizá cortarle el pelo al niño el sábado"))
    }

    @Test
    fun `verbo dativo aislado no captura`() {
        assertNull(analyze("cortarle"))
    }

    // ---- Regresiones c.842 (intactas) ----

    @Test
    fun `regresion reflexiva me intacta`() {
        val i = analyze("cortarme el pelo el sábado")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Cortarme el pelo", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `regresion forma desnuda intacta`() {
        val i = analyze("cortar el pelo mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.ERRAND, i!!.kind)
        assertEquals("Cortar el pelo", i.title)
        assertNotNull(i.dueAt)
    }
}
