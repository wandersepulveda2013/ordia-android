package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1059: lateral ABIERTA documentada en c.1012/c.1050 (pin G8
 * `indefinido dale una pastilla fuera lateral documentada` de
 * [ContextIntentEngineDalePastillaImperativeFloorTest]) — la forma
 * con artículo INDEFINIDO «dale una pastilla al perro» /
 * «darle una pastilla al gato». En captura PASIVA el enunciado
 * llega como notificación de un familiar (la vía real del motor,
 * [ContextCaptureSource.NOTIFICATION]): «dale una pastilla al
 * perro» ES un compromiso dirigido al usuario (delegación del
 * cuidado de la mascota en la coordinación familiar; olvido
 * silencioso P1/P2 medido). El piso dativo
 * [HOUSEHOLD_PILL_DATIVE_FLOOR] c.1012+c.1050 sólo admitía el
 * artículo DEFINIDO `(?:el|la|los|las)`, a diferencia del piso
 * de vacuna hermano c.1011+c.1014 que ya admite `(?:el|la|los|las|un|una)`.
 * Medida PRE con sonda efímera `/tmp/probe1058/Probe.kt` (motor
 * real vía `tools/run_probe.sh`, HEAD c.1057 `62bcbcb`): 6/6
 * candidatas NULL (gap confirmado), 3/3 regresiones HIT intactas,
 * 3/3 FUERA NULL correctos.
 * Fix mínimo (lockstep TRES puntos, lección c.616/c.751; hermano
 * estructural de la extensión de artículo del piso de vacuna
 * c.1014): la alternancia de artículo del piso pasa de
 * `(?:el|la|los|las)` a `(?:el|la|los|las|un|una|unos|unas)` en
 * (1) el piso [HOUSEHOLD_PILL_DATIVE_FLOOR], (2) la cláusula de
 * negación dedicada de [imperativeIsNegated] (cinturón y tirantes
 * simétrico, precedente c.829/c.1011/c.1012/c.1050) y (3) la
 * plantilla de título de [extractTitle] («Dale una pastilla al
 * perro» — grafía del usuario preservada, doctrina c.653). CERO
 * keywords nuevas (lección c.751): la keyword-mascota preexistente
 * abre el gate y el piso ancla. Anti-overreach intacto (guards
 * medidos PRE→POST NULL): negación inmediata (lookbehind +
 * cláusula), pasado «le di…» (no casa la alternancia), hedge
 * «quizá…». La negación envolvente «tengo que no darle…» captura
 * como TASK con la negación en el título — comportamiento ESTABLE
 * transversal medido sobre pisos preexistentes (definido c.1012,
 * pasear c.1018), NO regresión de este ciclo; lateral ABIERTA
 * transversal documentada (afirmar una negación, potencial P1/P2
 * en todos los pisos — doctrina aparte),
 * destinatario humano indefinido «dale una pastilla al niño»,
 * sintagma nominal «la pastilla del perro». Acotado deliberado
 * (UNA por ciclo): el piso humano «tomar pastillas» c.859 NO se
 * toca (su indefinido «tomar una pastilla» es lateral hermana
 * ABIERTA — bivalente con keyword humana, medición aparte); el
 * piso dativo de uñas c.1015 tampoco admite indefinido (lateral
 * hermana ABIERTA). Re-pin legítimo (precedente
 * c.1019/c.1024/c.1046/c.1052/c.1054/c.1056/c.1057): el pin G8 de
 * c.1050 pasa a captura RESUELTA en su clase de origen y se
 * reubica al FUERA estructural «dale una pastilla al niño»
 * (destinatario humano indefinido — la alternancia admite
 * indefinido pero el destinatario sigue acotado a mascota).
 */
class ContextIntentEngineDaleUnaPastillaIndefinidoDeltaTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Capturas indefinidas (piso extendido) ----

    @Test
    fun `captura imperativo dale una pastilla al perro`() {
        val i = analyze("dale una pastilla al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Dale una pastilla al perro", i.title)
    }

    @Test
    fun `captura infinitivo darle una pastilla al gato`() {
        val i = analyze("darle una pastilla al gato")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Darle una pastilla al gato", i.title)
    }

    @Test
    fun `captura plural indefinido dale unas pastillas al perro`() {
        val i = analyze("dale unas pastillas al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Dale unas pastillas al perro", i.title)
    }

    @Test
    fun `captura posesivo darle una pastilla a mi gato`() {
        val i = analyze("darle una pastilla a mi gato")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Darle una pastilla a mi gato", i.title)
    }

    @Test
    fun `captura indefinido con hora numerica`() {
        val i = analyze("dale una pastilla al perro a las 9")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Dale una pastilla al perro", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura indefinido con temporal manana`() {
        val i = analyze("darle una pastilla al gato mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Darle una pastilla al gato", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `envolvente tengo que con indefinido captura como tarea`() {
        val i = analyze("tengo que darle una pastilla al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
    }

    @Test
    fun `captura indefinido tras acuse de escucha`() {
        val i = analyze("vale, dale una pastilla al gato")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Dale una pastilla al gato", i.title)
    }

    // ---- Guards (NULL correctos) ----

    @Test
    fun `negacion inmediata indefinido bloqueada`() {
        assertNull(analyze("no darle una pastilla al perro"))
    }

    @Test
    fun `negacion envolvente captura como tarea comportamiento estable`() {
        // Comportamiento ESTABLE del motor (medido con sonda
        // `/tmp/probe1058/Probe2.kt` sobre pisos PREEXISTENTES:
        // «tengo que no darle la pastilla al perro» definido c.1012
        // y «tengo que no pasear al perro» c.1018 también dan TASK
        // con la negación preservada en el título). NO es regresión
        // de c.1059: la envolvente «tengo que» atrapa antes que el
        // guard de negación. Lateral ABIERTA transversal (afirmar
        // una negación es potencial P1/P2 en TODOS los pisos —
        // medición y doctrina aparte, UNA por ciclo).
        val i = analyze("tengo que no darle una pastilla al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
    }

    @Test
    fun `pasado indefinido no captura`() {
        assertNull(analyze("le di una pastilla al perro"))
    }

    @Test
    fun `hedge indefinido no captura`() {
        assertNull(analyze("quizá dale una pastilla al perro"))
    }

    // ---- Regresiones (HIT intactas) ----

    @Test
    fun `regresion imperativo definido c1050`() {
        val i = analyze("dale la pastilla al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Dale la pastilla al perro", i.title)
    }

    @Test
    fun `regresion infinitivo plural definido c1012`() {
        val i = analyze("darle las pastillas al gato")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Darle las pastillas al gato", i.title)
    }

    @Test
    fun `regresion vacuna indefinido c1014`() {
        val i = analyze("ponerle una vacuna al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Ponerle una vacuna al perro", i.title)
    }

    // ---- FUERA estructurales (NULL deliberados) ----

    @Test
    fun `destinatario humano indefinido fuera`() {
        assertNull(analyze("dale una pastilla al niño"))
    }

    @Test
    fun `sintagma nominal indefinido ausente fuera`() {
        assertNull(analyze("la pastilla del perro"))
    }
}
