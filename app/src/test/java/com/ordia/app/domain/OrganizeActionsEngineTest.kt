package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrganizeActionsEngineTest {

    private val now = System.currentTimeMillis()
    private val pastDue = now - 86_400_000L

    @Test
    fun emptyTasksReturnsEmptyProposal() {
        val proposal = OrganizeActionsEngine.proposeWeek(emptyList())
        assertTrue(proposal.isEmpty)
    }

    @Test
    fun overdueTaskProposesReschedule() {
        val task = TaskEntity(
            id = 1, title = "Vencida", dueAt = pastDue, status = TaskStatus.PLANNED
        )
        val proposal = OrganizeActionsEngine.proposeWeek(listOf(task), now)
        val resched = proposal.changes.filterIsInstance<OrganizeChange.RescheduleOverdue>()
        assertEquals(1, resched.size)
        assertEquals(1L, resched.first().taskId)
        assertTrue(resched.first().newDueAt > now)
    }

    @Test
    fun inboxTaskProposesPromotion() {
        val task = TaskEntity(
            id = 2, title = "En bandeja", status = TaskStatus.INBOX, dueAt = now + 86_400_000L
        )
        val proposal = OrganizeActionsEngine.proposeWeek(listOf(task), now)
        val promote = proposal.changes.filterIsInstance<OrganizeChange.PromoteInbox>()
        assertEquals(1, promote.size)
    }

    @Test
    fun noDateTaskProposesAssignDue() {
        val task = TaskEntity(
            id = 3, title = "Sin fecha", status = TaskStatus.PLANNED, dueAt = null
        )
        val proposal = OrganizeActionsEngine.proposeWeek(listOf(task), now)
        val assign = proposal.changes.filterIsInstance<OrganizeChange.AssignDue>()
        assertEquals(1, assign.size)
    }

    @Test
    fun duplicateTasksFlagged() {
        val a = TaskEntity(id = 10, title = "Comprar leche", status = TaskStatus.PLANNED)
        val b = TaskEntity(id = 11, title = "comprar leche", status = TaskStatus.PLANNED)
        val proposal = OrganizeActionsEngine.proposeWeek(listOf(a, b), now)
        val dups = proposal.changes.filterIsInstance<OrganizeChange.FlagDuplicate>()
        assertTrue("duplicates should be detected", dups.isNotEmpty())
        assertEquals(1, dups.size)
        // One flags the other as duplicate, never self
        assertNotEquals(dups.first().taskId, dups.first().duplicateOfId)
    }

    @Test
    fun proposalSummaryIsHumanReadable() {
        val task = TaskEntity(
            id = 1, title = "Entregar informe", dueAt = pastDue, status = TaskStatus.PLANNED
        )
        val proposal = OrganizeActionsEngine.proposeWeek(listOf(task), now)
        assertTrue(proposal.changes.first().summary.contains("Entregar informe"))
    }

    @Test
    fun maxChangesCapped() {
        val tasks = (1..40).map {
            TaskEntity(id = it.toLong(), title = "Tarea $it", status = TaskStatus.PLANNED, dueAt = null)
        }
        val proposal = OrganizeActionsEngine.proposeWeek(tasks, now)
        assertTrue(proposal.count <= 20)
    }
}
