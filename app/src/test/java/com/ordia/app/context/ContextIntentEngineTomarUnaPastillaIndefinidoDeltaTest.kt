package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.1062: lateral ABIERTA (1) documentada en c.1059/c.1061 — la
 * forma con artículo INDEFINIDO del piso humano «tomar una
 * pastilla» / «tomarme una pastilla» / «tomar unas pastillas» /
 * «tomar un medicamento» (LA forma dicho-como-se-habla de la
 * medicación puntual humana: una pastilla para el dolor de cabeza,
 * un medicamento nuevo recetado; el definido «tomar la pastilla»
 * presupone una medicación conocida, el indefinido es la mención
 * espontánea). En captura PASIVA el enunciado llega como
 * notificación ([ContextCaptureSource.NOTIFICATION]) y es un
 * compromiso real de autocuidado (olvido silencioso P1). El piso
 * humano c.859 (`tomar|tomarme` + medicina/medicamento/pastilla/
 * medicación) sólo admitía artículo DEFINIDO o posesivo
 * `(?:el|la|los|las|mi|tu|su)`, a diferencia de sus hermanos
 * estructurales dativos de mascota (vacuna c.1011+c.1014, pastilla
 * c.1012+c.1050+c.1059, uñas c.1015+c.1061) que ya admiten
 * indefinido. Bivalencia medida y acotada: «pastilla»/«medicina»/
 * «medicamento»/«medicación» tras «tomar» es SIEMPRE medicación
 * humana (el dativo de mascota exige «darle/dale», verbo disjunto);
 * los objetos bivalentes reales («tomar una copa», «tomar una
 * decisión») no casan la alternancia de objeto. Medida PRE con
 * sonda efímera `/tmp/probe1062/Probe.kt` (motor real vía
 * `tools/run_probe.sh`, HEAD c.1061 `07d885c`): 6/6 candidatas
 * NULL (gap confirmado), 3/3 guards NULL correctos, 3/3
 * regresiones HIT intactas, 3/3 FUERA NULL correctos; la
 * envolvente «tengo que tomar una pastilla» ya ruteaba TASK 0.45
 * con título limpio PRE vía el candado genérico de recordatorio
 * (RED-pass medido honestamente, patrón c.1055); la negación
 * envolvente «tengo que no tomar la/una pastilla» da TASK
 * afirmativa con la negación en el título («No tomar la
 * pastilla») — comportamiento ESTABLE transversal medido sobre el
 * piso PREEXISTENTE definido c.859 (sonda
 * `/tmp/probe1062/Probe2.kt`), NO regresión de este ciclo
 * (lateral ABIERTA transversal c.1059). Fix mínimo (lockstep DOS
 * puntos, lección c.616/c.751 — este piso NO tiene cláusula
 * dedicada en [imperativeIsNegated]: la keyword 0.12 + bono
 * temporal 0.1 = 0.22 < umbral la hace innecesaria, aritmética
 * c.859/c.860 documentada en el propio piso): la alternancia de
 * artículo pasa de `(?:el\s+|la\s+|los\s+|las\s+|mi\s+|tu\s+|su\s+)?`
 * a `(?:el\s+|la\s+|los\s+|las\s+|mi\s+|tu\s+|su\s+|un\s+|una\s+|unos\s+|unas\s+)?`
 * en (1) el piso (`score`) y (2) la plantilla de título de
 * [extractTitle] («Tomar una pastilla» — grafía del usuario
 * preservada, doctrina c.653). CERO keywords nuevas (lección
 * c.751): la keyword-medicación preexistente abre el gate y el
 * piso ancla. Anti-overreach intacto (guards medidos PRE→POST
 * NULL): negación inmediata (lookbehind `(?<!no )`), pasado «me
 * tomé…» (no casa la alternancia), hedge «quizá tome…», sintagma
 * nominal «una pastilla para el dolor», objeto bivalente «tomar
 * una copa», interrogativa «cómo tomar una pastilla». Acotado
 * deliberado (UNA por ciclo): la negación envolvente transversal
 * «tengo que no <piso>» queda ABIERTA (doctrina y medición
 * aparte); las colas relativas en títulos quedan ABIERTAS.
 */
class ContextIntentEngineTomarUnaPastillaIndefinidoDeltaTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1_700_000_000_000L)
    )

    // ---- Capturas indefinidas (piso extendido) ----

    @Test
    fun `captura indefinido singular tomar una pastilla`() {
        val i = analyze("tomar una pastilla")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Tomar una pastilla", i.title)
    }

    @Test
    fun `captura indefinido enclitico tomarme una pastilla`() {
        val i = analyze("tomarme una pastilla")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Tomarme una pastilla", i.title)
    }

    @Test
    fun `captura indefinido plural tomar unas pastillas`() {
        val i = analyze("tomar unas pastillas")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Tomar unas pastillas", i.title)
    }

    @Test
    fun `captura indefinido masculino tomar un medicamento`() {
        val i = analyze("tomar un medicamento")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Tomar un medicamento", i.title)
    }

    @Test
    fun `captura indefinido con temporal a las 9`() {
        val i = analyze("tomar una pastilla a las 9")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Tomar una pastilla", i.title)
        assertNotNull(i.dueAt)
    }

    @Test
    fun `captura indefinido tras acuse de escucha`() {
        val i = analyze("vale, tomar una pastilla")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Tomar una pastilla", i.title)
    }

    @Test
    fun `envolvente tengo que con indefinido captura como tarea`() {
        // RED-pass medido honestamente (sonda PRE C7): la envolvente
        // «tengo que» ya ruteaba TASK 0.45 con título limpio antes
        // del fix vía el candado genérico de recordatorio (patrón
        // c.1055/c.1061).
        val i = analyze("tengo que tomar una pastilla")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
    }

    // ---- Guards (NULL correctos) ----

    @Test
    fun `guard negacion inmediata no tomar una pastilla`() {
        assertNull(analyze("no tomar una pastilla"))
    }

    @Test
    fun `guard pasado me tome una pastilla`() {
        assertNull(analyze("me tomé una pastilla"))
    }

    @Test
    fun `guard hedge quiza tome una pastilla`() {
        assertNull(analyze("quizá tome una pastilla"))
    }

    @Test
    fun `pin estable negacion envolvente captura como tarea afirmativa`() {
        // Comportamiento ESTABLE transversal medido PRE sobre el piso
        // definido c.859 (sonda `/tmp/probe1062/Probe2.kt`, paridad
        // con c.1012/c.1015/c.1018 medidos c.1059/c.1061): la
        // negación envolvente «tengo que no <piso>» captura como TASK
        // afirmativa con la negación en el título. Pin, NO regresión
        // de este ciclo; lateral ABIERTA transversal.
        val i = analyze("tengo que no tomar una pastilla")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
    }

    // ---- Regresiones (definido/posesivo intactos) ----

    @Test
    fun `regresion definido tomar la pastilla`() {
        val i = analyze("tomar la pastilla")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Tomar la pastilla", i.title)
    }

    @Test
    fun `regresion enclitico definido tomarme la medicina`() {
        val i = analyze("tomarme la medicina")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Tomarme la medicina", i.title)
    }

    @Test
    fun `regresion definido tomar la medicacion`() {
        val i = analyze("tomar la medicación")
        assertNotNull(i)
        assertEquals(ContextIntentKind.TASK, i!!.kind)
        assertEquals("Tomar la medicación", i.title)
    }

    // ---- FUERA estructurales (NULL correctos) ----

    @Test
    fun `fuera sintagma nominal una pastilla para el dolor`() {
        assertNull(analyze("una pastilla para el dolor"))
    }

    @Test
    fun `fuera objeto bivalente tomar una copa`() {
        assertNull(analyze("tomar una copa"))
    }

    @Test
    fun `fuera interrogativa como tomar una pastilla`() {
        assertNull(analyze("cómo tomar una pastilla"))
    }
}
