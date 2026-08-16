package com.ordia.app.domain

import com.ordia.app.data.local.CommitmentEntity
import com.ordia.app.data.local.CommitmentKind
import com.ordia.app.data.local.CommitmentOwner
import com.ordia.app.data.local.CommitmentReviewStatus
import com.ordia.app.data.local.FocusSessionEntity
import com.ordia.app.data.local.HabitEntity
import com.ordia.app.data.local.HabitLogEntity
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.data.local.TaskStatus
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
    fun suggestedActionNamesUrgentOverdueOverSmallerNonUrgent() {
        // Una tarea atrasada URGENTE (la "vencida importante") debe preferirse a una
        // atrasada más pequeña y de menor prioridad: el nudge del guardián no debe
        // alejar al usuario de un plazo crítico que se le está pasando solo porque
        // exista algo más rápido de resolver. La importancia rompe el empate hacia lo
        // urgente; el "quick win" queda para cuando nada importante está atrasado.
        val past = midday - 86_400_000L
        val urgent = TaskEntity(id = 1, title = "Entregar informe", dueAt = past, durationMinutes = 90, priority = TaskPriority.URGENT)
        val small = TaskEntity(id = 2, title = "Regar plantas", dueAt = past, durationMinutes = 10, priority = TaskPriority.LOW)

        val result = GuardianEngine.snapshot(
            tasks = listOf(small, urgent),
            habits = emptyList(), habitLogs = emptyList(),
            focusSessions = emptyList(), notes = emptyList(),
            preferences = UserPreferences(), nowMillis = midday, zoneId = zone
        )

        assertTrue(result.suggestedAction.contains("Entregar informe"))
        assertFalse(result.suggestedAction.contains("Regar plantas"))
        // El nudge transmite que es urgente (descripción honesta de su prioridad real).
        assertTrue(result.suggestedAction.contains("urgente"))
    }

    @Test
    fun suggestedActionKeepsSmallestAmongUrgentOverdue() {
        // Entre varias atrasadas URGENTES, el "quick win" sigue vigente: se nombra la
        // más pequeña para reducir la fricción de arrancar, sin perder la señal de
        // urgencia. Así urgencia y momentum cooperan en vez de contradecirse.
        val past = midday - 86_400_000L
        val bigUrgent = TaskEntity(id = 1, title = "Informe urgente", dueAt = past, durationMinutes = 90, priority = TaskPriority.URGENT)
        val smallUrgent = TaskEntity(id = 2, title = "Llamar cliente", dueAt = past, durationMinutes = 10, priority = TaskPriority.URGENT)

        val result = GuardianEngine.snapshot(
            tasks = listOf(bigUrgent, smallUrgent),
            habits = emptyList(), habitLogs = emptyList(),
            focusSessions = emptyList(), notes = emptyList(),
            preferences = UserPreferences(), nowMillis = midday, zoneId = zone
        )

        assertTrue(result.suggestedAction.contains("Llamar cliente"))
        assertFalse(result.suggestedAction.contains("Informe urgente"))
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

    // --- suggestedAction: recuperación de tareas con hueco planificado olvidado ---

    @Test
    fun suggestedAction_nombraTareaConHuecoPasadoCuandoNoHayAtrasadas() {
        // start 13:00, duración 30 min → ventana hasta 13:30. now 15:00 rebasó el hueco.
        // due en el futuro → no es atrasada. Sin este nudge, la tarea caía al limbo y el
        // guardián decía "Completa una tarea breve" sin nombrarla.
        val missed = TaskEntity(
            id = 1, title = "Llamar al banco", startAt = midday - 2 * 60 * 60_000L,
            dueAt = midday + 2 * 24 * 60 * 60_000L, durationMinutes = 30
        )
        val result = GuardianEngine.snapshot(
            tasks = listOf(missed), habits = emptyList(), habitLogs = emptyList(),
            focusSessions = emptyList(), notes = emptyList(),
            preferences = UserPreferences(), nowMillis = midday, zoneId = zone
        )
        assertTrue(result.suggestedAction.contains("Llamar al banco"))
        assertTrue(result.suggestedAction.contains("hueco"))
    }

    @Test
    fun suggestedAction_atrasadaTomaPrecedenciaSobreHuecoPasado() {
        // Una atrasada (due pasado) y una con hueco pasado (due futuro): la atrasada es
        // señal más fuerte y debe nombrarse primero. No doble señalización.
        val overdue = TaskEntity(
            id = 1, title = "Factura vencida", dueAt = midday - 60 * 60_000L, durationMinutes = 20
        )
        val missed = TaskEntity(
            id = 2, title = "Llamar al banco", startAt = midday - 2 * 60 * 60_000L,
            dueAt = midday + 2 * 24 * 60 * 60_000L, durationMinutes = 30
        )
        val result = GuardianEngine.snapshot(
            tasks = listOf(overdue, missed), habits = emptyList(), habitLogs = emptyList(),
            focusSessions = emptyList(), notes = emptyList(),
            preferences = UserPreferences(), nowMillis = midday, zoneId = zone
        )
        assertTrue(result.suggestedAction.contains("Factura vencida"))
        assertTrue(result.suggestedAction.contains("atrasada"))
        assertFalse(result.suggestedAction.contains("Llamar al banco"))
    }

    @Test
    fun suggestedAction_huecoPasadoUrgenteSePrefiereAlNormal() {
        // Dos huecos pasados: el URGENTE (aunque sea más largo) manda sobre el NORMAL,
        // igual que smallestOverdueAction prefiere lo urgente. Coherencia de criterio.
        val urgent = TaskEntity(
            id = 1, title = "Cita crítica", startAt = midday - 2 * 60 * 60_000L,
            dueAt = midday + 24 * 60 * 60_000L, durationMinutes = 45,
            priority = TaskPriority.URGENT
        )
        val normal = TaskEntity(
            id = 2, title = "Leer informe", startAt = midday - 2 * 60 * 60_000L,
            dueAt = midday + 24 * 60 * 60_000L, durationMinutes = 15,
            priority = TaskPriority.NORMAL
        )
        val result = GuardianEngine.snapshot(
            tasks = listOf(normal, urgent), habits = emptyList(), habitLogs = emptyList(),
            focusSessions = emptyList(), notes = emptyList(),
            preferences = UserPreferences(), nowMillis = midday, zoneId = zone
        )
        assertTrue(result.suggestedAction.contains("Cita crítica"))
        assertTrue(result.suggestedAction.contains("urgente"))
        assertFalse(result.suggestedAction.contains("Leer informe"))
    }

    @Test
    fun suggestedAction_noNombraHuecoPasadoSiEstaMarcadaEnCurso() {
        // Hueco pasado pero el usuario la marcó en curso: está sobre ella, no es olvido.
        // El nudge cae al mensaje genérico, sin nombrar la tarea ni hablar de "hueco".
        val inProgress = TaskEntity(
            id = 1, title = "Trabajándola", startAt = midday - 2 * 60 * 60_000L,
            durationMinutes = 30, status = TaskStatus.IN_PROGRESS
        )
        val result = GuardianEngine.snapshot(
            tasks = listOf(inProgress), habits = emptyList(), habitLogs = emptyList(),
            focusSessions = emptyList(), notes = emptyList(),
            preferences = UserPreferences(), nowMillis = midday, zoneId = zone
        )
        assertFalse(result.suggestedAction.contains("Trabajándola"))
        assertFalse(result.suggestedAction.contains("hueco"))
    }

    @Test
    fun suggestedAction_caeAGenericoCuandoNoHayHuecoPasadoNiAtrasadas() {
        // Tareas de bandeja sin startAt, sin atraso: nada que recuperar, nudge genérico.
        val inbox = TaskEntity(id = 1, title = "Idea suelta", durationMinutes = 15)
        val result = GuardianEngine.snapshot(
            tasks = listOf(inbox), habits = emptyList(), habitLogs = emptyList(),
            focusSessions = emptyList(), notes = emptyList(),
            preferences = UserPreferences(), nowMillis = midday, zoneId = zone
        )
        assertFalse(result.suggestedAction.contains("Idea suelta"))
        assertFalse(result.suggestedAction.contains("hueco"))
    }

    @Test
    fun suggestedAction_noDiceIniciarElDiaSiYaHayImpulsoRealDeEnfoque() {
        // El usuario concentró 20 min pero no completó ninguna tarea: el día ya está
        // en marcha. El nudge NO debe enmarcarlo como "no empezado" ("iniciar el día");
        // señala lo básico pendiente (el hábito), sin ignorar el avance real.
        val task = TaskEntity(id = 1, title = "Revisar correo", durationMinutes = 15)
        val habit = HabitEntity(id = 1, title = "Leer", targetPerPeriod = 1)
        val focus = FocusSessionEntity(
            id = 1, startedAt = midday - 20 * 60_000L, actualMinutes = 20, completed = true
        )
        val result = GuardianEngine.snapshot(
            tasks = listOf(task), habits = listOf(habit), habitLogs = emptyList(),
            focusSessions = listOf(focus), notes = emptyList(),
            preferences = UserPreferences(), nowMillis = midday, zoneId = zone
        )
        assertFalse(result.suggestedAction.contains("iniciar el día"))
        assertTrue(result.suggestedAction.contains("hábito"))
    }

    @Test
    fun suggestedAction_noDiceIniciarElDiaSiYaHayImpulsoRealDeHabito() {
        // Mantuvo un hábito pero no completó tarea ni concentró 15 min: el día avanzó.
        // No es "iniciar"; el nudge invita a la sesión de enfoque, no a "arrancar el día".
        val task = TaskEntity(id = 1, title = "Revisar correo", durationMinutes = 15)
        val habit = HabitEntity(id = 1, title = "Leer", targetPerPeriod = 1)
        val todayEpoch = Instant.ofEpochMilli(midday).atZone(zone).toLocalDate().toEpochDay()
        val log = HabitLogEntity(habitId = 1, epochDay = todayEpoch, count = 1)
        val result = GuardianEngine.snapshot(
            tasks = listOf(task), habits = listOf(habit), habitLogs = listOf(log),
            focusSessions = emptyList(), notes = emptyList(),
            preferences = UserPreferences(), nowMillis = midday, zoneId = zone
        )
        assertFalse(result.suggestedAction.contains("iniciar el día"))
        assertTrue(result.suggestedAction.contains("enfoque"))
    }

    @Test
    fun suggestedAction_cierraElCirculoCuandoHayImpulsoPeroNoTareaCompletada() {
        // Enfoque (>=15 min) y hábito hechos, sin tareas completadas: el cuidado diario
        // básico está cubierto y el día avanzó. El nudge invita a "cerrar el círculo"
        // completando una tarea, en vez de "iniciar el día" (ignoraría el avance) o
        // "descansar" (quedaría sin transformar el impulso en una tarea terminada).
        val task = TaskEntity(id = 1, title = "Revisar correo", durationMinutes = 15)
        val habit = HabitEntity(id = 1, title = "Leer", targetPerPeriod = 1)
        val todayEpoch = Instant.ofEpochMilli(midday).atZone(zone).toLocalDate().toEpochDay()
        val log = HabitLogEntity(habitId = 1, epochDay = todayEpoch, count = 1)
        val focus = FocusSessionEntity(
            id = 1, startedAt = midday - 20 * 60_000L, actualMinutes = 20, completed = true
        )
        val result = GuardianEngine.snapshot(
            tasks = listOf(task), habits = listOf(habit), habitLogs = listOf(log),
            focusSessions = listOf(focus), notes = emptyList(),
            preferences = UserPreferences(), nowMillis = midday, zoneId = zone
        )
        assertFalse(result.suggestedAction.contains("iniciar el día"))
        assertFalse(result.suggestedAction.contains("descansar"))
        assertTrue(result.suggestedAction.contains("cerrar el círculo"))
    }

    // --- suggestedAction: recuperación de la captura de bandeja arrinconada (stale-inbox) ---

    @Test
    fun suggestedAction_nombraCapturaArrinconadaCuandoNoHayAtrasadasNiHuecos() {
        // Tercer olvido: una idea capturada en la bandeja, sin fecha ni hueco, que lleva
        // ≥7 días esperando. Sin vencidas ni huecos pasados, el nudge la nombra como el
        // primer paso del día (hacer/agendar/quitar) en vez del consejo genérico. Cierra la
        // asimetría con la tarjeta del asistente (GuardianCoach), que sí la recuperaba.
        val staleInbox = TaskEntity(
            id = 1, title = "Idea de proyecto", createdAt = midday - 8L * 24 * 60 * 60_000L
        )
        val result = GuardianEngine.snapshot(
            tasks = listOf(staleInbox), habits = emptyList(), habitLogs = emptyList(),
            focusSessions = emptyList(), notes = emptyList(),
            preferences = UserPreferences(), nowMillis = midday, zoneId = zone
        )
        assertTrue(result.suggestedAction.contains("Idea de proyecto"))
        assertTrue(result.suggestedAction.contains("bandeja"))
        assertTrue(result.suggestedAction.contains("sin fecha"))
    }

    @Test
    fun suggestedAction_atrasadaTomaPrecedenciaSobreCapturaArrinconada() {
        // Una atrasada (due pasado) y una captura arrinconada (sin fecha, ≥7 días): la
        // atrasada es señal más fuerte y debe nombrarse primero. El stale-inbox no roba el
        // lugar al plazo incumplido. nextBestTask ordena overdue por encima de la bandeja.
        val overdue = TaskEntity(
            id = 1, title = "Factura vencida", dueAt = midday - 60 * 60_000L, durationMinutes = 20
        )
        val staleInbox = TaskEntity(
            id = 2, title = "Idea arrinconada", createdAt = midday - 8L * 24 * 60 * 60_000L
        )
        val result = GuardianEngine.snapshot(
            tasks = listOf(overdue, staleInbox), habits = emptyList(), habitLogs = emptyList(),
            focusSessions = emptyList(), notes = emptyList(),
            preferences = UserPreferences(), nowMillis = midday, zoneId = zone
        )
        assertTrue(result.suggestedAction.contains("Factura vencida"))
        assertTrue(result.suggestedAction.contains("atrasada"))
        assertFalse(result.suggestedAction.contains("Idea arrinconada"))
    }

    @Test
    fun suggestedAction_noNombraCapturaArrinconadaSiYaHayImpulsoReal() {
        // La recuperación del stale-inbox es "suave": respeta que una captura sin fecha es
        // aplazable. Si el día ya avanzó (enfoque ≥15 min + hábito), el nudge invita a
        // "cerrar el círculo", no a revolver la bandeja. La captura olvidada NO se nombra.
        val staleInbox = TaskEntity(
            id = 1, title = "Idea arrinconada", createdAt = midday - 8L * 24 * 60 * 60_000L
        )
        val habit = HabitEntity(id = 1, title = "Leer", targetPerPeriod = 1)
        val todayEpoch = Instant.ofEpochMilli(midday).atZone(zone).toLocalDate().toEpochDay()
        val log = HabitLogEntity(habitId = 1, epochDay = todayEpoch, count = 1)
        val focus = FocusSessionEntity(
            id = 1, startedAt = midday - 20 * 60_000L, actualMinutes = 20, completed = true
        )
        val result = GuardianEngine.snapshot(
            tasks = listOf(staleInbox), habits = listOf(habit), habitLogs = listOf(log),
            focusSessions = listOf(focus), notes = emptyList(),
            preferences = UserPreferences(), nowMillis = midday, zoneId = zone
        )
        assertFalse(result.suggestedAction.contains("Idea arrinconada"))
        assertFalse(result.suggestedAction.contains("bandeja"))
        assertTrue(result.suggestedAction.contains("cerrar el círculo"))
    }

    @Test
    fun suggestedAction_noNombraCapturaRecienteDeBandeja() {
        // Una captura fresca (<7 días, sin fecha) NO es "olvidada": nada que recuperar, el
        // nudge cae al genérico de iniciar el día sin nombrarla. Confirma que la rama
        // stale-inbox solo dispara con la edad mínima honesta (STALE_INBOX_DAYS_THRESHOLD).
        val freshInbox = TaskEntity(id = 1, title = "Idea reciente", durationMinutes = 15)
        val result = GuardianEngine.snapshot(
            tasks = listOf(freshInbox), habits = emptyList(), habitLogs = emptyList(),
            focusSessions = emptyList(), notes = emptyList(),
            preferences = UserPreferences(), nowMillis = midday, zoneId = zone
        )
        assertFalse(result.suggestedAction.contains("Idea reciente"))
        assertFalse(result.suggestedAction.contains("bandeja"))
    }

    @Test
    fun suggestedAction_noNombraCapturaArrinconadaSiTareaConFechaVenceHoyEsMejorCandidata() {
        // El stale-inbox es la señal más débil: delega en nextBestTask y solo se nombra si
        // la mejor candidata ES ella misma la captura olvidada. Aquí hay una tarea que vence
        // hoy (timeRank=3) y una captura arrinconada (timeRank=0): nextBestTask elige la de
        // hoy, que NO es stale-inbox, así que el nudge cae a genérico sin robarle su lugar.
        val dueToday = TaskEntity(
            id = 1, title = "Entregar reporte",
            dueAt = Instant.parse("2026-07-29T23:59:00Z").toEpochMilli(), durationMinutes = 30
        )
        val staleInbox = TaskEntity(
            id = 2, title = "Idea arrinconada", createdAt = midday - 8L * 24 * 60 * 60_000L
        )
        val result = GuardianEngine.snapshot(
            tasks = listOf(dueToday, staleInbox), habits = emptyList(), habitLogs = emptyList(),
            focusSessions = emptyList(), notes = emptyList(),
            preferences = UserPreferences(), nowMillis = midday, zoneId = zone
        )
        assertFalse(result.suggestedAction.contains("Idea arrinconada"))
        assertFalse(result.suggestedAction.contains("bandeja"))
    }

    // --- Paridad "completadas hoy" (c.284): fuente única TaskRules.completedTodayCount ---
    // GuardianEngine antes contaba tareas con completedAt hoy PERO completed=false
    // (no chequeaba `completed`), inflando ánimo/energía sobre actividad inexistente.

    @Test
    fun completedToday_ignoresCompletedAtSetButCompletedFalse() {
        // Dato inconsistente: completedAt hoy pero completed=false. No es un logro.
        val inconsistent = TaskEntity(title = "Fantasma", completed = false, completedAt = midday)
        val result = GuardianEngine.snapshot(
            tasks = listOf(inconsistent), habits = emptyList(), habitLogs = emptyList(),
            focusSessions = emptyList(), notes = emptyList(),
            preferences = UserPreferences(), nowMillis = midday, zoneId = zone
        )
        assertEquals(0, result.completedToday)
    }

    @Test
    fun completedToday_ignoresCancelledCompletedToday() {
        val cancelled = TaskEntity(
            title = "Descartada", completed = true, completedAt = midday,
            status = TaskStatus.CANCELLED
        )
        val result = GuardianEngine.snapshot(
            tasks = listOf(cancelled), habits = emptyList(), habitLogs = emptyList(),
            focusSessions = emptyList(), notes = emptyList(),
            preferences = UserPreferences(), nowMillis = midday, zoneId = zone
        )
        assertEquals(0, result.completedToday)
    }

    // --- suggestedAction: recuperación de la 4ª clase de olvido ---
    // Un compromiso vencido extraído de una conversación (dueAt pasado, PENDING, sin
    // convertir en tarea) es una promesa que se pasó de plazo. Hasta ahora el nudge del
    // guardián recuperaba las 3 clases de olvido de tareas (atrasada / hueco incumplido /
    // captura arrinconada) pero callaba esta 4ª — la superficie de recuperación visible
    // decía "todo en orden" mientras una promesa vencida pasaba inadvertida.

    private fun overdueCommitment(
        id: Long,
        action: String,
        dueAt: Long,
        actor: String = "yo",
        status: CommitmentReviewStatus = CommitmentReviewStatus.PENDING
    ) = CommitmentEntity(
        id = id, conversationId = 1,
        kind = CommitmentKind.SELF_COMMITMENT, owner = CommitmentOwner.SELF,
        actor = actor, action = action, dueAt = dueAt, confidence = 0.8f,
        reviewStatus = status, fingerprint = "fp$id", createdAt = dueAt
    )

    @Test
    fun suggestedAction_nombraCompromisoVencidoCuandoNoHayTareasOlvidadas() {
        // Sin tareas atrasadas ni con hueco pasado, pero con un compromiso vencido
        // ("te llamo" debido hace 2 días, PENDING): el guardián debe nombrarlo en vez
        // del consejo genérico. Es la 4ª clase de olvido; callarla es mentir por omisión.
        val commitment = overdueCommitment(1, "te llamo", midday - 2 * 86_400_000L)
        val result = GuardianEngine.snapshot(
            tasks = emptyList(), commitments = listOf(commitment),
            habits = emptyList(), habitLogs = emptyList(),
            focusSessions = emptyList(), notes = emptyList(),
            preferences = UserPreferences(), nowMillis = midday, zoneId = zone
        )
        assertTrue(result.suggestedAction.contains("te llamo"))
    }

    @Test
    fun suggestedAction_nombraCompromisoMasAtrasadoPrimero() {
        // Dos compromisos vencidos: el más atrasado (3 días) debe nombrarse antes que
        // el de 1 día. Orden determinista de CommitmentRules.overduePendingSorted.
        val older = overdueCommitment(1, "enviar propuesta", midday - 3 * 86_400_000L)
        val newer = overdueCommitment(2, "te llamo", midday - 86_400_000L)
        val result = GuardianEngine.snapshot(
            tasks = emptyList(), commitments = listOf(newer, older),
            habits = emptyList(), habitLogs = emptyList(),
            focusSessions = emptyList(), notes = emptyList(),
            preferences = UserPreferences(), nowMillis = midday, zoneId = zone
        )
        assertTrue(result.suggestedAction.contains("enviar propuesta"))
        assertFalse(result.suggestedAction.contains("te llamo"))
    }

    @Test
    fun suggestedAction_tareaAtrasadaTomaPrecedenciaSobreCompromisoVencido() {
        // Una tarea atrasada (due pasado) es señal más fuerte y accionable directamente:
        // se nombra primero. El compromiso vencido se añade como cola informativa para no
        // callarlo. No doble señalización de acción, pero tampoco silencio.
        val overdueTask = TaskEntity(
            id = 1, title = "Factura vencida", dueAt = midday - 60 * 60_000L, durationMinutes = 20
        )
        val commitment = overdueCommitment(2, "te llamo", midday - 2 * 86_400_000L)
        val result = GuardianEngine.snapshot(
            tasks = listOf(overdueTask), commitments = listOf(commitment),
            habits = emptyList(), habitLogs = emptyList(),
            focusSessions = emptyList(), notes = emptyList(),
            preferences = UserPreferences(), nowMillis = midday, zoneId = zone
        )
        assertTrue(result.suggestedAction.contains("Factura vencida"))
        assertTrue(result.suggestedAction.contains("atrasada"))
        // El compromiso no se calla aunque la tarea encabece: cola informativa.
        assertTrue(result.suggestedAction.contains("te llamo"))
    }

    @Test
    fun suggestedAction_huecoPasadoTomaPrecedenciaSobreCompromisoVencido() {
        // Tarea con hueco pasado (start pasado, due futuro) es accionable directamente;
        // el compromiso vencido queda como cola. El guardián prioriza lo que el usuario
        // puede resolver ahora mismo en una tarea ya capturada.
        val missed = TaskEntity(
            id = 1, title = "Llamar al banco", startAt = midday - 2 * 60 * 60_000L,
            dueAt = midday + 2 * 24 * 60 * 60_000L, durationMinutes = 30
        )
        val commitment = overdueCommitment(2, "enviar propuesta", midday - 2 * 86_400_000L)
        val result = GuardianEngine.snapshot(
            tasks = listOf(missed), commitments = listOf(commitment),
            habits = emptyList(), habitLogs = emptyList(),
            focusSessions = emptyList(), notes = emptyList(),
            preferences = UserPreferences(), nowMillis = midday, zoneId = zone
        )
        assertTrue(result.suggestedAction.contains("Llamar al banco"))
        assertTrue(result.suggestedAction.contains("hueco"))
        assertTrue(result.suggestedAction.contains("enviar propuesta"))
    }

    @Test
    fun suggestedAction_ignoraCompromisoFuturoORevisado() {
        // Un compromiso con dueAt futuro, o ya CONVERTED/DISMISSED, no es un olvido: no
        // debe disparar el nudge. Guard anti-falso-positivo.
        val future = overdueCommitment(1, "te llamo", midday + 86_400_000L)
        val converted = overdueCommitment(
            2, "enviar propuesta", midday - 86_400_000L, status = CommitmentReviewStatus.CONVERTED
        )
        val dismissed = overdueCommitment(
            3, "avisar a ana", midday - 86_400_000L, status = CommitmentReviewStatus.DISMISSED
        )
        val result = GuardianEngine.snapshot(
            tasks = emptyList(), commitments = listOf(future, converted, dismissed),
            habits = emptyList(), habitLogs = emptyList(),
            focusSessions = emptyList(), notes = emptyList(),
            preferences = UserPreferences(), nowMillis = midday, zoneId = zone
        )
        assertFalse(result.suggestedAction.contains("te llamo"))
        assertFalse(result.suggestedAction.contains("enviar propuesta"))
        assertFalse(result.suggestedAction.contains("avisar a ana"))
    }

    // --- suggestedAction: cola del 3.er olvido (capturas arrinconadas) ---
    // Asimetria con la cola del 4.o olvido (compromiso vencido): antes, un usuario con una
    // tarea atrasada Y varias ideas arrinconadas en la bandeja recibia el nudge de la
    // atrasada y NINGUNA senal de las capturas olvidadas -- la recuperacion proactiva del
    // stale-inbox solo disparaba cuando era la candidata #1, invisible en cualquier otra
    // rama. Ahora la cola informa del conteo para no mentir por omision.

    private fun staleCapture(id: Long, title: String) = TaskEntity(
        id = id, title = title, createdAt = midday - 8L * 24 * 60 * 60_000L
    )

    @Test
    fun suggestedAction_atrasadaIncluyeColaDeCapturasArrinconadas() {
        // Una atrasada (senal primaria) + dos capturas arrinconadas sin fecha: el nudge
        // nombra la atrasada y anade una cola con el conteo de capturas olvidadas. No las
        // nombra (no es doble senalizacion de accion), solo informa para empujar a revisar
        // la bandeja. Paridad con la cola de compromiso vencido.
        val overdue = TaskEntity(
            id = 1, title = "Factura vencida", dueAt = midday - 60 * 60_000L, durationMinutes = 20
        )
        val result = GuardianEngine.snapshot(
            tasks = listOf(overdue, staleCapture(2, "Idea A"), staleCapture(3, "Idea B")),
            commitments = emptyList(), habits = emptyList(), habitLogs = emptyList(),
            focusSessions = emptyList(), notes = emptyList(),
            preferences = UserPreferences(), nowMillis = midday, zoneId = zone
        )
        assertTrue(result.suggestedAction.contains("Factura vencida"))
        assertTrue(result.suggestedAction.contains("2 capturas"))
        assertTrue(result.suggestedAction.contains("bandeja"))
        // No nombra los titulos de las capturas: la accion primaria sigue siendo la atrasada.
        assertFalse(result.suggestedAction.contains("Idea A"))
        assertFalse(result.suggestedAction.contains("Idea B"))
    }

    @Test
    fun suggestedAction_huecoPasadoIncluyeColaDeCapturasArrinconadas() {
        // Misma cola para la rama de hueco pasado: la captura arrinconada no se calla aunque
        // el nudge encabece con una tarea con startAt incumplido. Singular cuando hay 1.
        val missed = TaskEntity(
            id = 1, title = "Llamar al banco", startAt = midday - 2 * 60 * 60_000L,
            dueAt = midday + 2 * 24 * 60 * 60_000L, durationMinutes = 30
        )
        val result = GuardianEngine.snapshot(
            tasks = listOf(missed, staleCapture(2, "Idea unica")),
            commitments = emptyList(), habits = emptyList(), habitLogs = emptyList(),
            focusSessions = emptyList(), notes = emptyList(),
            preferences = UserPreferences(), nowMillis = midday, zoneId = zone
        )
        assertTrue(result.suggestedAction.contains("Llamar al banco"))
        assertTrue(result.suggestedAction.contains("1 captura"))
        assertTrue(result.suggestedAction.contains("bandeja"))
    }


    @Test
    fun suggestedAction_compromisoVencidoIncluyeColaDeCapturasArrinconadas() {
        // Cuando la señal primaria es un compromiso vencido (4.º olvido) Y hay además
        // capturas arrinconadas en la bandeja (3.er olvido), estas no se callan: paridad
        // con las ramas de tarea atrasada/hueco pasado. Antes, un usuario con una promesa
        // vencida y varias ideas arrinconadas recibía el nudge del compromiso y NINGUNA
        // señal de las capturas olvidadas — mentir por omisión sobre el 3.er olvido.
        val commitment = overdueCommitment(1, "te llamo", midday - 2 * 86_400_000L)
        val result = GuardianEngine.snapshot(
            tasks = listOf(staleCapture(2, "Idea A"), staleCapture(3, "Idea B")),
            commitments = listOf(commitment),
            habits = emptyList(), habitLogs = emptyList(),
            focusSessions = emptyList(), notes = emptyList(),
            preferences = UserPreferences(), nowMillis = midday, zoneId = zone
        )
        assertTrue(result.suggestedAction.contains("te llamo"))
        assertTrue(result.suggestedAction.contains("2 capturas"))
        assertTrue(result.suggestedAction.contains("bandeja"))
        // No nombra los títulos de las capturas: la acción primaria sigue siendo el compromiso.
        assertFalse(result.suggestedAction.contains("Idea A"))
        assertFalse(result.suggestedAction.contains("Idea B"))
    }

    @Test
    fun suggestedAction_noAnadeColaDeCapturasCuandoNoHayArrinconadas() {
        // Una atrasada sola, sin capturas olvidadas: no se anade cola alguna. La cola es
        // exclusivamente informativa de olvido real; sin olvido, sin ruido.
        val overdue = TaskEntity(
            id = 1, title = "Factura vencida", dueAt = midday - 60 * 60_000L, durationMinutes = 20
        )
        val result = GuardianEngine.snapshot(
            tasks = listOf(overdue), commitments = emptyList(),
            habits = emptyList(), habitLogs = emptyList(),
            focusSessions = emptyList(), notes = emptyList(),
            preferences = UserPreferences(), nowMillis = midday, zoneId = zone
        )
        assertTrue(result.suggestedAction.contains("Factura vencida"))
        assertFalse(result.suggestedAction.contains("capturas"))
        assertFalse(result.suggestedAction.contains("bandeja"))
    }

    @Test
    fun suggestedAction_capturaFrescaNoCuentaComoArrinconada() {
        // Una captura de <7 dias NO es "olvidada": aunque haya una atrasada que encabece,
        // la fresca no debe inflar la cola. Confirma el umbral honesto de stale-inbox.
        val overdue = TaskEntity(
            id = 1, title = "Factura vencida", dueAt = midday - 60 * 60_000L, durationMinutes = 20
        )
        val fresh = TaskEntity(id = 2, title = "Idea reciente", createdAt = midday - 2 * 86_400_000L)
        val result = GuardianEngine.snapshot(
            tasks = listOf(overdue, fresh), commitments = emptyList(),
            habits = emptyList(), habitLogs = emptyList(),
            focusSessions = emptyList(), notes = emptyList(),
            preferences = UserPreferences(), nowMillis = midday, zoneId = zone
        )
        assertTrue(result.suggestedAction.contains("Factura vencida"))
        assertFalse(result.suggestedAction.contains("capturas"))
    }

    @Test
    fun suggestedAction_colasDeCompromisoYCapturasSeEncadenan() {
        // Ambos olvidos colaterales (compromiso vencido + capturas arrinconadas) coexisten
        // con una tarea atrasada: la accion primaria encabeza y ambas colas se anaden, sin
        // que ninguna se calle. Orden: tarea -> compromiso -> capturas.
        val overdue = TaskEntity(
            id = 1, title = "Factura vencida", dueAt = midday - 60 * 60_000L, durationMinutes = 20
        )
        val commitment = overdueCommitment(2, "te llamo", midday - 2 * 86_400_000L)
        val result = GuardianEngine.snapshot(
            tasks = listOf(overdue, staleCapture(3, "Idea A"), staleCapture(4, "Idea B")),
            commitments = listOf(commitment), habits = emptyList(), habitLogs = emptyList(),
            focusSessions = emptyList(), notes = emptyList(),
            preferences = UserPreferences(), nowMillis = midday, zoneId = zone
        )
        assertTrue(result.suggestedAction.contains("Factura vencida"))
        assertTrue(result.suggestedAction.contains("te llamo"))
        assertTrue(result.suggestedAction.contains("2 capturas"))
    }
}
