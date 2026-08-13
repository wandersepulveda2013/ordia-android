package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class TaskRulesTest {
    private val zone = ZoneId.of("America/Santo_Domingo")
    private val date = LocalDate.of(2026, 7, 29)

    @Test
    fun dueToday_matchesCalendarDay() {
        val today = LocalDate.of(2026, 7, 29)
        val now = today.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val due = today.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
        assertTrue(TaskRules.isDueToday(TaskEntity(title = "Tarea", dueAt = due), now, zone))
    }

    @Test
    fun completedTask_isNotOverdue() {
        val task = TaskEntity(title = "Lista", dueAt = 1, completed = true)
        assertFalse(TaskRules.isOverdue(task, 10))
    }

    @Test
    fun nextBestTask_prefersHighPriority() {
        val normal = TaskEntity(id = 1, title = "Normal", priority = TaskPriority.NORMAL)
        val high = TaskEntity(id = 2, title = "Alta", priority = TaskPriority.HIGH)
        assertEquals(high, TaskRules.nextBestTask(listOf(normal, high), 100))
    }

    @Test
    fun nextBestTask_prefersTaskHappeningNowOverUrgent() {
        val now = DateRules.toEpochMillis(date, LocalTime.of(10, 0), zone)
        val inProgress = TaskEntity(
            id = 1, title = "En curso",
            startAt = DateRules.toEpochMillis(date, LocalTime.of(9, 30), zone),
            durationMinutes = 60
        )
        val urgent = TaskEntity(id = 2, title = "Urgente", priority = TaskPriority.URGENT)
        assertEquals(inProgress, TaskRules.nextBestTask(listOf(urgent, inProgress), now, zone))
    }

    @Test
    fun nextBestTask_prefersDueTodayOverUrgentDueTomorrow() {
        val now = DateRules.toEpochMillis(date, LocalTime.of(10, 0), zone)
        val dueToday = TaskEntity(
            id = 1, title = "Vence hoy", priority = TaskPriority.NORMAL,
            dueAt = DateRules.toEpochMillis(date, LocalTime.of(18, 0), zone)
        )
        val urgentTomorrow = TaskEntity(
            id = 2, title = "Urgente mañana", priority = TaskPriority.URGENT,
            dueAt = DateRules.toEpochMillis(date.plusDays(1), LocalTime.of(18, 0), zone)
        )
        assertEquals(dueToday, TaskRules.nextBestTask(listOf(urgentTomorrow, dueToday), now, zone))
    }

    @Test
    fun focusClock_formatsMinutesAndSeconds() {
        assertEquals("25:00", FocusClock.format(1500))
        assertEquals("00:00", FocusClock.format(-2))
    }
}
