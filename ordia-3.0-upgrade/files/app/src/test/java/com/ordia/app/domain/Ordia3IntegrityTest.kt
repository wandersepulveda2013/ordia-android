package com.ordia.app.domain

import com.ordia.app.data.local.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class Ordia3IntegrityTest {
    @Test fun cancelledTask_isNotRecommendedOrCounted() {
        val cancelled = TaskEntity(id=1, title="Cancelada", status=TaskStatus.CANCELLED)
        assertNull(TaskRules.nextBestTask(listOf(cancelled)))
        assertEquals(0, TaskRules.completionRate(listOf(cancelled)))
    }
    @Test fun streakDoesNotBreakAtStartOfToday() {
        val habit = HabitEntity(id=1, title="Leer")
        val today = LocalDate.of(2026,7,30)
        val logs = listOf(HabitLogEntity(1, today.minusDays(1).toEpochDay(), 1))
        assertEquals(1, HabitRules.currentStreak(habit, logs, today))
    }
    @Test fun overdueRecurrenceSkipsIntoFutureAndKeepsOffsets() {
        val zone=ZoneId.of("America/Santo_Domingo")
        val due=LocalDateTime.of(2026,1,1,10,0).atZone(zone).toInstant().toEpochMilli()
        val completed=LocalDateTime.of(2026,7,30,10,0).atZone(zone).toInstant().toEpochMilli()
        val next=RecurrenceEngine.nextOccurrence(TaskEntity(title="Diaria", startAt=due-3_600_000, dueAt=due, reminderAt=due-1_800_000, recurrence=RecurrenceFrequency.DAILY), completed, zone)!!
        assertTrue(next.dueAt!! > completed)
        assertEquals(3_600_000, next.dueAt!! - next.startAt!!)
        assertEquals(1_800_000, next.dueAt!! - next.reminderAt!!)
    }
}
