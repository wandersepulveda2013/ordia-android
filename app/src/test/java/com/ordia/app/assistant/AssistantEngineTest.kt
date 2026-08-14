package com.ordia.app.assistant

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantEngineTest {
    @Test fun blankRequest_isHelpfulAndOffline() {
        val answer = AssistantEngine.answer("", emptyList(), emptyList(), emptyList())
        assertEquals(AssistantAction.NONE, answer.action)
        assertTrue(answer.text.isNotBlank())
    }

    @Test fun whatNow_usesRealPriority() {
        val answer = AssistantEngine.answer(
            "¿Qué hago ahora?",
            listOf(TaskEntity(id = 1, title = "Normal"), TaskEntity(id = 2, title = "Urgente", priority = TaskPriority.URGENT)),
            emptyList(), emptyList()
        )
        assertEquals(listOf(2L), answer.relatedTaskIds)
    }

    @Test fun whatNow_explainsWhyAndMentionsOverdue() {
        val overdue = TaskEntity(id = 1, title = "Atrasada", dueAt = 1L)
        val answer = AssistantEngine.answer(
            "¿Qué hago ahora?",
            listOf(overdue, TaskEntity(id = 2, title = "Normal")),
            emptyList(), emptyList()
        )
        assertEquals(listOf(1L), answer.relatedTaskIds)
        assertTrue("explica la razón: ${answer.text}", answer.text.contains("vencida"))
        assertTrue("menciona 1 vencida: ${answer.text}", answer.text.contains("1 vencida"))
    }

    @Test fun planMinimo_ranksOverdueFirst() {
        val normal = TaskEntity(id = 1, title = "Normal", priority = TaskPriority.HIGH)
        val overdue = TaskEntity(id = 2, title = "Atrasada", dueAt = 1L)
        val answer = AssistantEngine.answer(
            "plan mínimo para hoy",
            listOf(normal, overdue),
            emptyList(), emptyList()
        )
        assertEquals(listOf(2L, 1L), answer.relatedTaskIds)
    }

    @Test fun createNote_requiresContentAndThenOffersAction() {
        assertEquals(AssistantAction.NONE, AssistantEngine.answer("Guardar como nota", emptyList(), emptyList(), emptyList()).action)
        assertEquals(AssistantAction.CREATE_NOTE, AssistantEngine.answer("Guardar como nota: idea privada", emptyList(), emptyList(), emptyList()).action)
    }
}
