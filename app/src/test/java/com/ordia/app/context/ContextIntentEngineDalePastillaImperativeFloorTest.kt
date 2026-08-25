package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1050: lateral ABIERTA documentada en c.1012 (pin FUERA
 * `imperativo segunda persona fuera lateral documentada` de
 * [ContextIntentEngineDarlePastillaFloorTest]) — el imperativo 2ª
 * persona «dale la pastilla al perro/gato». En captura PASIVA el
 * enunciado llega como notificación de un familiar (la vía real del
 * motor, [ContextCaptureSource.NOTIFICATION]): «dale la pastilla al
 * perro» en un mensaje ES un compromiso dirigido al usuario —
 * exactamente cómo se delega el cuidado de la mascota en la
 * coordinación familiar (olvido silencioso P1/P2 medido). El piso
 * dativo [HOUSEHOLD_PILL_DATIVE_FLOOR] c.1012 sólo cubre el
 * infinitivo «darle/darles» (así se habla el quick-capture propio),
 * no el imperativo dirigido (así se habla la delegación recibida).
 * Medida PRE con sonda efímera `/tmp/probe1048/DalePastillaPreProbe.kt`
 * (motor real vía `tools/run_probe.sh`, HEAD c.1047): 6/6 candidatas
 * NULL (gap confirmado), 8/8 guards NULL correctos, 6/6 regresiones
 * HIT intactas.
 * Fix mínimo (lockstep TRES puntos, lección c.616/c.751; hermano
 * estructural de la extensión de objeto c.1046 «pasear al gato»):
 * la alternancia de verbo del piso pasa de `dar(?:le|les)` a
 * `(?:dar(?:le|les)|dale?s?)` en (1) el piso
 * [HOUSEHOLD_PILL_DATIVE_FLOOR], (2) la cláusula de negación
 * dedicada de [imperativeIsNegated] (cinturón y tirantes simétrico,
 * precedente c.829/c.1011/c.1012) y (3) la plantilla de título de
 * [extractTitle] («Dale la pastilla al perro» — grafía del usuario
 * preservada, doctrina c.653). CERO keywords nuevas (lección
 * c.751): la keyword-mascota preexistente abre el gate y el piso
 * ancla. Anti-overreach intacto (guards medidos PRE→POST NULL):
 * negación inmediata (lookbehind + cláusula), subjuntivo «no le
 * des…» (no casa la alternancia), destinatario humano «al niño»,
 * forma sin objeto, hedge «quizá…», sintagma nominal «la pastilla
 * del perro», «dale» bivalente idiomático («dale duro al equipo» —
 * sin ancla de objeto no casa). Acotado deliberado (UNA por ciclo):
 * la forma con artículo INDEFINIDO «dale una pastilla al perro»
 * queda FUERA (pin G8 — lateral hermana ABIERTA; el piso dativo
 * hermano c.1012 tampoco admite indefinido, a diferencia del piso
 * de vacuna c.1011+c.1014). Re-pin legítimo (precedente
 * c.1019/c.1024/c.1046): el pin FUERA de c.1012 pasa a captura
 * RESUELTA en su clase de origen.
 */
class ContextIntentEngineDalePastillaImperativeFloorTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Capturas imperativas (piso extendido) ----

    @Test
    fun `captura imperativo dale con destinatario y fecha`() {
        val i = analyze("dale la pastilla al perro mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Dale la pastilla al perro", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura imperativo dale con plural pastillas y posesivo`() {
        val i = analyze("dale las pastillas a mi gato")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Dale las pastillas a mi gato", i.title)
    }

    @Test
    fun `captura imperativo dales con dativo plural y fecha`() {
        val i = analyze("dales la pastilla a los perros el sábado")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Dales la pastilla a los perros", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura imperativo tras acuse de escucha`() {
        val i = analyze("vale, dale la pastilla al gato")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Dale la pastilla al gato", i.title)
    }

    @Test
    fun `captura imperativo sin fecha`() {
        val i = analyze("dale la pastilla al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Dale la pastilla al perro", i.title)
    }

    @Test
    fun `captura imperativo con temporal esta noche`() {
        val i = analyze("dale la pastilla al gato esta noche")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Dale la pastilla al gato", i.title)
        assertNotNull(i.dueAt)
    }

    // ---- Guards (NULL correctos) ----

    @Test
    fun `negacion inmediata imperativo bloqueada`() {
        assertNull(analyze("no dale la pastilla al perro"))
    }

    @Test
    fun `negacion subjuntivo no le des fuera`() {
        assertNull(analyze("no le des la pastilla al perro"))
    }

    @Test
    fun `destinatario humano imperativo fuera`() {
        assertNull(analyze("dale la pastilla al niño"))
    }

    @Test
    fun `imperativo sin objeto ancla fuera`() {
        assertNull(analyze("dale la pastilla"))
    }

    @Test
    fun `hedge imperativo no captura`() {
        assertNull(analyze("quizá dale la pastilla al perro"))
    }

    @Test
    fun `sintagma nominal sigue sin capturar`() {
        assertNull(analyze("la pastilla del perro"))
    }

    @Test
    fun `dale idiomatico sin ancla de objeto fuera`() {
        assertNull(analyze("dale duro al equipo esta tarde"))
    }

    @Test
    fun `indefinido dale una pastilla fuera lateral documentada`() {
        assertNull(analyze("dale una pastilla al perro"))
    }

    // ---- Regresiones (HIT intactas) ----

    @Test
    fun `regresion piso dativo infinitivo c1012`() {
        val i = analyze("darle la pastilla al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Darle la pastilla al perro", i.title)
    }

    @Test
    fun `regresion piso dativo vacuna c1011`() {
        val i = analyze("ponerle la vacuna al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Ponerle la vacuna al perro", i.title)
    }

    @Test
    fun `regresion piso dativo unas c1015`() {
        val i = analyze("cortarle las uñas al gato")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Cortarle las uñas al gato", i.title)
    }

    @Test
    fun `regresion piso tomar medicacion c859`() {
        val i = analyze("tomar la medicación")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Tomar la medicación", i.title)
    }

    @Test
    fun `regresion piso pasear gato c1046`() {
        val i = analyze("pasear al gato")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Pasear al gato", i.title)
    }

    @Test
    fun `regresion piso vacunar c757`() {
        val i = analyze("vacunar al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Vacunar al perro", i.title)
    }
}
