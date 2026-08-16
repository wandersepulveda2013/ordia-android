package com.ordia.app.domain

import com.ordia.app.data.local.CommitmentEntity
import com.ordia.app.data.local.CommitmentKind
import com.ordia.app.data.local.CommitmentOwner
import com.ordia.app.data.local.CommitmentReviewStatus
import com.ordia.app.data.local.HabitEntity
import com.ordia.app.data.local.HabitFrequency
import com.ordia.app.data.local.HabitLogEntity
import com.ordia.app.data.local.NoteEntity
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class GuardianReportEngineTest {

    private val now = System.currentTimeMillis()
    private val weekAgo = now - 8L * 86_400_000L

    @Test
    fun emptyDataReturnsAllClear() {
        val report = GuardianReportEngine.report(
            tasks = emptyList(),
            notes = emptyList(),
            habits = emptyList(),
            habitLogs = emptyList(),
            projects = emptyList(),
            commitments = emptyList()
        )
        assertNull(report.pending)
        assertNull(report.chaos)
        assertNull(report.commitments)
        assertNull(report.routine)
        assertTrue(report.cards.isEmpty())
    }

    @Test
    fun staleTasksTriggerPendingGuardian() {
        val staleTask = TaskEntity(
            title = "Vieja",
            status = TaskStatus.PLANNED,
            updatedAt = weekAgo
        )
        val report = GuardianReportEngine.report(
            tasks = listOf(staleTask),
            notes = emptyList(),
            habits = emptyList(),
            habitLogs = emptyList(),
            projects = emptyList(),
            commitments = emptyList()
        )
        assertNotNull(report.pending)
        assertTrue(report.pending!!.message.contains("semana"))
        assertEquals(1, report.pending!!.count)
    }

    @Test
    fun freshTasksDoNotTriggerPendingGuardian() {
        val freshTask = TaskEntity(
            title = "Nueva",
            status = TaskStatus.PLANNED,
            updatedAt = now
        )
        val report = GuardianReportEngine.report(
            tasks = listOf(freshTask),
            notes = emptyList(),
            habits = emptyList(),
            habitLogs = emptyList(),
            projects = emptyList(),
            commitments = emptyList()
        )
        assertNull(report.pending)
    }

    @Test
    fun unorganizedNotesTriggerChaosGuardian() {
        val notes = (1..10).map { NoteEntity(title = "Nota $it") }
        val report = GuardianReportEngine.report(
            tasks = emptyList(),
            notes = notes,
            habits = emptyList(),
            habitLogs = emptyList(),
            projects = emptyList(),
            commitments = emptyList()
        )
        assertNotNull(report.chaos)
        assertTrue(report.chaos!!.count >= 10)
    }

    @Test
    fun pendingCommitmentsTriggerCommitmentsGuardian() {
        val commitment = CommitmentEntity(
            conversationId = 1,
            kind = CommitmentKind.SELF_COMMITMENT,
            owner = CommitmentOwner.SELF,
            actor = "Ana",
            action = "llamar",
            dueAt = now + 2L * 86_400_000L,
            confidence = 0.9f,
            reviewStatus = CommitmentReviewStatus.PENDING,
            fingerprint = "fp1"
        )
        val report = GuardianReportEngine.report(
            tasks = emptyList(),
            notes = emptyList(),
            habits = emptyList(),
            habitLogs = emptyList(),
            projects = emptyList(),
            commitments = listOf(commitment)
        )
        assertNotNull(report.commitments)
        assertTrue(report.commitments!!.title.contains("Ana"))
    }

    @Test
    fun fulfilledCommitmentsDoNotTriggerGuardian() {
        val commitment = CommitmentEntity(
            conversationId = 1,
            kind = CommitmentKind.SELF_COMMITMENT,
            owner = CommitmentOwner.SELF,
            actor = "Ana",
            action = "llamar",
            dueAt = now + 2L * 86_400_000L,
            confidence = 0.9f,
            reviewStatus = CommitmentReviewStatus.PENDING,
            resultTaskId = 99L,
            fingerprint = "fp1"
        )
        val report = GuardianReportEngine.report(
            tasks = emptyList(),
            notes = emptyList(),
            habits = emptyList(),
            habitLogs = emptyList(),
            projects = emptyList(),
            commitments = listOf(commitment)
        )
        assertNull(report.commitments)
    }

    @Test
    fun skippedHabitTriggersRoutineGuardian() {
        val habit = HabitEntity(id = 1, title = "Meditación", frequency = HabitFrequency.DAILY)
        // No logs for habit 1 → all 7 days skipped
        val report = GuardianReportEngine.report(
            tasks = emptyList(),
            notes = emptyList(),
            habits = listOf(habit),
            habitLogs = emptyList(),
            projects = emptyList(),
            commitments = emptyList()
        )
        assertNotNull("expected routine guardian for skipped habit", report.routine)
        assertEquals(1, report.routine!!.count)
    }

    @Test
    fun loggedHabitDoesNotTriggerRoutineGuardian() {
        val habit = HabitEntity(id = 1, title = "Meditación", frequency = HabitFrequency.DAILY)
        val today = LocalDate.now()
        val logs = (0 until 7).map {
            HabitLogEntity(habitId = 1, epochDay = today.minusDays(it.toLong()).toEpochDay())
        }
        val report = GuardianReportEngine.report(
            tasks = emptyList(),
            notes = emptyList(),
            habits = listOf(habit),
            habitLogs = logs,
            projects = emptyList(),
            commitments = emptyList()
        )
        assertNull(report.routine)
    }

    @Test
    fun allGuardiansCanCoexist() {
        val staleTask = TaskEntity(title = "Vieja", status = TaskStatus.PLANNED, updatedAt = weekAgo)
        val notes = (1..10).map { NoteEntity(title = "Nota $it") }
        val commitment = CommitmentEntity(
            conversationId = 1, kind = CommitmentKind.SELF_COMMITMENT, owner = CommitmentOwner.SELF,
            actor = "Ana", action = "llamar", dueAt = now + 86_400_000L,
            confidence = 0.9f, reviewStatus = CommitmentReviewStatus.PENDING, fingerprint = "fp1"
        )
        val habit = HabitEntity(id = 1, title = "Meditación", frequency = HabitFrequency.DAILY)

        val report = GuardianReportEngine.report(
            tasks = listOf(staleTask),
            notes = notes,
            habits = listOf(habit),
            habitLogs = emptyList(),
            projects = emptyList(),
            commitments = listOf(commitment)
        )
        assertEquals(4, report.cards.size)
        assertNotNull(report.pending)
        assertNotNull(report.chaos)
        assertNotNull(report.commitments)
        assertNotNull(report.routine)
    }
}
