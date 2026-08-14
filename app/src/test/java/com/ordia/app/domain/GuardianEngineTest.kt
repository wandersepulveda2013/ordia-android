package com.ordia.app.domain

import com.ordia.app.data.local.FocusSessionEntity
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.preferences.GuardianSpecies
import com.ordia.app.data.preferences.UserPreferences
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
    fun suggestedActionNamesTheSmallestOverdueTask() {
        // El nudge del guardián nombra la tarea atrasada más pequeña (por duración
        // planificada) en vez de un consejo genérico: recupera la tarea olvidada en
        // la superficie existente. Aquí "Luz" (30 min) debe preferirse a "Informe"
        // (90 min) aunque ambas estén atrasadas.
        val past = midday - 86_400_000L
        val big = TaskEntity(id = 1, title = "Informe", dueAt = past, durationMinutes = 90)
        val small = TaskEntity(id = 2, title = "Pagar luz", dueAt = past, durationMinutes = 30)

        val result = GuardianEngine.snapshot(
            tasks = listOf(big, small),
            habits = emptyList(), habitLogs = emptyList(),
            focusSessions = emptyList(), notes = emptyList(),
            preferences = UserPreferences(), nowMillis = midday, zoneId = zone
        )

        assertTrue(result.suggestedAction.contains("Pagar luz"))
        assertTrue(result.suggestedAction.contains("atrasada"))
        // La duración planificada real (30) se muestra, no la cruda si fuera 0.
        assertTrue(result.suggestedAction.contains("30"))
        assertFalse(result.suggestedAction.contains("Informe"))
    }

    @Test
    fun suggestedActionDeterministicForEqualDurationOverdueTasks() {
        // A igual duración, el orden es determinista (prioridad → vencimiento → id):
        // dos ejecuciones idénticas nombran la misma tarea. id menor gana el desempate.
        val past = midday - 86_400_000L
        val a = TaskEntity(id = 5, title = "Alfa", dueAt = past, durationMinutes = 30)
        val b = TaskEntity(id = 2, title = "Beta", dueAt = past, durationMinutes = 30)

        val result = GuardianEngine.snapshot(
            tasks = listOf(a, b),
            habits = emptyList(), habitLogs = emptyList(),
            focusSessions = emptyList(), notes = emptyList(),
            preferences = UserPreferences(), nowMillis = midday, zoneId = zone
        )

        assertTrue(result.suggestedAction.contains("Beta"))
    }

    @Test
    fun suggestedActionIgnoresSubtasksWhenNamingOverdueTask() {
        // Una subtarea atrasada más pequeña que el padre NO debe ser nombrada:
        // el guardián solo considera tareas raíz, igual que el conteo `overdue`.
        val past = midday - 86_400_000L
        val parent = TaskEntity(id = 1, title = "Padre grande", dueAt = past, durationMinutes = 90)
        val tinySub = TaskEntity(id = 2, title = "Sub minúscula", dueAt = past, durationMinutes = 10, parentTaskId = 1)

        val result = GuardianEngine.snapshot(
            tasks = listOf(parent, tinySub),
            habits = emptyList(), habitLogs = emptyList(),
            focusSessions = emptyList(), notes = emptyList(),
            preferences = UserPreferences(), nowMillis = midday, zoneId = zone
        )

        assertTrue(result.suggestedAction.contains("Padre grande"))
        assertFalse(result.suggestedAction.contains("Sub minúscula"))
    }

    @Test
    fun suggestedActionSkipsOverdueTaskAlreadyInProgress() {
        // Una tarea vencida que se está ejecutando justo ahora (startAt ya empezó
        // y no rebasó su duración, dueAt ya pasado) NO debe ser nombrada como
        // "hazla ya": el usuario ya la está haciendo. El guardián nombra en su
        // lugar la siguiente atrasada más pequeña que no esté en curso.
        // midday = 15:00. inProgress: startAt 14:30, duración 120 min → activa hasta
        // 16:30; dueAt 14:50 → vencida y en curso a la vez.
        val inProgressOverdue = TaskEntity(
            id = 1, title = "En curso",
            startAt = midday - 30 * 60_000L, dueAt = midday - 10 * 60_000L,
            durationMinutes = 120
        )
        val otherOverdue = TaskEntity(id = 2, title = "Pagar luz", dueAt = midday - 86_400_000L, durationMinutes = 30)

        val result = GuardianEngine.snapshot(
            tasks = listOf(inProgressOverdue, otherOverdue),
            habits = emptyList(), habitLogs = emptyList(),
            focusSessions = emptyList(), notes = emptyList(),
            preferences = UserPreferences(), nowMillis = midday, zoneId = zone
        )

        assertTrue(result.suggestedAction.contains("Pagar luz"))
        assertFalse(result.suggestedAction.contains("En curso"))
    }

    @Test
    fun suggestedActionFallsBackWhenAllOverdueAreInProgress() {
        // Si TODAS las atrasadas están en curso, no queda ninguna que nombrar: el
        // nudge cae al mensaje genérico en vez de insistir con una tarea en curso.
        val inProgressOverdue = TaskEntity(
            id = 1, title = "En curso",
            startAt = midday - 30 * 60_000L, dueAt = midday - 10 * 60_000L,
            durationMinutes = 120
        )

        val result = GuardianEngine.snapshot(
            tasks = listOf(inProgressOverdue),
            habits = emptyList(), habitLogs = emptyList(),
            focusSessions = emptyList(), notes = emptyList(),
            preferences = UserPreferences(), nowMillis = midday, zoneId = zone
        )

        // overdue > 0 dispara la rama atrasada, pero sin candidata nombrable cae al
        // mensaje genérico (no contiene el título de la tarea en curso).
        assertFalse(result.suggestedAction.contains("En curso"))
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

    @Test
    fun overdueCountIgnoresSubtasksToAvoidInflatedConcern() {
        val past = midday - 86_400_000L
        val parent = TaskEntity(id = 1, title = "Padre", dueAt = past)
        val subs = listOf(
            TaskEntity(id = 2, title = "s1", parentTaskId = 1, dueAt = past),
            TaskEntity(id = 3, title = "s2", parentTaskId = 1, dueAt = past),
            TaskEntity(id = 4, title = "s3", parentTaskId = 1, dueAt = past),
            TaskEntity(id = 5, title = "s4", parentTaskId = 1, dueAt = past)
        )
        val result = GuardianEngine.snapshot(
            tasks = listOf(parent) + subs,
            habits = emptyList(), habitLogs = emptyList(),
            focusSessions = emptyList(), notes = emptyList(),
            preferences = UserPreferences(),
            nowMillis = midday, zoneId = zone
        )
        // 1 tarea logica vencida (<5) no debe disparar CONCERNED ni inflar el conteo.
        assertNotEquals(GuardianEngine.Mood.CONCERNED, result.mood)
        assertFalse(result.message.contains("5"))
    }

    @Test
    fun derivedExperienceCountsLogicalTasksNotSubtasks() {
        val doneParent = TaskEntity(id = 10, title = "Hecho", completed = true, completedAt = midday)
        val doneSubs = listOf(
            TaskEntity(id = 11, title = "ds1", parentTaskId = 10, completed = true, completedAt = midday),
            TaskEntity(id = 12, title = "ds2", parentTaskId = 10, completed = true, completedAt = midday),
            TaskEntity(id = 13, title = "ds3", parentTaskId = 10, completed = true, completedAt = midday)
        )
        val value = GuardianEngine.derivedExperience(
            tasks = listOf(doneParent) + doneSubs,
            habits = emptyList(), habitLogs = emptyList(),
            focusSessions = emptyList(), notes = emptyList()
        )
        // 1 tarea logica completada = 12 XP, no 48 por las subtareas.
        assertEquals(12, value)
    }
}
