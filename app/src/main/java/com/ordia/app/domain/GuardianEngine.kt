package com.ordia.app.domain

import com.ordia.app.data.local.FocusSessionEntity
import com.ordia.app.data.local.HabitEntity
import com.ordia.app.data.local.HabitLogEntity
import com.ordia.app.data.local.NoteEntity
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.data.preferences.GuardianSpecies
import com.ordia.app.data.preferences.PreferencesRepository
import com.ordia.app.data.preferences.UserPreferences
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure rules for Ordia's virtual guardian system.
 *
 * Activity experience is derived from real records so restoring a backup never loses progress.
 * Persisted experience mirrors the highest derived value for surfaces that cannot load Room
 * directly (for example, the floating overlay). Bond contributes a limited bonus: interaction is
 * meaningful, but repeatedly tapping the guardian can never replace real progress in Ordia.
 */
object GuardianEngine {
    enum class Stage(val minimumXp: Int) {
        SPARK(0),
        HATCHLING(180),
        YOUNG(520),
        COMPANION(1_100),
        ASCENDED(2_200)
    }

    enum class Mood {
        CALM,
        HAPPY,
        FOCUSED,
        SLEEPY,
        CURIOUS,
        PROUD,
        PLAYFUL,
        CONCERNED
    }

    enum class Archetype {
        BALANCED,
        ACHIEVER,
        FOCUSED,
        CONSISTENT,
        CREATIVE
    }

    enum class Interaction(val bond: Int, val event: String) {
        PET(3, "pet"),
        PLAY(5, "play"),
        FEED(4, "feed"),
        TALK(4, "talk"),
        REST(2, "rest")
    }

    data class Snapshot(
        val name: String,
        val species: GuardianSpecies,
        val stage: Stage,
        val nextStage: Stage?,
        val mood: Mood,
        val experience: Int,
        val activityExperience: Int,
        val bondExperience: Int,
        val experienceToNext: Int,
        val level: Int,
        val progressToNext: Float,
        val bond: Int,
        val energy: Int,
        val archetype: Archetype,
        val interactionsRemaining: Int,
        val message: String,
        val completedToday: Int,
        val focusMinutesToday: Int,
        val habitsDoneToday: Int,
        val dailyGoalsCompleted: Int,
        val dailyGoalsTotal: Int,
        val overdue: Int,
        val suggestedAction: String
    )

    fun snapshot(
        tasks: List<TaskEntity>,
        habits: List<HabitEntity>,
        habitLogs: List<HabitLogEntity>,
        focusSessions: List<FocusSessionEntity>,
        notes: List<NoteEntity>,
        preferences: UserPreferences,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Snapshot {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        val today = now.toLocalDate()
        val completedSessions = focusSessions.filter { it.completed }
        val completedToday = tasks.count { task ->
            task.parentTaskId == null && !task.archived &&
                task.completedAt?.let { DateRules.toLocalDate(it, zoneId) == today } == true
        }
        val focusMinutesToday = completedSessions
            .filter { DateRules.toLocalDate(it.startedAt, zoneId) == today }
            .sumOf { it.actualMinutes.coerceIn(0, MAX_FOCUS_MINUTES_PER_SESSION) }
        val habitsDoneToday = habits.count { habit ->
            HabitRules.countFor(habitLogs, habit.id, today) >= habit.targetPerPeriod.coerceAtLeast(1)
        }
        val activeNotes = notes.count { !it.archived }
        val completedAll = TaskRules.completedRootCount(tasks)
        val streakPower = habits.sumOf { HabitRules.currentStreak(it, habitLogs).coerceIn(0, MAX_STREAK_DAYS_PER_HABIT) }
        val completedFocusMinutes = completedSessions.sumOf {
            it.actualMinutes.coerceIn(0, MAX_FOCUS_MINUTES_PER_SESSION)
        }

        val derivedExperience = activityExperience(
            completedTasks = completedAll,
            completedFocusMinutes = completedFocusMinutes,
            streakPower = streakPower,
            activeNotes = activeNotes
        )
        val persistedActivityExperience = maxOf(derivedExperience, preferences.guardianExperience)
            .coerceIn(0, MAX_EXPERIENCE)
        val bondExperience = bondExperience(preferences.guardianBond)
        val experience = effectiveExperience(
            derivedExperience = derivedExperience,
            persistedExperience = preferences.guardianExperience,
            bond = preferences.guardianBond
        )

        val archetype = when {
            completedFocusMinutes >= maxOf(60, completedAll * 18) -> Archetype.FOCUSED
            streakPower >= 45 -> Archetype.CONSISTENT
            activeNotes >= maxOf(3, completedAll) -> Archetype.CREATIVE
            completedAll >= 12 -> Archetype.ACHIEVER
            else -> Archetype.BALANCED
        }

        val stage = stageForExperience(experience)
        val next = Stage.entries.getOrNull(stage.ordinal + 1)
        val progress = if (next == null) 1f else {
            val span = (next.minimumXp - stage.minimumXp).coerceAtLeast(1)
            ((experience - stage.minimumXp).toFloat() / span).coerceIn(0f, 1f)
        }
        val level = (1 + experience / 100).coerceAtMost(999)
        val overdue = tasks.count {
            it.parentTaskId == null && TaskRules.isOverdue(it, nowMillis)
        }
        val recentInteraction = preferences.guardianLastInteraction > 0L &&
            nowMillis - preferences.guardianLastInteraction in 0 until RECENT_INTERACTION_MILLIS
        val mood = when {
            now.hour >= 23 || now.hour <= 5 -> Mood.SLEEPY
            completedToday >= 5 || habitsDoneToday >= 3 -> Mood.PROUD
            focusMinutesToday >= 25 -> Mood.FOCUSED
            overdue >= 5 -> Mood.CONCERNED
            preferences.guardianLastEvent == "play" && recentInteraction -> Mood.PLAYFUL
            preferences.guardianLastEvent == "rest" && recentInteraction -> Mood.SLEEPY
            preferences.guardianLastEvent == "progress" && recentInteraction -> Mood.PROUD
            recentInteraction -> Mood.HAPPY
            completedToday == 0 && focusMinutesToday == 0 -> Mood.CURIOUS
            else -> Mood.CALM
        }
        val inactiveHours = if (preferences.guardianLastInteraction <= 0L) 0 else {
            ((nowMillis - preferences.guardianLastInteraction).coerceAtLeast(0L) / 3_600_000L).toInt()
        }
        val recentCareBoost = if (recentInteraction) {
            when (preferences.guardianLastEvent) {
                "feed" -> 14
                "rest" -> 10
                "play" -> 5
                "talk" -> 3
                "pet" -> 2
                "progress" -> 4
                else -> 0
            }
        } else {
            0
        }
        val energy = (
            82 + completedToday * 3 + habitsDoneToday * 4 + recentCareBoost - inactiveHours.coerceAtMost(48)
            ).coerceIn(18, 100)
        val interactionsToday = if (preferences.guardianInteractionEpochDay == today.toEpochDay()) {
            preferences.guardianInteractionsToday
        } else {
            0
        }
        val dailyGoalsCompleted = listOf(
            completedToday >= 1,
            focusMinutesToday >= 15,
            habits.isEmpty() || habitsDoneToday >= 1
        ).count { it }

        return Snapshot(
            name = preferences.guardianName.ifBlank { preferences.guardianSpecies.defaultName },
            species = preferences.guardianSpecies,
            stage = stage,
            nextStage = next,
            mood = mood,
            experience = experience,
            activityExperience = persistedActivityExperience,
            bondExperience = bondExperience,
            experienceToNext = next?.let { (it.minimumXp - experience).coerceAtLeast(0) } ?: 0,
            level = level,
            progressToNext = progress,
            bond = preferences.guardianBond.coerceIn(0, 9_999),
            energy = energy,
            archetype = archetype,
            interactionsRemaining = (PreferencesRepository.DAILY_INTERACTION_LIMIT - interactionsToday).coerceAtLeast(0),
            message = message(
                mood = mood,
                completed = completedToday,
                focus = focusMinutesToday,
                overdue = overdue,
                event = preferences.guardianLastEvent,
                recentInteraction = recentInteraction
            ),
            completedToday = completedToday,
            focusMinutesToday = focusMinutesToday,
            habitsDoneToday = habitsDoneToday,
            dailyGoalsCompleted = dailyGoalsCompleted,
            dailyGoalsTotal = 3,
            overdue = overdue,
            suggestedAction = suggestedAction(tasks, habits, completedToday, focusMinutesToday, habitsDoneToday, overdue, nowMillis)
        )
    }

    fun stageForExperience(experience: Int): Stage =
        Stage.entries.last { experience.coerceAtLeast(0) >= it.minimumXp }

    /** A bounded contribution that makes care useful without allowing interaction-only evolution. */
    fun bondExperience(bond: Int): Int = (bond.coerceIn(0, 9_999) / 4).coerceAtMost(MAX_BOND_EXPERIENCE)

    fun effectiveExperience(derivedExperience: Int, persistedExperience: Int, bond: Int): Int =
        (maxOf(derivedExperience, persistedExperience).coerceAtLeast(0) + bondExperience(bond))
            .coerceIn(0, MAX_EXPERIENCE)

    fun derivedExperience(
        tasks: List<TaskEntity>,
        habits: List<HabitEntity>,
        habitLogs: List<HabitLogEntity>,
        focusSessions: List<FocusSessionEntity>,
        notes: List<NoteEntity>
    ): Int {
        val completedTasks = TaskRules.completedRootCount(tasks)
        val completedFocusMinutes = focusSessions
            .asSequence()
            .filter { it.completed }
            .sumOf { it.actualMinutes.coerceIn(0, MAX_FOCUS_MINUTES_PER_SESSION) }
        val streakPower = habits.sumOf { HabitRules.currentStreak(it, habitLogs).coerceIn(0, MAX_STREAK_DAYS_PER_HABIT) }
        val activeNotes = notes.count { !it.archived }
        return activityExperience(completedTasks, completedFocusMinutes, streakPower, activeNotes)
    }

    fun isQuietHours(startMinutes: Int, endMinutes: Int, currentMinutes: Int): Boolean {
        val start = startMinutes.coerceIn(0, 1439)
        val end = endMinutes.coerceIn(0, 1439)
        val current = currentMinutes.coerceIn(0, 1439)
        if (start == end) return false
        return if (start < end) current in start until end else current >= start || current < end
    }

    internal fun activityExperience(
        completedTasks: Int,
        completedFocusMinutes: Int,
        streakPower: Int,
        activeNotes: Int
    ): Int = (
        completedTasks.coerceAtLeast(0) * 12 +
            completedFocusMinutes.coerceAtLeast(0) +
            streakPower.coerceAtLeast(0) * 4 +
            activeNotes.coerceAtLeast(0) * 2
        ).coerceIn(0, MAX_EXPERIENCE)

    private fun suggestedAction(
        tasks: List<TaskEntity>,
        habits: List<HabitEntity>,
        completedToday: Int,
        focusMinutesToday: Int,
        habitsDoneToday: Int,
        overdue: Int,
        nowMillis: Long
    ): String = when {
        overdue > 0 -> smallestOverdueAction(tasks, nowMillis)
        completedToday == 0 && tasks.any { TaskRules.isActive(it) } -> "Completa una tarea breve para iniciar el día con impulso."
        focusMinutesToday < 15 -> "Haz una sesión de enfoque de 15 minutos sin perseguir la perfección."
        habits.isNotEmpty() && habitsDoneToday == 0 -> "Registra un hábito sencillo para mantener la continuidad."
        else -> "Tu cuidado diario está completo. Puedes descansar o avanzar por gusto."
    }

    /**
     * Cuando hay tareas atrasadas, el guardián nombra una concreta en vez de un
     * consejo genérico: recupera una tarea olvidada en la superficie que ya
     * existe (el nudge diario), sin añadir pantallas.
     *
     * Selección de la tarea a nombrar —dos señales que cooperan en vez de
     * contradecirse:
     * 1. Si hay alguna atrasada URGENTE, esa gana siempre. Un plazo crítico que
     *    se está pasando es la "vencida importante": el nudge no debe alejar al
     *    usuario hacia algo más rápido pero irrelevante mientras lo urgente sigue
     *    vencido. Es coherente con [TaskRules.timeRank]/[nextBestTask], donde lo
     *    atrasado urgente manda, y con la dirección "detección de vencidas
     *    importantes".
     * 2. Entre las atrasadas no urgentes (o entre las urgentes entre sí), el
     *    "quick win" sigue vigente: se nombra la más pequeña (por
     *    [TaskRules.plannedDuration]) para reducir la fricción de arrancar y
     *    romper la parálisis.
     *
     * Orden determinista (urgencia → duración → prioridad → vencimiento → id)
     * para que dos ejecuciones idénticas nombren la misma tarea. Solo se
     * consideran tareas raíz, no completadas ni archivadas, iguales que el
     * conteo de `overdue`.
     *
     * Excluye las tareas que se están ejecutando justo ahora
     * ([TaskRules.isInProgressNow]): una tarea vencida pero en curso (p. ej.
     * empezada a tiempo, con `dueAt` ya pasado pero dentro de su ventana de
     * duración) no debe presentarse como "hazla ya": el usuario ya la está
     * haciendo, y nombra en su lugar la siguiente atrasada más pequeña. Si
     * todas las atrasadas están en curso, cae al mensaje genérico.
     */
    private fun smallestOverdueAction(tasks: List<TaskEntity>, nowMillis: Long): String {
        val chosen = tasks
            .filter {
                it.parentTaskId == null && TaskRules.isActive(it) &&
                    TaskRules.isOverdue(it, nowMillis) &&
                    !TaskRules.isInProgressNow(it, nowMillis)
            }
            .minWithOrNull(
                compareByDescending<TaskEntity> { it.priority == TaskPriority.URGENT }
                    .thenBy { TaskRules.plannedDuration(it) }
                    .thenBy { TaskRules.priorityScore(it.priority) }
                    .thenBy { it.dueAt ?: Long.MAX_VALUE }
                    .thenBy { it.id }
            )
            ?: return "Elige una tarea atrasada y decide: hacerla, moverla o archivarla."
        val minutes = TaskRules.plannedDuration(chosen)
        // Cuando lo atrasado es urgente, el nudge lo dice: la señal de "plazo crítico
        // vencido" es justo lo que el usuario necesita oír para no dejarlo pasar. Es
        // una descripción honesta de la prioridad real de la tarea, no una etiqueta
        // interna.
        return if (chosen.priority == TaskPriority.URGENT) {
            "«${chosen.title}» está atrasada y es urgente (~${minutes} min). Hazla, muévela o archívala."
        } else {
            "«${chosen.title}» está atrasada (~${minutes} min). Hazla, muévela o archívala."
        }
    }

    private fun message(
        mood: Mood,
        completed: Int,
        focus: Int,
        overdue: Int,
        event: String,
        recentInteraction: Boolean
    ): String {
        if (recentInteraction) {
            when (event) {
                "feed" -> return "¡Gracias! Recuperé energía para acompañarte en el siguiente paso."
                "rest" -> return "Bajemos el ritmo un momento. Descansar también sostiene el progreso."
                "talk" -> return "Te escucho. No tienes que resolverlo todo de una sola vez."
                "pet" -> return "Nuestro vínculo crece con estos pequeños momentos."
                "play" -> return "¡Eso estuvo divertido! Ahora tengo curiosidad por tu próxima aventura."
                "progress" -> return "Noté tu avance. Cada acción real está transformando mi historia."
            }
        }
        return when (mood) {
            Mood.PROUD -> "¡Mira todo lo que avanzaste hoy! Estoy creciendo contigo."
            Mood.FOCUSED -> "Tu concentración me da energía. Sigamos con una sola cosa a la vez."
            Mood.CONCERNED -> "Hay $overdue pendientes atrasados. Elegimos uno pequeño y empezamos juntos."
            Mood.PLAYFUL -> "¡Eso estuvo divertido! Ahora tengo curiosidad por tu próxima aventura."
            Mood.HAPPY -> "Me alegra verte. Cada pequeño avance fortalece nuestro vínculo."
            Mood.SLEEPY -> "La noche también es parte del progreso. Podemos bajar el ritmo."
            Mood.CURIOUS -> "¿Qué te gustaría lograr hoy? Puedo acompañarte desde el primer paso."
            Mood.CALM -> when {
                completed > 0 -> "Ya completaste $completed hoy. No hace falta correr para seguir avanzando."
                focus > 0 -> "Llevas $focus minutos de enfoque. Tu constancia ya está dejando huella."
                else -> "Estoy aquí. Podemos empezar con algo sencillo cuando estés listo."
            }
        }
    }

    private const val MAX_EXPERIENCE = 100_000
    private const val MAX_BOND_EXPERIENCE = 500
    private const val MAX_FOCUS_MINUTES_PER_SESSION = 180
    private const val MAX_STREAK_DAYS_PER_HABIT = 30
    private const val RECENT_INTERACTION_MILLIS = 45 * 60 * 1000L
}
