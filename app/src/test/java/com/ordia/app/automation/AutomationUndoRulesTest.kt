package com.ordia.app.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationUndoRulesTest {

    @Test
    fun mixedUpdateAndCreate_onlyCreatedTaskIsDeleted() {
        val result = AutomationUndoRules.createdTaskIds(
            affectedTaskIds = listOf(10L, 20L, 30L),
            snapshotTaskIds = setOf(10L, 20L)
        )

        assertEquals(setOf(30L), result)
    }

    @Test
    fun dayPlan_neverTreatsUpdatedInboxTaskAsCreated() {
        val result = AutomationUndoRules.createdTaskIds(
            affectedTaskIds = listOf(7L),
            snapshotTaskIds = setOf(7L)
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun routine_withoutSnapshots_treatsEveryAffectedTaskAsCreated() {
        val result = AutomationUndoRules.createdTaskIds(
            affectedTaskIds = listOf(1L, 2L),
            snapshotTaskIds = emptySet()
        )

        assertEquals(setOf(1L, 2L), result)
    }
}
