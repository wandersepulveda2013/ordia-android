package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * c.1044: lateral «ponerme la vacuna» (humana, reflexiva) — registrada
 * ABIERTA en c.1011 cuando el piso dativo de mascota se acotó al objeto
 * `perro/gato` («el destinatario humano… queda FUERA… la forma humana
 * «ponerme la vacuna» es otra candidata»). Medida PRE con sonda efímera
 * `/tmp/probe-laterals.kt` sobre HEAD `04878cd` (c.1043): las 4 formas
 * NULL (gap confirmado — vacunarse es un compromiso de salud cotidiano de
 * coste real: campaña de gripe, refuerzos, viajes; olvido silencioso P1);
 * 7/7 guards NULL correctos; 5/5 regresiones de mascota HIT intactas.
 *
 * Fix lockstep TRES puntos (lección c.616/c.751; hermano estructural
 * «ponerse la insulina» c.766 — salud/autocuidado QUINTA clase — y dativo
 * mascota «ponerle la vacuna» c.1011): (1) piso TASK con enclítico
 * reflexivo `poner(me|te|se|nos)` + objeto `vacunas?` (determinante
 * definido/indefinido/posesivo opcional, hermano c.1014 «un|una»); (2)
 * keyword-OBJETO «vacuna» en [ContextIntentKind.TASK] — SIN ella la
 * frase ni llegaba al análisis (lección c.751; 0.12 sola < umbral y
 * 0.34 con «perro»+temporal: «la vacuna del perro» sigue NULL; subcadenas
 * «vacunar»/«vacunación» la contienen pero son inertes bajo el umbral);
 * (3) plantilla de título (verbo con pronombre preservado, doctrina
 * c.653; el residuo temporal lo depura [sanitizeTitle]). Negación sin
 * cláusula dedicada (aritmética c.766: 0.12+0.1=0.22 < 0.45) + `(?<!no )`
 * en el piso. Kind TASK (salud humana, hermana «tomar la medicación»
 * c.765 y «ponerse la insulina» c.766; deliberación contra HOUSEHOLD —
 * el cuidado propio no es quehacer doméstico; el dativo mascota sigue
 * HOUSEHOLD c.1011).
 */
class ContextIntentEnginePonermeVacunaFloorTest {

    private fun analyze(text: String) = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, 1000)
    )

    // --- CAPTURAS (RED medida: NULL sobre HEAD 04878cd) ---

    @Test
    fun `ponerme la vacuna manana captura TASK con titulo limpio`() {
        val intent = analyze("ponerme la vacuna mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertEquals("Ponerme la vacuna", intent.title)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `ponerme la vacuna de la gripe el lunes captura`() {
        val intent = analyze("ponerme la vacuna de la gripe el lunes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `ponerme una vacuna el martes captura`() {
        val intent = analyze("ponerme una vacuna el martes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `ponerte la vacuna manana captura`() {
        val intent = analyze("ponerte la vacuna mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `ponerse la vacuna el lunes captura`() {
        val intent = analyze("ponerse la vacuna el lunes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
        assertNotNull(intent.dueAt)
    }

    @Test
    fun `ponerme las vacunas manana captura plural`() {
        val intent = analyze("ponerme las vacunas mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `vale ponerme la vacuna manana captura tras acuse`() {
        val intent = analyze("vale, ponerme la vacuna mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    // --- GUARDS (NULL correcto esperado; verdes desde RED) ---

    @Test
    fun `negacion no ponerme la vacuna sigue NULL`() {
        assertNull(analyze("no ponerme la vacuna mañana"))
    }

    @Test
    fun `pasado me puse la vacuna sigue NULL`() {
        assertNull(analyze("me puse la vacuna ayer"))
    }

    @Test
    fun `duda quizá ponerme la vacuna sigue NULL`() {
        assertNull(analyze("quizá ponerme la vacuna"))
    }

    @Test
    fun `sustantivo la vacuna de la gripe sigue NULL`() {
        assertNull(analyze("la vacuna de la gripe está disponible"))
    }

    @Test
    fun `dativo humano ponerle la vacuna al nino sigue NULL`() {
        assertNull(analyze("ponerle la vacuna al niño mañana"))
    }

    @Test
    fun `sintagma nominal la vacuna del perro sigue NULL`() {
        assertNull(analyze("la vacuna del perro"))
    }

    // --- REGRESIONES (HIT intacto esperado; verdes desde RED) ---

    @Test
    fun `regresion dativo mascota ponerle la vacuna al perro sigue HOUSEHOLD`() {
        val intent = analyze("ponerle la vacuna al perro el mes que viene")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
    }

    @Test
    fun `regresion transitiva vacunar al gato sigue HOUSEHOLD`() {
        val intent = analyze("vacunar al gato el lunes")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
    }

    @Test
    fun `regresion insulina ponerse la insulina sigue TASK`() {
        val intent = analyze("ponerse la insulina mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.TASK, intent!!.kind)
    }

    @Test
    fun `titulo preserva el pronombre reflexivo`() {
        val intent = analyze("ponerme la vacuna mañana")
        assertNotNull(intent)
        assertTrue(intent!!.title.startsWith("Ponerme"))
    }
}
