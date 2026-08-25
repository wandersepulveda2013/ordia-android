package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1061: lateral ABIERTA (2) documentada en c.1059 — la forma con
 * artículo INDEFINIDO del dativo de uñas «cortarle una uña al gato» /
 * «cortarle unas uñas al perro» (una uña rota que hay que recortar,
 * «unas uñas» = algunas; dicho-como-se-habla en la coordinación del
 * cuidado de la mascota). En captura PASIVA el enunciado llega como
 * notificación ([ContextCaptureSource.NOTIFICATION]) y es un
 * compromiso real de higiene de la mascota (olvido silencioso P1).
 * El piso dativo [HOUSEHOLD_NAIL_DATIVE_FLOOR] c.1015+c.1024 sólo
 * admitía el artículo DEFINIDO `(?:el|la|los|las)`, a diferencia de
 * sus hermanos estructurales: el piso de vacuna c.1011+c.1014 y el
 * piso de pastilla c.1012+c.1050+c.1059, que ya admiten indefinido.
 * Medida PRE con sonda efímera `/tmp/probe1060/Probe.kt` (motor real
 * vía `tools/run_probe.sh`, HEAD c.1059 `8771f7e`): 6/6 candidatas
 * NULL (gap confirmado), 3/3 guards NULL correctos, 3/3 regresiones
 * HIT intactas, 3/3 FUERA NULL correctos; la envolvente «tengo que
 * cortarle una uña al perro» ya ruteaba TASK PRE (RED-pass medido
 * honestamente, patrón c.1055); la negación envolvente «tengo que no
 * cortarle…» da TASK afirmativa con la negación en el título —
 * comportamiento ESTABLE transversal medido sobre el piso
 * PREEXISTENTE definido c.1015 (sonda `/tmp/probe1060/Probe2.kt`),
 * NO regresión de este ciclo (lateral ABIERTA transversal c.1059).
 * Fix mínimo (lockstep TRES puntos, lección c.616/c.751; hermano
 * estructural directo de c.1059): la alternancia de artículo del
 * piso pasa de `(?:el|la|los|las)` a `(?:el|la|los|las|un|una|unos|unas)`
 * en (1) el piso [HOUSEHOLD_NAIL_DATIVE_FLOOR], (2) la cláusula de
 * negación dedicada de [imperativeIsNegated] (cinturón y tirantes
 * simétrico, precedente c.829/c.1011/c.1012/c.1015/c.1050/c.1059) y
 * (3) la plantilla de título de [extractTitle] («Cortarle una uña al
 * gato» — grafía del usuario preservada, doctrina c.653). CERO
 * keywords nuevas (lección c.751): la keyword-mascota preexistente
 * abre el gate y el piso ancla. Anti-overreach intacto (guards
 * medidos PRE→POST NULL): negación inmediata (lookbehind + cláusula),
 * pasado «le corté…» (no casa la alternancia), hedge «quizá…»,
 * destinatario humano indefinido «cortarle una uña al niño» (la
 * alternancia admite indefinido pero el destinatario sigue acotado
 * a mascota), forma sin ancla «cortarle una uña», sintagma nominal
 * «una uña del gato». Acotado deliberado (UNA por ciclo): el piso
 * humano «tomar pastillas» c.859 NO se toca (su indefinido «tomar
 * una pastilla» es lateral hermana ABIERTA — bivalente con keyword
 * humana, medición aparte); la negación envolvente transversal
 * «tengo que no <piso>» queda ABIERTA (doctrina y medición aparte).
 */
class ContextIntentEngineCortarleUnasIndefinidoDeltaTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Capturas indefinidas (piso extendido) ----

    @Test
    fun `captura indefinido singular cortarle una uña al gato`() {
        val i = analyze("cortarle una uña al gato")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Cortarle una uña al gato", i.title)
    }

    @Test
    fun `captura indefinido plural cortarle unas uñas al perro`() {
        val i = analyze("cortarle unas uñas al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Cortarle unas uñas al perro", i.title)
    }

    @Test
    fun `captura indefinido posesivo cortarle unas uñas a mi gato`() {
        val i = analyze("cortarle unas uñas a mi gato")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Cortarle unas uñas a mi gato", i.title)
    }

    @Test
    fun `captura indefinido genitivo cortar una uña del gato`() {
        val i = analyze("cortar una uña del gato")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Cortar una uña del gato", i.title)
    }

    @Test
    fun `captura indefinido con temporal manana`() {
        val i = analyze("cortarle una uña al perro mañana")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Cortarle una uña al perro", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura indefinido tras acuse de escucha`() {
        val i = analyze("vale, cortarle unas uñas al gato")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Cortarle unas uñas al gato", i.title)
    }

    @Test
    fun `envolvente tengo que con indefinido captura como tarea`() {
        // RED-pass medido honestamente (sonda PRE C7): la envolvente
        // «tengo que» ya ruteaba TASK con título limpio antes del fix
        // (patrón c.1055; en c.1059 la envolvente sí era NULL PRE).
        val i = analyze("tengo que cortarle una uña al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
    }

    // ---- Guards (NULL correctos) ----

    @Test
    fun `negacion inmediata indefinido bloqueada`() {
        assertNull(analyze("no cortarle una uña al gato"))
    }

    @Test
    fun `negacion envolvente captura como tarea comportamiento estable`() {
        // Comportamiento ESTABLE del motor (medido con sonda
        // `/tmp/probe1060/Probe2.kt` sobre el piso PREEXISTENTE
        // definido c.1015: «tengo que no cortarle las uñas al gato»
        // también da TASK con la negación preservada en el título;
        // paridad con pastilla c.1012 y pasear c.1018 medidos c.1059).
        // NO es regresión de c.1061: la envolvente «tengo que» atrapa
        // antes que el guard de negación. Lateral ABIERTA transversal
        // (afirmar una negación, potencial P1/P2 en TODOS los pisos —
        // medición y doctrina aparte, UNA por ciclo).
        val i = analyze("tengo que no cortarle una uña al gato")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
    }

    @Test
    fun `pasado indefinido no captura`() {
        assertNull(analyze("le corté una uña al gato"))
    }

    @Test
    fun `hedge indefinido no captura`() {
        assertNull(analyze("quizá cortarle unas uñas al gato"))
    }

    // ---- Regresiones (HIT intactas) ----

    @Test
    fun `regresion dativo definido c1015`() {
        val i = analyze("cortarle las uñas al gato")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Cortarle las uñas al gato", i.title)
    }

    @Test
    fun `regresion genitivo definido c1024`() {
        val i = analyze("cortar las uñas del gato")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Cortar las uñas del gato", i.title)
    }

    @Test
    fun `regresion pastilla indefinido c1059`() {
        val i = analyze("darle una pastilla al perro")
        assertNotNull(i)
        assertEquals(ContextIntentKind.HOUSEHOLD, i!!.kind)
        assertEquals("Darle una pastilla al perro", i.title)
    }

    // ---- FUERA estructurales (NULL deliberados) ----

    @Test
    fun `destinatario humano indefinido fuera`() {
        assertNull(analyze("cortarle una uña al niño"))
    }

    @Test
    fun `indefinido sin destinatario fuera`() {
        assertNull(analyze("cortarle una uña"))
    }

    @Test
    fun `sintagma nominal indefinido fuera`() {
        assertNull(analyze("una uña del gato"))
    }
}
