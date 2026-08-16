package com.ordia.app.domain

import com.ordia.app.data.local.CaptureTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextSuggestionEngineTest {

    @Test
    fun blankReturnsEmpty() {
        assertTrue(ContextSuggestionEngine.suggest("").isEmpty())
    }

    @Test
    fun timeAndPlaceSuggestsEvent() {
        val s = ContextSuggestionEngine.suggest("jueves 3:00 Ágora Mall")
        assertTrue(s.any { it.target == CaptureTarget.EVENT && it.label == "Crear evento" })
    }

    @Test
    fun dueDateSuggestsReminder() {
        val s = ContextSuggestionEngine.suggest("pagar electricidad antes del 23")
        assertTrue(s.any { it.target == CaptureTarget.REMINDER })
    }

    @Test
    fun shoppingListSuggestsList() {
        val s = ContextSuggestionEngine.suggest("arroz, leche, avena")
        assertTrue(s.any { it.label == "Crear lista" })
    }

    @Test
    fun plainTextReturnsEmpty() {
        assertTrue(ContextSuggestionEngine.suggest("una idea aleatoria sin señales").isEmpty())
    }

    @Test
    fun multipleSuggestionsCombined() {
        val s = ContextSuggestionEngine.suggest("reunión jueves 3:00 antes del 28")
        assertEquals(2, s.size)
    }
}
