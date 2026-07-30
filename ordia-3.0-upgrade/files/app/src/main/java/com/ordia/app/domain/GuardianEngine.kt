package com.ordia.app.domain

import com.ordia.app.data.local.FocusSessionEntity
import com.ordia.app.data.local.HabitEntity
import com.ordia.app.data.local.HabitLogEntity
import com.ordia.app.data.local.NoteEntity
import com.ordia.app.data.local.TaskEntity
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
    enum class Stage(val label: String, val minimumXp: Int) {
        SPARK("Chispa", 0),
        HATCHLING("Cría", 180),
        YOUNG("Joven", 520),
        COMPANION("Compañero", 1_100),
        ASCENDED("Ascendido", 2_200)
    }

    enum class Mood(val label: String) {
        CALM("tranquilo"),
        HAPPY("feliz"),
        FOCUSED("concentrado"),
        SLEEPY("con sueño"),
        CURIOUS("curioso"),
        PROUD("orgulloso"),
        PLAYFUL("juguetón"),
        CONCERNED("preocupado")
    }

    enum class Archetype(val label: String, val description: String) {
        BALANCED("Equilibrado", "Combina organización, constancia, ideas y descanso."),
        ACHIEVER("Impulsor", "Crece principalmente al terminar tareas y proyectos."),
        FOCUSED("Centinela", "Su forma refleja sesiones largas de concentración."),
        CONSISTENT("Guardián de rachas", "Evoluciona con hábitos repetidos y rutinas sostenibles."),
        CREATIVE("Explorador", "Se fortalece al capturar notas, ideas y conocimiento.")
    }

    enum class Interaction(val label: String, val bond: Int, val event: String) {
        PET("Acariciar", 3, "pet"),
        PLAY("Jugar", 5, "play"),
        FEED("Dar energía", 4, "feed"),
        TALK("Conversar", 4, "talk"),
        REST("Descansar", 2, "rest")
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
            !task.archived && task.completedAt?.let { DateRules.toLocalDate(it, zoneId) == today } == true
        }
        val focusMinutesToday = completedSessions
            .filter { DateRules.toLocalDate(it.startedAt, zoneId) == today }
            .sumOf { it.actualMinutes.coerceIn(0, MAX_FOCUS_MINUTES_PER_SESSION) }
        val habitsDoneToday = habits.count { habit ->
            HabitRules.countFor(habitLogs, habit.id, today) >= habit.targetPerPeriod.coerceAtLeast(1)
        }
        val activeNotes = notes.count { !it.archived }
        val completedAll = tasks.count { it.completed && !it.archived }
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
        val overdue = tasks.count { !it.completed && !it.archived && TaskRules.isOverdue(it, nowMillis) }
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
            suggestedAction = suggestedAction(tasks, habits, completedToday, focusMinutesToday, habitsDoneToday, overdue)
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
        val completedTasks = tasks.count { it.completed && !it.archived }
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
        overdue: Int
    ): String = when {
        overdue > 0 -> "Elige una tarea atrasada pequeña y decide: hacerla, moverla o archivarla."
        completedToday == 0 && tasks.any { !it.completed && !it.archived } -> "Completa una tarea breve para iniciar el día con impulso."
        focusMinutesToday < 15 -> "Haz una sesión de enfoque de 15 minutos sin perseguir la perfección."
        habits.isNotEmpty() && habitsDoneToday == 0 -> "Registra un hábito sencillo para mantener la continuidad."
        else -> "Tu cuidado diario está completo. Puedes descansar o avanzar por gusto."
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
