package com.ordia.app.domain

import com.ordia.app.data.local.CommitmentEntity
import com.ordia.app.data.local.CommitmentKind
import com.ordia.app.data.local.CommitmentOwner
import com.ordia.app.data.local.CommitmentReviewStatus
import com.ordia.app.data.local.HabitEntity
import com.ordia.app.data.local.HabitLogEntity
import com.ordia.app.data.local.ProjectEntity
import com.ordia.app.data.local.ProjectStatus
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class GuardianCoachTest {
    private val zone = ZoneId.of("America/Santo_Domingo")
    private val today = LocalDate.of(2026, 7, 29)
    private val now = DateRules.toEpochMillis(today, LocalTime.NOON, zone)

    @Test
    fun overdueWorkWinsOverEverythingElse() {
        val overdue = TaskEntity(
            id = 1,
            title = "Enviar informe",
            dueAt = DateRules.toEpochMillis(today.minusDays(1), LocalTime.of(9, 0), zone),
            priority = TaskPriority.NORMAL
        )
        val urgentToday = TaskEntity(
            id = 2,
            title = "Llamar proveedor",
            dueAt = DateRules.toEpochMillis(today, LocalTime.of(15, 0), zone),
            priority = TaskPriority.URGENT
        )

        val insight = GuardianCoach.insight(listOf(urgentToday, overdue), emptyList(), emptyList(), now, zone)

        assertEquals(1L, insight.taskId)
        assertEquals(GuardianCoach.Tone.GENTLE, insight.tone)
    }

    @Test
    fun pendingHabitIsSuggestedWhenTasksAreClear() {
        val habit = HabitEntity(id = 7, title = "Leer diez minutos")
        val insight = GuardianCoach.insight(emptyList(), listOf(habit), emptyList(), now, zone)

        assertEquals("Leer diez minutos", insight.title)
        assertNotNull(insight.message)
    }

    @Test
    fun calmFallback_isHiddenFromHome() {
        val insight = GuardianCoach.insight(emptyList(), emptyList(), emptyList(), now, zone)

        assertEquals("TODO EN CALMA", insight.eyebrow)
        assertEquals(false, insight.showOnHome)
    }

    @Test
    fun dismissKey_isStableAcrossCalls() {
        val overdue = TaskEntity(
            id = 1,
            title = "Enviar informe",
            dueAt = DateRules.toEpochMillis(today.minusDays(1), LocalTime.of(9, 0), zone)
        )
        val first = GuardianCoach.insight(listOf(overdue), emptyList(), emptyList(), now, zone)
        val second = GuardianCoach.insight(listOf(overdue), emptyList(), emptyList(), now, zone)

        assertEquals(first.dismissKey, second.dismissKey)
        assertTrue(first.dismissKey.contains("Enviar informe"))
    }

    @Test
    fun inboxClutterAppearsWhenInboxGrows() {
        val inbox = (1L..8L).map { TaskEntity(id = it, title = "Captura $it") }

        val insight = GuardianCoach.insight(inbox, emptyList(), emptyList(), now, zone)

        assertEquals(GuardianCoach.Kind.INBOX_CLUTTER, insight.kind)
        assertTrue(insight.title.contains("8"))
        assertEquals(null, insight.taskId)
    }

    @Test
    fun overloadDetectedWhenManyTasksDueToday() {
        val tasks = (1L..7L).map {
            TaskEntity(
                id = it,
                title = "Hoy $it",
                status = com.ordia.app.data.local.TaskStatus.PLANNED,
                dueAt = DateRules.toEpochMillis(today, LocalTime.of(18, 0), zone)
            )
        }

        val insight = GuardianCoach.insight(tasks, emptyList(), emptyList(), now, zone)

        assertEquals(GuardianCoach.Kind.OVERLOAD, insight.kind)
        assertEquals(null, insight.taskId)
    }

    @Test
    fun upcomingCommitmentIsSuggested() {
        val commitment = CommitmentEntity(
            id = 1,
            conversationId = 10,
            kind = CommitmentKind.REQUEST,
            owner = CommitmentOwner.SELF,
            actor = "Ana",
            action = "Enviar el presupuesto",
            dueAt = now + 86_400_000L,
            confidence = 0.9f,
            fingerprint = "a".repeat(64),
            reviewStatus = CommitmentReviewStatus.PENDING
        )

        val insight = GuardianCoach.insight(
            emptyList(), emptyList(), emptyList(), now, zone,
            commitments = listOf(commitment)
        )

        assertEquals(GuardianCoach.Kind.UPCOMING_COMMITMENT, insight.kind)
        assertTrue(insight.title.contains("presupuesto"))
    }

    @Test
    fun staleProjectIsSuggestedWithoutGuilt() {
        val stale = ProjectEntity(
            id = 1,
            name = "Migración",
            status = ProjectStatus.ACTIVE,
            updatedAt = now - 30L * 86_400_000L
        )
        val task = TaskEntity(
            id = 2,
            title = "Revisar pendientes",
            projectId = 1,
            updatedAt = now - 30L * 86_400_000L
        )

        val insight = GuardianCoach.insight(
            listOf(task), emptyList(), emptyList(), now, zone,
            projects = listOf(stale)
        )

        assertEquals(GuardianCoach.Kind.STALE_PROJECT, insight.kind)
        assertEquals("Migración", insight.title)
    }

    @Test
    fun repeatedlyPostponedTaskIsDetected() {
        val due = now - 10L * 86_400_000L
        val task = TaskEntity(
            id = 1,
            title = "Llama al banco",
            dueAt = due,
            updatedAt = due + 5L * 86_400_000L
        )

        val insight = GuardianCoach.insight(listOf(task), emptyList(), emptyList(), now, zone)

        assertEquals(GuardianCoach.Kind.PROCRASTINATION, insight.kind)
        assertEquals(1L, insight.taskId)
    }

    @Test
    fun forgottenHabitIsDetectedAfterSeveralDays() {
        val habit = HabitEntity(id = 7, title = "Leer diez minutos")
        val log = HabitLogEntity(habitId = 7, epochDay = today.minusDays(5).toEpochDay(), completedAt = now)

        val insight = GuardianCoach.insight(emptyList(), listOf(habit), listOf(log), now, zone)

        assertEquals(GuardianCoach.Kind.FORGOTTEN_HABIT, insight.kind)
        assertEquals("Leer diez minutos", insight.title)
    }

    @Test
    fun inboxClutterIsIgnoredWhenOverdueExists() {
        val overdue = TaskEntity(
            id = 1,
            title = "Enviar informe",
            dueAt = DateRules.toEpochMillis(today.minusDays(1), LocalTime.of(9, 0), zone)
        )
        val inbox = (2L..9L).map { TaskEntity(id = it, title = "Captura $it") }

        val insight = GuardianCoach.insight(listOf(overdue) + inbox, emptyList(), emptyList(), now, zone)

        assertEquals(GuardianCoach.Kind.OVERDUE, insight.kind)
    }
}
