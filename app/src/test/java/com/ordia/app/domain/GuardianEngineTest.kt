package com.ordia.app.domain

import com.ordia.app.data.local.FocusSessionEntity
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.preferences.GuardianSpecies
import com.ordia.app.data.preferences.UserPreferences
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardianEngineTest {
    private val zone = ZoneId.of("UTC")
    private val midday = Instant.parse("2026-07-29T15:00:00Z").toEpochMilli()

    @Test
    fun bondHelpsButCannotReplaceRealActivity() {
        val result = GuardianEngine.snapshot(
            tasks = emptyList(), habits = emptyList(), habitLogs = emptyList(),
            focusSessions = emptyList(), notes = emptyList(),
            preferences = UserPreferences(guardianBond = 9_999, guardianSpecies = GuardianSpecies.MOSS),
            nowMillis = midday, zoneId = zone
        )

        assertEquals(500, result.bondExperience)
        assertEquals(GuardianEngine.Stage.HATCHLING, result.stage)
        assertEquals(GuardianSpecies.MOSS, result.species)
    }

    @Test
    fun completedFocusChangesMoodAndExperience() {
        val result = GuardianEngine.snapshot(
            tasks = listOf(TaskEntity(title = "Prueba", completed = true, completedAt = midday)),
            habits = emptyList(), habitLogs = emptyList(),
            focusSessions = listOf(
                FocusSessionEntity(
                    startedAt = midday - 30 * 60_000L,
                    endedAt = midday,
                    actualMinutes = 30,
                    completed = true
                )
            ),
            notes = emptyList(), preferences = UserPreferences(),
            nowMillis = midday, zoneId = zone
        )

        assertEquals(GuardianEngine.Mood.FOCUSED, result.mood)
        assertEquals(42, result.experience)
        assertEquals(30, result.focusMinutesToday)
    }

    @Test
    fun persistedExperienceDoesNotDoubleCountDerivedActivity() {
        val task = TaskEntity(title = "Prueba", completed = true, completedAt = midday)
        val result = GuardianEngine.snapshot(
            tasks = listOf(task), habits = emptyList(), habitLogs = emptyList(),
            focusSessions = emptyList(), notes = emptyList(),
            preferences = UserPreferences(guardianExperience = 12),
            nowMillis = midday, zoneId = zone
        )

        assertEquals(12, result.experience)
        assertEquals(12, result.activityExperience)
    }

    @Test
    fun largerPersistedSnapshotWinsWithoutAddingDerivedAgain() {
        assertEquals(
            820,
            GuardianEngine.effectiveExperience(
                derivedExperience = 300,
                persistedExperience = 800,
                bond = 80
            )
        )
    }

    @Test
    fun newGuardianStartsWithHealthyEnergy() {
        val result = GuardianEngine.snapshot(
            tasks = emptyList(), habits = emptyList(), habitLogs = emptyList(),
            focusSessions = emptyList(), notes = emptyList(),
            preferences = UserPreferences(guardianLastInteraction = 0L),
            nowMillis = midday, zoneId = zone
        )
        assertTrue(result.energy >= 80)
    }

    @Test
    fun quietHoursSupportOvernightAndDaytimeRanges() {
        assertTrue(GuardianEngine.isQuietHours(22 * 60, 7 * 60, 23 * 60))
        assertTrue(GuardianEngine.isQuietHours(22 * 60, 7 * 60, 6 * 60 + 30))
        assertTrue(!GuardianEngine.isQuietHours(22 * 60, 7 * 60, 12 * 60))
        assertTrue(GuardianEngine.isQuietHours(9 * 60, 17 * 60, 12 * 60))
        assertTrue(!GuardianEngine.isQuietHours(9 * 60, 17 * 60, 18 * 60))
        assertTrue(!GuardianEngine.isQuietHours(9 * 60, 9 * 60, 9 * 60))
    }

    @Test
    fun derivedExperienceCountsEachStoredRecordOnce() {
        val value = GuardianEngine.derivedExperience(
            tasks = listOf(TaskEntity(title = "Hecha", completed = true, completedAt = midday)),
            habits = emptyList(), habitLogs = emptyList(),
            focusSessions = listOf(
                FocusSessionEntity(
                    startedAt = midday - 30 * 60_000L,
                    endedAt = midday,
                    actualMinutes = 30,
                    completed = true
                )
            ),
            notes = emptyList()
        )
        assertEquals(42, value)
    }

    @Test
    fun dailyCareGoalsAreDeterministicAtFixedTime() {
        val result = GuardianEngine.snapshot(
            tasks = listOf(TaskEntity(title = "Hecha", completed = true, completedAt = midday)),
            habits = emptyList(), habitLogs = emptyList(), focusSessions = emptyList(), notes = emptyList(),
            preferences = UserPreferences(), nowMillis = midday, zoneId = zone
        )
        assertEquals(2, result.dailyGoalsCompleted)
        assertEquals(3, result.dailyGoalsTotal)
    }
}
