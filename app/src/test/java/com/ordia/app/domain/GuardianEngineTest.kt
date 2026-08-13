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
    fun overdueCountsOnlyRootTasksNotNestedSubtasks() {
        // Un padre atrasado con dos subtareas también atrasadas debe contar
        // como 1 solo atrasado (igual que la tarjeta de resumen), no 3.
        val yesterday = midday - 24 * 60 * 60_000L
        val parent = TaskEntity(id = 1, title = "Padre", dueAt = yesterday)
        val subA = TaskEntity(id = 2, title = "Sub A", dueAt = yesterday, parentTaskId = 1)
        val subB = TaskEntity(id = 3, title = "Sub B", dueAt = yesterday, parentTaskId = 1)

        val result = GuardianEngine.snapshot(
            tasks = listOf(parent, subA, subB),
            habits = emptyList(), habitLogs = emptyList(),
            focusSessions = emptyList(), notes = emptyList(),
            preferences = UserPreferences(),
            nowMillis = midday, zoneId = zone
        )

        assertEquals(1, result.overdue)
        // Con un solo atrasado el guardián no entra en ánimo preocupado (umbral 5):
        // sin interacción ni avance hoy, el ánimo es CURIOUS, no CONCERNED.
        assertEquals(GuardianEngine.Mood.CURIOUS, result.mood)
        // Pero la acción sugerida sí reacciona al atrasado (overdue > 0).
        assertTrue(result.suggestedAction.contains("atrasada"))
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
