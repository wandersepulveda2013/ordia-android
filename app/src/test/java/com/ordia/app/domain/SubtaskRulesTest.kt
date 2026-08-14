package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtaskRulesTest {
    private fun task(
        id: Long,
        parentId: Long? = null,
        completed: Boolean = false,
        status: TaskStatus = TaskStatus.INBOX
    ) = TaskEntity(id = id, title = "T$id", parentTaskId = parentId, completed = completed, status = status)

    private fun cancelled(id: Long, parentId: Long? = null) =
        task(id, parentId, completed = false, status = TaskStatus.CANCELLED)

    @Test
    fun progressCountsCompletedOverTotal() {
        val subs = listOf(task(2, 1, completed = true), task(3, 1), task(4, 1, completed = true))

        assertEquals(2 to 3, SubtaskRules.progress(subs))
    }

    @Test
    fun allCompletedRequiresNonEmptyAndAllDone() {
        assertFalse(SubtaskRules.allCompleted(emptyList()))
        assertFalse(SubtaskRules.allCompleted(listOf(task(2, 1, completed = true), task(3, 1))))
        assertTrue(SubtaskRules.allCompleted(listOf(task(2, 1, completed = true), task(3, 1, completed = true))))
    }

    @Test
    fun shouldAutoCompleteParentWhenLastSubtaskClosed() {
        val parent = task(1)
        assertFalse(SubtaskRules.shouldAutoCompleteParent(parent, listOf(task(2, 1), task(3, 1))))
        assertTrue(SubtaskRules.shouldAutoCompleteParent(parent, listOf(task(2, 1, completed = true), task(3, 1, completed = true))))
        // padre ya completo: no hace falta autocompletarlo
        assertFalse(SubtaskRules.shouldAutoCompleteParent(task(1, completed = true), listOf(task(2, 1, completed = true))))
    }

    @Test
    fun shouldAutoReopenParentWhenSubtaskReopened() {
        val parent = task(1, completed = true)
        assertFalse(SubtaskRules.shouldAutoReopenParent(parent, listOf(task(2, 1, completed = true))))
        assertTrue(SubtaskRules.shouldAutoReopenParent(parent, listOf(task(2, 1, completed = true), task(3, 1))))
        // padre pendiente: no hay que reabrirlo
        assertFalse(SubtaskRules.shouldAutoReopenParent(task(1), listOf(task(3, 1))))
    }

    // --- CANCELLED se trata como "resuelto" (descartado), no como pendiente ---
    // Continúa el hilo de consistencia de CANCELLED (c.169-c.173): una subtarea
    // cancelada (status=CANCELLED, completed=false) ya no cuenta como trabajo
    // pendiente del padre. Sin esto, una subtarea cancelada (alcanzable hoy vía
    // restore de respaldo) BLOQUEA el autocompletado del padre y FUERZA su
    // reapertura, contradiciendo que el usuario la descartó.

    @Test
    fun cancelledSubtaskCountsAsResolvedForAutoComplete() {
        val parent = task(1)
        // Todas las subtareas canceladas → todas resueltas → el padre se autocompleta
        assertTrue(SubtaskRules.shouldAutoCompleteParent(parent, listOf(cancelled(2, 1), cancelled(3, 1))))
        // Mezcla: una completada + una cancelada → todas resueltas → autocompleta
        assertTrue(SubtaskRules.shouldAutoCompleteParent(parent, listOf(task(2, 1, completed = true), cancelled(3, 1))))
        // Una pendiente real (ni completada ni cancelada) → NO autocompleta
        assertFalse(SubtaskRules.shouldAutoCompleteParent(parent, listOf(task(2, 1, completed = true), task(3, 1))))
    }

    @Test
    fun cancelledSubtaskDoesNotForceParentReopen() {
        val parent = task(1, completed = true)
        // Solo hay una cancelada (resuelta) → nada pendiente → no reabrir
        assertFalse(SubtaskRules.shouldAutoReopenParent(parent, listOf(cancelled(2, 1))))
        // Completada + cancelada → ninguna pendiente → no reabrir
        assertFalse(SubtaskRules.shouldAutoReopenParent(parent, listOf(task(2, 1, completed = true), cancelled(3, 1))))
        // Una subtarea realmente pendiente SÍ reabre
        assertTrue(SubtaskRules.shouldAutoReopenParent(parent, listOf(task(2, 1, completed = true), task(3, 1))))
    }

    @Test
    fun allCompletedTreatsCancelledAsResolved() {
        assertTrue(SubtaskRules.allCompleted(listOf(cancelled(2), cancelled(3))))
        assertTrue(SubtaskRules.allCompleted(listOf(task(2, completed = true), cancelled(3))))
        assertFalse(SubtaskRules.allCompleted(listOf(task(2, completed = true), task(3))))
    }

    @Test
    fun depthWalksAncestors() {
        val byId = mapOf(
            1L to task(1),
            2L to task(2, 1),
            3L to task(3, 2),
            4L to task(4, 3)
        )
        assertEquals(0, SubtaskRules.depth(byId.getValue(1L), byId))
        assertEquals(1, SubtaskRules.depth(byId.getValue(2L), byId))
        assertEquals(2, SubtaskRules.depth(byId.getValue(3L), byId))
        assertEquals(3, SubtaskRules.depth(byId.getValue(4L), byId))
    }

    @Test
    fun depthToleratesCycles() {
        // A → B → A (ciclo); no debe colgarse
        val byId = mapOf(
            1L to task(1, 2L),
            2L to task(2, 1L)
        )
        val d = SubtaskRules.depth(byId.getValue(1L), byId)
        assertTrue(d >= 1)
    }

    @Test
    fun depthStopsAtMissingParent() {
        val byId = mapOf(1L to task(1), 2L to task(2, 999L))
        assertEquals(0, SubtaskRules.depth(byId.getValue(2L), byId))
    }

    @Test
    fun canAddSubtaskRespectsMaxDepth() {
        val byId = mapOf(
            1L to task(1),
            2L to task(2, 1),
            3L to task(3, 2),
            4L to task(4, 3)
        )
        assertTrue(SubtaskRules.canAddSubtask(byId.getValue(3L), byId))
        assertFalse(SubtaskRules.canAddSubtask(byId.getValue(4L), byId))
    }
}
