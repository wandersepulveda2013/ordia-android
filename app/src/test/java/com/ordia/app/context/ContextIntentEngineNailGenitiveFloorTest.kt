package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1024 (renumerada c.1021->c.1022->c.1023->c.1024 por TRIPLE
 * colisión de cycle-ID con el c.1021 assistant ARCHIVE, el c.1022
 * assistant honestidad «borra» y el c.1023 parser delta del hermano
 * — regiones SIEMPRE DISJUNTAS, doctrina: numerar tras re-fetch):
 * lateral ABIERTA de c.1015 (piso dativo
 * [HOUSEHOLD_NAIL_DATIVE_FLOOR]) — la forma SIN dativo con genitivo
 * mascota «cortar las uñas del gato» (pineada FUERA en c.1015). Es
 * la forma dicho-como-se-habla del mismo quehacer: «cortar las
 * uñas del gato mañana» / «cortar las uñas de mi perro el sábado»
 * / «cortar las uñas al perro» / la coloquial dativo+genitivo
 * «cortarle las uñas del gato». PRE medido con sonda efímera
 * `/tmp/probe1021/Probe.kt` (motor real vía `tools/run_probe.sh`)
 * sobre HEAD `fed4dbd9`: 4/4 formas peladas NULL (olvido
 * silencioso P1 — la keyword-mascota sola queda bajo el umbral,
 * misma aritmética medida c.1011/c.1012/c.1015/c.1018); las 2
 * formas con envolvente de obligación («hay que…», «tengo que…»)
 * YA capturan TASK con título limpio (pin byte-idéntico — la
 * envolvente gobierna, seam c.613, misma doctrina que
 * «recuérdame pasear al perro» c.1020); 7/7 guards NULL correctos
 * (negación inmediata, «no voy a…» c.1009, pasado, hedge
 * subjuntivo, destinatario humano «del niño», sin destinatario
 * «cortar las uñas» [uñas propias bivalentes], nominalización
 * «el corte de uñas del gato»); 4/4 regresiones intactas (dativo
 * c.1015 HOUSEHOLD, «cortarme las uñas» NULL, césped HOUSEHOLD,
 * pelo dativo c.1006 ERRAND).
 * Fix mínimo (lockstep TRES puntos, lección c.616/c.751; extensión
 * in-situ del piso hermano c.1015): dativo `(le|les)` OPCIONAL +
 * conector genitivo `del|de (art.)` añadido a la alternancia del
 * destinatario, en el piso [HOUSEHOLD_NAIL_DATIVE_FLOOR], en su
 * cláusula de negación dedicada de [imperativeIsNegated] y en la
 * plantilla de título de [extractTitle]. CERO keywords nuevas:
 * «cortar» y «uñas» NO se añaden (bivalentes); el piso basta.
 * Acotado intacto: el destinatario sigue exigiendo mascota
 * `perr[oa]s?|gat[oa]s?` («del niño» FUERA, pin), la forma sin
 * destinatario («cortar las uñas» — uñas propias) FUERA (pin
 * byte-idéntico), la negación la bloquean el lookbehind Y la
 * cláusula, y el guard c.1009 descarta «no voy a cortar…» antes
 * del piso (pin).
 */
class ContextIntentEngineNailGenitiveFloorTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Capturas (formas peladas — RED: EXACTAMENTE 4 fallos) ----

    @Test
    fun `captura genitivo del gato con cola temporal`() {
        val i = analyze("cortar las uñas del gato mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Cortar las uñas del gato", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura genitivo de mi perro con weekday`() {
        val i = analyze("cortar las uñas de mi perro el sábado")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Cortar las uñas de mi perro", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura conector al perro sin dativo`() {
        val i = analyze("cortar las uñas al perro el lunes")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Cortar las uñas al perro", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura dativo con genitivo coloquial`() {
        val i = analyze("cortarle las uñas del gato mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Cortarle las uñas del gato", i.title)
        assertNotNull(i.dueAt)
    }

    // ---- Pines byte-idénticos PRE: la envolvente de obligación
    // gobierna TASK (seam c.613; NO las toca este fix) ----

    @Test
    fun `pin hay que gobierna TASK`() {
        val i = analyze("hay que cortar las uñas del gato")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Cortar las uñas del gato", i.title)
    }

    @Test
    fun `pin tengo que gobierna TASK con fecha`() {
        val i = analyze("tengo que cortar las uñas de la gata esta tarde")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Cortar las uñas de la gata", i.title)
        assertNotNull(i.dueAt)
    }

    // ---- Guards (NULL antes y después) ----

    @Test
    fun `negacion inmediata no captura`() {
        assertNull(analyze("no cortar las uñas del gato"))
    }

    @Test
    fun `plan negado c1009 no captura`() {
        assertNull(analyze("no voy a cortar las uñas del gato hoy"))
    }

    @Test
    fun `pasado no captura`() {
        assertNull(analyze("corté las uñas del gato ayer"))
    }

    @Test
    fun `duda subjuntivo no captura`() {
        assertNull(analyze("quizá corte las uñas del gato"))
    }

    @Test
    fun `destinatario humano no captura`() {
        assertNull(analyze("cortar las uñas del niño mañana"))
    }

    @Test
    fun `sin destinatario no captura`() {
        assertNull(analyze("cortar las uñas mañana"))
    }

    @Test
    fun `nominalizacion no captura`() {
        assertNull(analyze("el corte de uñas del gato"))
    }

    // ---- Regresiones (HIT/NULL intactos) ----

    @Test
    fun `regresion dativo c1015`() {
        val i = analyze("cortarle las uñas al gato mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Cortarle las uñas al gato", i.title)
    }

    @Test
    fun `regresion unas propias sigue FUERA`() {
        assertNull(analyze("cortarme las uñas"))
    }
}
