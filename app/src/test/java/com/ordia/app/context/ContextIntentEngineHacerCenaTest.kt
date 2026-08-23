package com.ordia.app.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * c.898 — familia NOVENA (5/8) «verbos comida/deberes» del hermano c.728
 * («hacer la cama» HOUSEHOLD acotado por objeto): el piso libre no cubría
 * «hacer/preparar/descongelar» y la bivalente «hacer» no anclaba tampoco
 * en «hacer los deberes». PRE medido por la sonda persistida
 * `tools/probe/HacerCenaProbe.kt` (4/4 candidatas NULL; 4/4 guards
 * bivalentes NULL) — olvido P1 medido antes del fix.
 *
 * Extensión ADITIVA del lockstep hermano (sin reescritura): (1) pisos
 * acotados nuevos [HOUSEHOLD_MEAL_FLOOR] (hacer/preparar + objeto-comida),
 * [HOUSEHOLD_DEFROST_FLOOR] (descongelar inequívoco) y
 * [STUDY_HOMEWORK_FLOOR] (hacer + deberes; «entregar» recortado — tiene
 * piso libre en TASK y su adición fluctua la suite); (2) keywords-OBJETO
 * comida en HOUSEHOLD («cena/comida/almuerzo/desayuno/merienda/
 * descongelar») y «deberes» en STUDY (alimentan TRIGGER_WORDS, lección
 * c.751); (3) plantillas de título dedicadas en [extractTitle] (lockstep
 * piso↔título, lección c.616). Guard de bivalentes intacta: «hacer la
 * lista/el plan» y negaciones «no hacer…» siguen NULL.
 */
class ContextIntentEngineHacerCenaTest {

    private fun analyze(
        text: String,
        now: Long = System.currentTimeMillis()
    ): ContextIntent? = ContextIntentEngine.analyze(
        ContextEvent(ContextCaptureSource.NOTIFICATION, text, now)
    )

    // ─── Capturas (4/4 candidatas; flavor ganador: kind+title) ────

    @Test
    fun `hacer la cena captura HOUSEHOLD con titulo limpio`() {
        val intent = analyze("hacer la cena esta noche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Hacer la cena", intent.title)
    }

    @Test
    fun `preparar el almuerzo captura HOUSEHOLD`() {
        val intent = analyze("preparar el almuerzo mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Preparar el almuerzo", intent.title)
    }

    @Test
    fun `descongelar la carne captura HOUSEHOLD`() {
        val intent = analyze("descongelar la carne para la cena")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals("Descongelar la carne para la cena", intent.title)
    }

    @Test
    fun `hacer los deberes captura STUDY`() {
        val intent = analyze("hacer los deberes mañana")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.STUDY, intent!!.kind)
        assertEquals("Hacer los deberes", intent.title)
    }

    @Test
    fun `candidatas rutean con fecha`() {
        val intent = analyze("hacer la cena esta noche")
        assertNotNull(intent)
        assertEquals(ContextIntentKind.HOUSEHOLD, intent!!.kind)
        assertEquals(true, intent.dueAt != null)
    }

    // ─── Lockstep keywords (TRIGGER_WORDS debe contener el objeto) ───

    @Test
    fun `keywords objeto comida activan HOUSEHOLD kind`() {
        assertKeywordContains(ContextIntentKind.HOUSEHOLD, "cena")
        assertKeywordContains(ContextIntentKind.HOUSEHOLD, "comida")
        assertKeywordContains(ContextIntentKind.HOUSEHOLD, "almuerzo")
        assertKeywordContains(ContextIntentKind.HOUSEHOLD, "desayuno")
        assertKeywordContains(ContextIntentKind.HOUSEHOLD, "merienda")
        assertKeywordContains(ContextIntentKind.HOUSEHOLD, "descongelar")
    }

    @Test
    fun `keyword deberes activa STUDY kind`() {
        org.junit.Assert.assertTrue(
            "STUDY debe contener la keyword-OBJETO «deberes»",
            ContextIntentKind.STUDY.keywords.contains("deberes")
        )
    }

    private fun assertKeywordContains(kind: ContextIntentKind, word: String) {
        org.junit.Assert.assertTrue(
            "$kind debe contener la keyword-OBJETO «$word»",
            kind.keywords.contains(word)
        )
    }

    // ─── Guards anti-overreach (objetivo: NULL) ────────────────────

    @Test
    fun `hacer bivalente sin ancla excluido`() {
        assertNull(analyze("hacer la lista esta noche"))
        assertNull(analyze("hacer el plan mañana"))
    }

    @Test
    fun `negacion excluida en comida`() {
        assertNull(analyze("no hacer la cena esta noche"))
        assertNull(analyze("no preparar el almuerzo mañana"))
        assertNull(analyze("no descongelar la carne"))
    }

    @Test
    fun `negacion excluida en deberes`() {
        assertNull(analyze("no hacer los deberes mañana"))
        assertNull(analyze("no entregar los deberes mañana"))
    }
}
