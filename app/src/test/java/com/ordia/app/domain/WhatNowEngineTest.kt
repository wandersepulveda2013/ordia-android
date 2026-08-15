package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.data.local.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class WhatNowEngineTest {
    private val zone = ZoneId.of("America/Santo_Domingo")
    private val date = LocalDate.of(2026, 7, 29)
    private val now = DateRules.toEpochMillis(date, LocalTime.of(10, 0), zone)

    private fun task(id: Long, title: String, priority: TaskPriority = TaskPriority.NORMAL) =
        TaskEntity(id = id, title = title, priority = priority)

    @Test
    fun suggestsTaskHappeningNowFirst() {
        val inProgress = task(1, "En curso").copy(
            startAt = DateRules.toEpochMillis(date, LocalTime.of(9, 30), zone),
            dueAt = DateRules.toEpochMillis(date, LocalTime.of(12, 0), zone),
            durationMinutes = 60
        )
        val urgent = task(2, "Urgente", TaskPriority.URGENT)

        val suggestion = WhatNowEngine.suggest(listOf(urgent, inProgress), now = now, zone = zone)

        assertEquals(1L, suggestion!!.task.id)
        assertEquals(WhatNowReason.IN_PROGRESS_NOW, suggestion.reason)
    }

    @Test
    fun explicitlyStartedTaskWinsWithoutScheduledStart() {
        val started = task(1, "Ya iniciada").copy(status = TaskStatus.IN_PROGRESS)
        val urgent = task(2, "Urgente", TaskPriority.URGENT)

        val suggestion = WhatNowEngine.suggest(listOf(urgent, started), now = now, zone = zone)

        assertEquals(1L, suggestion!!.task.id)
        assertEquals(WhatNowReason.IN_PROGRESS_NOW, suggestion.reason)
    }

    @Test
    fun prefersOverdueOverUrgentWithoutDate() {
        val overdue = task(1, "Atrasada").copy(dueAt = DateRules.toEpochMillis(date.minusDays(1), LocalTime.of(18, 0), zone))
        val urgent = task(2, "Urgente", TaskPriority.URGENT)

        val suggestion = WhatNowEngine.suggest(listOf(urgent, overdue), now = now, zone = zone)

        assertEquals(1L, suggestion!!.task.id)
        assertEquals(WhatNowReason.OVERDUE, suggestion.reason)
    }

    @Test
    fun prefersDueTodayOverUrgentWithoutDate() {
        val dueToday = task(1, "Vence hoy").copy(dueAt = DateRules.toEpochMillis(date, LocalTime.of(18, 0), zone))
        val urgent = task(2, "Urgente", TaskPriority.URGENT)

        val suggestion = WhatNowEngine.suggest(listOf(urgent, dueToday), now = now, zone = zone)

        assertEquals(1L, suggestion!!.task.id)
        assertEquals(WhatNowReason.DUE_TODAY, suggestion.reason)
    }

    @Test
    fun picksEarliestDueAmongSameReason() {
        val later = task(1, "Vence tarde").copy(dueAt = DateRules.toEpochMillis(date, LocalTime.of(18, 0), zone))
        val earlier = task(2, "Vence pronto").copy(dueAt = DateRules.toEpochMillis(date, LocalTime.of(11, 0), zone))

        val suggestion = WhatNowEngine.suggest(listOf(later, earlier), now = now, zone = zone)

        assertEquals(2L, suggestion!!.task.id)
        assertEquals(WhatNowReason.DUE_TODAY, suggestion.reason)
    }

    @Test
    fun urgentReasonWhenOnlyUrgentPending() {
        val urgent = task(1, "Urgente", TaskPriority.URGENT)

        val suggestion = WhatNowEngine.suggest(listOf(urgent), now = now, zone = zone)

        assertEquals(1L, suggestion!!.task.id)
        assertEquals(WhatNowReason.URGENT, suggestion.reason)
    }

    @Test
    fun inboxIsLastResort() {
        val inbox = task(1, "De la bandeja")

        val suggestion = WhatNowEngine.suggest(listOf(inbox), now = now, zone = zone)

        assertEquals(1L, suggestion!!.task.id)
        assertEquals(WhatNowReason.NEXT_INBOX, suggestion.reason)
    }

    @Test
    fun returnsNullWhenNothingPending() {
        val done = task(1, "Hecha").copy(completed = true, status = TaskStatus.COMPLETED)
        val archived = task(2, "Archivada").copy(archived = true)
        val cancelled = task(3, "Cancelada").copy(status = TaskStatus.CANCELLED)
        val subtask = task(4, "Sub").copy(parentTaskId = 1L)

        assertNull(WhatNowEngine.suggest(listOf(done, archived, cancelled, subtask), now = now, zone = zone))
    }

    @Test
    fun ignoresTaskScheduledLaterToday() {
        val later = task(1, "Más tarde").copy(
            startAt = DateRules.toEpochMillis(date, LocalTime.of(15, 0), zone),
            dueAt = DateRules.toEpochMillis(date, LocalTime.of(16, 0), zone)
        )
        val inbox = task(2, "De la bandeja")

        val suggestion = WhatNowEngine.suggest(listOf(later, inbox), now = now, zone = zone)

        assertEquals(2L, suggestion!!.task.id)
        assertEquals(WhatNowReason.NEXT_INBOX, suggestion.reason)
    }

    @Test
    fun detail_countsOverdueDays() {
        val overdue = task(1, "Atrasada").copy(dueAt = DateRules.toEpochMillis(date.minusDays(2), LocalTime.of(18, 0), zone))

        val suggestion = WhatNowEngine.suggest(listOf(overdue), now = now, zone = zone)

        assertEquals("Atrasada 2 días.", suggestion!!.detail)
    }

    @Test
    fun detail_showsDueTimeForDueToday() {
        val dueToday = task(1, "Vence hoy").copy(dueAt = DateRules.toEpochMillis(date, LocalTime.of(18, 0), zone))

        val suggestion = WhatNowEngine.suggest(listOf(dueToday), now = now, zone = zone)

        assertEquals("Vence hoy a las 18:00.", suggestion!!.detail)
    }

    @Test
    fun detail_explainsUrgentWithoutDate() {
        val urgent = task(1, "Urgente", TaskPriority.URGENT)

        val suggestion = WhatNowEngine.suggest(listOf(urgent), now = now, zone = zone)

        assertEquals("Urgente y sin fecha límite.", suggestion!!.detail)
    }
}
