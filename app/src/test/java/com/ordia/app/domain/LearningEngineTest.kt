package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class LearningEngineTest {
    private val zone = ZoneId.of("America/Santo_Domingo")
    private val today = LocalDate.of(2026, 7, 29)
    private val now = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun completed(id: Long, completedAt: Long) =
        TaskEntity(id = id, title = "T$id", completed = true, completedAt = completedAt)

    @Test
    fun emptyTasksReturnDefaults() {
        val p = LearningEngine.learn(emptyList(), now, zone)

        assertEquals(9 * 60, p.dayStartMinute)
        assertEquals(18 * 60, p.dayEndMinute)
    }

    @Test
    fun ignoresIncompleteAndOldTasks() {
        val old = completed(1, at(today.minusDays(30), 8, 10))
        val pending = TaskEntity(id = 2, title = "pendiente")
        val noCompletedAt = TaskEntity(id = 3, title = "sin fecha", completed = true, completedAt = null)
        val tasks = listOf(old, pending, noCompletedAt)

        val p = LearningEngine.learn(tasks, now, zone)

        assertEquals(9 * 60, p.dayStartMinute)
        assertEquals(18 * 60, p.dayEndMinute)
    }

    @Test
    fun learnsWorkingWindowFromCompletionTimes() {
        // Completadas entre 7:30 y 21:00 en la ventana
        val tasks = listOf(
            completed(1, at(today, 7, 30)),
            completed(2, at(today, 8, 10)),
            completed(3, at(today.minusDays(1), 9, 0)),
            completed(4, at(today.minusDays(2), 12, 30)),
            completed(5, at(today.minusDays(3), 18, 45)),
            completed(6, at(today.minusDays(4), 20, 10)),
            completed(7, at(today.minusDays(5), 21, 0))
        )

        val p = LearningEngine.learn(tasks, now, zone)

        // percentil 10 ≈ 8:10 → 8:00; percentil 90 ≈ 20:10 → 20:00
        assertEquals(8 * 60, p.dayStartMinute)
        assertEquals(20 * 60, p.dayEndMinute)
    }

    @Test
    fun startClampedToSixAMWhenVeryEarly() {
        val tasks = (0..6).map { i ->
            completed(i.toLong(), at(today.minusDays(i.toLong()), 5, 0))
        }

        val p = LearningEngine.learn(tasks, now, zone)

        assertTrue(p.dayStartMinute >= 6 * 60)
    }

    @Test
    fun endClampedToElevenPMWhenVeryLate() {
        val tasks = (0..6).map { i ->
            completed(i.toLong(), at(today.minusDays(i.toLong()), 23, 50))
        }

        val p = LearningEngine.learn(tasks, now, zone)

        assertTrue(p.dayEndMinute <= 23 * 60)
    }

    @Test
    fun ensuresWindowWhenExtremesClose() {
        // Todos completan entre 9:00 y 10:30: la ventana no se colapsa.
        val tasks = (0..6).map { i ->
            completed(i.toLong(), at(today.minusDays(i.toLong()), 9 + (i % 2), (i * 10) % 60))
        }

        val p = LearningEngine.learn(tasks, now, zone)

        assertTrue(p.dayEndMinute - p.dayStartMinute >= 60)
    }

    @Test
    fun roundsToQuarterHour() {
        val tasks = listOf(
            completed(1, at(today, 7, 43)),
            completed(2, at(today.minusDays(1), 8, 16)),
            completed(3, at(today.minusDays(2), 9, 2)),
            completed(4, at(today.minusDays(3), 12, 33)),
            completed(5, at(today.minusDays(4), 18, 47)),
            completed(6, at(today.minusDays(5), 20, 22)),
            completed(7, at(today.minusDays(6), 21, 4))
        )

        val p = LearningEngine.learn(tasks, now, zone)

        assertEquals(0, p.dayStartMinute % 15)
        assertEquals(0, p.dayEndMinute % 15)
    }
}
