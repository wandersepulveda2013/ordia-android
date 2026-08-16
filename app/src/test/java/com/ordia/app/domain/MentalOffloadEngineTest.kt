package com.ordia.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MentalOffloadEngineTest {

    @Test
    fun emptyTextReturnsEmptyResult() {
        val result = MentalOffloadEngine.parse("")
        assertTrue(result.isEmpty)
        assertEquals(0, result.count)
    }

    @Test
    fun singleTaskProducesOneItem() {
        val result = MentalOffloadEngine.parse("tengo que llamar al banco")
        assertEquals(1, result.count)
        assertEquals(OffloadItemKind.TASK, result.items.first().kind)
        assertTrue(result.items.first().title.contains("banco", ignoreCase = true))
    }

    @Test
    fun multipleItemsSplitByConjunction() {
        val result = MentalOffloadEngine.parse(
            "mañana tengo que llamar al banco, comprar shampoo y preguntarle a Carlos por el documento antes del viernes"
        )
        assertTrue("expected at least 3 items, got ${result.count}", result.count >= 3)
        assertEquals("mañana", result.sharedContext)

        val purchase = result.items.firstOrNull { it.kind == OffloadItemKind.PURCHASE }
        assertNotNull("expected a purchase item", purchase)
        assertTrue(purchase!!.title.contains("shampoo", ignoreCase = true))

        val followup = result.items.firstOrNull { it.kind == OffloadItemKind.FOLLOWUP }
        assertNotNull("expected a followup item", followup)
        assertTrue(followup!!.title.contains("Carlos", ignoreCase = true))
    }

    @Test
    fun purchaseDetectedWithVerb() {
        val result = MentalOffloadEngine.parse("comprar arroz, leche y avena")
        val purchases = result.items.filter { it.kind == OffloadItemKind.PURCHASE }
        assertTrue("expected at least 1 purchase, got ${purchases.size}", purchases.isNotEmpty())
        assertTrue(purchases.any { it.title.contains("arroz", ignoreCase = true) })
        assertTrue(purchases.any { it.title.contains("leche", ignoreCase = true) })
        assertTrue(purchases.any { it.title.contains("avena", ignoreCase = true) })
    }

    @Test
    fun followupExtractsPersonAndSubject() {
        val result = MentalOffloadEngine.parse("preguntarle a Carlos por el documento")
        val followup = result.items.first { it.kind == OffloadItemKind.FOLLOWUP }
        assertTrue(followup.title.contains("Carlos", ignoreCase = true))
        assertTrue(followup.title.contains("documento", ignoreCase = true))
    }

    @Test
    fun sharedContextTomorrowSetsDueDate() {
        val result = MentalOffloadEngine.parse("mañana tengo que llamar al banco")
        assertEquals("mañana", result.sharedContext)
        assertNotNull("expected a due date for 'mañana'", result.items.first().dueAt)
    }

    @Test
    fun plainTextBecomesNoteWhenNoSignals() {
        val result = MentalOffloadEngine.parse("una idea sobre el proyecto")
        assertEquals(1, result.count)
        val item = result.items.first()
        assertTrue("expected note or task, got ${item.kind}", item.kind == OffloadItemKind.NOTE || item.kind == OffloadItemKind.TASK)
    }

    @Test
    fun duplicateTitlesDeduplicated() {
        val result = MentalOffloadEngine.parse("comprar leche y comprar leche")
        val titles = result.items.map { it.title.lowercase() }
        assertEquals("expected deduped items", titles.toSet().size, titles.size)
    }

    @Test
    fun beforeDeadlineParsesDate() {
        val result = MentalOffloadEngine.parse("preguntar a Ana por el informe antes del viernes")
        val followup = result.items.firstOrNull { it.kind == OffloadItemKind.FOLLOWUP }
        assertNotNull(followup)
        assertTrue(followup!!.title.contains("Ana", ignoreCase = true))
    }
}
