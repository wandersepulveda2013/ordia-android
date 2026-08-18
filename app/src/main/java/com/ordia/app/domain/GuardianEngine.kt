package com.ordia.app.domain

import com.ordia.app.data.local.CommitmentEntity
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
        zoneId: ZoneId = ZoneId.systemDefault(),
        commitments: List<CommitmentEntity> = emptyList()
    ): Snapshot {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        val today = now.toLocalDate()
        val completedSessions = focusSessions.filter { it.completed }
        val completedToday = TaskRules.completedTodayCount(tasks, nowMillis, zoneId)
        val focusMinutesToday = completedSessions
            .filter { DateRules.toLocalDate(it.startedAt, zoneId) == today }
            .sumOf { it.actualMinutes.coerceIn(0, MAX_FOCUS_MINUTES_PER_SESSION) }
        val habitsDoneToday = habits.count { habit ->
            HabitRules.isCompleted(HabitRules.countFor(habitLogs, habit.id, today), habit.targetPerPeriod)
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
            suggestedAction = suggestedAction(tasks, habits, commitments, completedToday, focusMinutesToday, habitsDoneToday, overdue, nowMillis, zoneId)
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

    /**
     * Nudge diario: una sola acción útil según el estado del día. La rama
     * "iniciar el día" solo aplica sin impulso real (sin tarea completada, <15 min
     * de enfoque y sin hábito hecho); con avance, invita a cerrar el círculo para
     * no enmarcar como "no empezado" un día que ya avanzó.
     *
     * Recuperación de los CUATRO olvidos de Ordía: el atraso de plazo
     * ([smallestOverdueAction]) y el hueco incumplido ([missedStartAction]) ya
     * tenían rama; la captura de bandeja arrinconada ([TaskRules.isStaleInbox]) y
     * el compromiso vencido de conversación ([overdueCommitmentAction],
     * [CommitmentRules.isOverduePending]) completan las cuatro. Sin el cuarto, un
     * día sin vencidas ni huecos pasados pero con una promesa vencida (dueAt
     * pasado, PENDING, sin convertir en tarea) recibía el nudge genérico "Completa
     * una tarea breve" aunque lo más útil era justamente nombrar ese compromiso —
     * asimetría con el asistente (c.286), que sí lo recupera. Aquí se cierra
     * simétricamente en la misma superficie existente, sin nueva pantalla.
     *
     * Precedencia: lo accionable directamente en una tarea ya capturada manda
     * (atrasada → hueco pasado), porque el usuario puede resolverlo ahora mismo.
     * El compromiso vencido va después: no es tarea hasta que se convierte, así
     * que su acción es guiar (revisar la conversación), no ejecutar a ciegas. El
     * stale-inbox es la señal más débil (sin plazo ni hueco) y va tras él.
     *
     * Cola informativa: cuando el nudge nombra una tarea (atrasada o hueco
     * pasado) Y hay además un compromiso vencido, este NO se calla — se añade
     * como cola para no mentir por omisión (paridad con c.286). No doble
     * señalización de acción: la acción primaria sigue siendo la tarea, pero el
     * compromiso vencido queda visible.
     */
    private fun suggestedAction(
        tasks: List<TaskEntity>,
        habits: List<HabitEntity>,
        commitments: List<CommitmentEntity>,
        completedToday: Int,
        focusMinutesToday: Int,
        habitsDoneToday: Int,
        overdue: Int,
        nowMillis: Long,
        zoneId: ZoneId
    ): String {
        val overdueCommitments = CommitmentRules.overduePendingSorted(commitments, nowMillis)
        val missed = missedStartAction(tasks, nowMillis)
        val hasActiveTask = tasks.any { TaskRules.isActive(it) }
        val noMomentumYet = focusMinutesToday < 15 && (habits.isEmpty() || habitsDoneToday == 0)
        // Las colas informativas (compromiso vencido + capturas arrinconadas) se
        // encadenan en orden de precedencia: la acción primaria es la tarea nombrada
        // (atrasada o hueco pasado) o, sin tareas olvidadas, el compromiso vencido;
        // tras ella, el 3.er olvido (capturas arrinconadas) siempre se añade para no
        // mentir por omisión sobre ninguna. Ver [withCommitmentTail] /
        // [withStaleInboxTail].
        return when {
            overdue > 0 -> {
                val overdueAction = smallestOverdueAction(tasks, nowMillis)
                if (overdueAction != null) {
                    // Cuántas atrasadas nombrables quedan tras la elegida (excluye
                    // "en curso" vía [actionableOverdueTasks]). Evita el olvido del
                    // resto del atraso: [withOverdueTail] no nombra títulos.
                    val actionable = actionableOverdueTasks(tasks, nowMillis)
                    val otherOverdue = actionable.size - 1
                    // "Vencida importante": de las atrasadas restantes, ¿cuántas son
                    // URGENTES? La elegida ya es la urgente de mayor prioridad (ver
                    // [smallestOverdueAction]), así que el atraso urgente RESTANTE es
                    // (total urgente − 1) acotado a 0. Sin esto, varias atrasadas
                    // urgentes se ocultaban tras un conteo plano: el nudge nombraba UNA
                    // y el resto del atraso crítico quedaba indistinguible del atraso
                    // banal. [withOverdueTail] lo señaliza sin abrir una 2.ª acción.
                    val urgentOther =
                        (actionable.count { it.priority == TaskPriority.URGENT } - 1).coerceAtLeast(0)
                    overdueAction
                        .withOverdueTail(otherOverdue, urgentOther)
                        .withCommitmentTail(overdueCommitments)
                        .withStaleInboxTail(tasks, nowMillis, zoneId)
                }
                // Si TODAS las atrasadas están en curso (no hay ninguna nombrable),
                // no insistir con "elige una atrasada" — el usuario ya la está
                // haciendo. En vez de cortar la cascada con un mensaje engañoso,
                // dejar caer a las siguientes señales de recuperación (hueco
                // olvidado, compromiso vencido, captura arrinconada) para no mentir
                // por omisión sobre otros olvidos reales. Es la partición natural:
                // `overdue` cuenta todas las atrasadas, pero `smallestOverdueAction`
                // excluye las en curso ([TaskRules.isBeingWorkedOn]); cuando la
                // diferencia es la lista entera, lo accionable está en otra señal.
                else when {
                    missed != null -> missed.withCommitmentTail(overdueCommitments)
                        .withStaleInboxTail(tasks, nowMillis, zoneId)
                    overdueCommitments.isNotEmpty() -> overdueCommitmentAction(overdueCommitments)
                        .withStaleInboxTail(tasks, nowMillis, zoneId)
                    // La acción primaria (reconocer la tarea en curso) ya está dicha; la cola
                    // del stale-inbox sólo INFORMA del conteo para no mentir por omisión del 3.er
                    // olvido, igual que las otras sub-ramas de overdue>0. Sin esta cola, un
                    // usuario con la atrasada en curso Y capturas arrinconadas recibía el
                    // mensaje de continuación y NINGUNA señal de las capturas olvidadas.
                    else -> "Estás trabajando en tu tarea atrasada ahora mismo. Sigue así y ciérrala cuando puedas."
                        .withStaleInboxTail(tasks, nowMillis, zoneId)
                }
            }
            missed != null -> missed.withCommitmentTail(overdueCommitments)
                .withStaleInboxTail(tasks, nowMillis, zoneId)
            overdueCommitments.isNotEmpty() -> overdueCommitmentAction(overdueCommitments)
                .withStaleInboxTail(tasks, nowMillis, zoneId)
            completedToday == 0 && hasActiveTask && noMomentumYet -> staleInboxAction(tasks, nowMillis, zoneId)
                ?: "Completa una tarea breve para iniciar el día con impulso."
            focusMinutesToday < 15 ->
                "Haz una sesión de enfoque de 15 minutos sin perseguir la perfección."
            habits.isNotEmpty() && habitsDoneToday == 0 ->
                "Registra un hábito sencillo para mantener la continuidad."
            completedToday == 0 && hasActiveTask ->
                "Ya avanzaste hoy: completa una tarea breve para cerrar el círculo."
            else -> "Tu cuidado diario está completo. Puedes descansar o avanzar por gusto."
        }
    }

    /**
     * Tercer olvido en el nudge del guardián: una captura de bandeja arrinconada
     * ([TaskRules.isStaleInbox], ≥[TaskRules.STALE_INBOX_DAYS_THRESHOLD] días sin
     * fecha). Simétrico a [smallestOverdueAction]/[missedStartAction] en cuanto
     * nombra UNA tarea concreta para recuperarla en la superficie existente, pero
     * delega en [TaskRules.nextBestTask] (como [GuardianCoach]) porque el
     * stale-inbox es la señal más débil y no debe robar el lugar de algo más
     * time-sensitive: solo retorna mensaje cuando la candidata elegida es ella
     * misma la captura olvidada. Devuelve `null` si no aplica (la rama llamadora
     * cae al nudge de iniciar el día).
     *
     * Elige la candidata que ya eligió nextBestTask y reusa su edad
     * ([TaskRules.inboxAgeDays] + [DateRules.ageLabel]) para un mensaje honesto y
     * sincronizado con el asistente. La acción propuesta (hacer/agendar/quitar) es
     * la misma decisión real que la tarjeta del coach, coherente entre las dos
     * superficies de recuperación. Solo tareas raíz activas, por
     * [TaskRules.isStaleInbox].
     */
    private fun staleInboxAction(tasks: List<TaskEntity>, nowMillis: Long, zoneId: ZoneId): String? {
        val next = TaskRules.nextBestTask(tasks, nowMillis, zoneId) ?: return null
        if (!TaskRules.isStaleInbox(next, nowMillis, zoneId)) return null
        val ageLabel = DateRules.ageLabel(TaskRules.inboxAgeDays(next, nowMillis, zoneId))
        return "«${next.title}» lleva $ageLabel en tu bandeja sin fecha. Hazla hoy, agéndala o quítala."
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
     * ([TaskRules.isBeingWorkedOn] — marcada en curso o dentro de su ventana): una
     * tarea vencida pero en curso (p. ej. empezada a tiempo, con `dueAt` ya pasado
     * pero dentro de su ventana de duración, o que el usuario marcó IN_PROGRESS a
     * mano) no debe presentarse como "hazla ya": el usuario ya la está haciendo, y
     * nombra en su lugar la siguiente atrasada más pequeña. Si todas las atrasadas
     * están en curso, devuelve `null`: la cascada de [suggestedAction] continúa con
     * las siguientes señales de recuperación (hueco olvidado, compromiso vencido,
     * captura arrinconada) en vez de cortar con un "elige una atrasada" que mentiría
     * sobre una tarea que ya se está haciendo. Mismo predicado "sacro en curso" que
     * AutomationActionPlanner e isMissedStart.
     */
    private fun smallestOverdueAction(tasks: List<TaskEntity>, nowMillis: Long): String? {
        val chosen = actionableOverdueTasks(tasks, nowMillis)
            .minWithOrNull(
                compareByDescending<TaskEntity> { it.priority == TaskPriority.URGENT }
                    .thenBy { TaskRules.plannedDuration(it) }
                    .thenBy { TaskRules.priorityScore(it.priority) }
                    .thenBy { it.dueAt ?: Long.MAX_VALUE }
                    .thenBy { it.id }
            )
            ?: return null
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

    /**
     * Subconjunto de tareas atrasadas realmente "nombrables" por el nudge: raíces
     * activas, atrasadas y NO en curso ([TaskRules.isBeingWorkedOn] — una atrasada
     * que el usuario ya está haciendo no es un olvido a recuperar). Es el mismo
     * filtro que [smallestOverdueAction] aplicaba inline; se extrae para que el
     * conteo de "otras atrasadas" de [withOverdueTail] use exactamente el mismo
     * criterio que la selección, sin duplicar el predicado ( fuente de drift si
     * las reglas de "sacro en curso" cambian ).
     */
    private fun actionableOverdueTasks(tasks: List<TaskEntity>, nowMillis: Long): List<TaskEntity> =
        tasks.filter {
            it.parentTaskId == null && TaskRules.isActive(it) &&
                TaskRules.isOverdue(it, nowMillis) &&
                !TaskRules.isBeingWorkedOn(it, nowMillis)
        }

    /**
     * Recupera un compromiso agendado cuyo hueco ya pasó sin atraso: el "olvido
     * silencioso" de [TaskRules.isMissedStart]. Cuando no hay nada atrasado (la rama
     * de [smallestOverdueAction] no aplicó) pero sí quedó una tarea con `startAt`
     * cuyo turno expiró, el guardián la nombra en la misma superficie existente (el
     * nudge diario), sin añadir pantallas. Es recuperación de tareas olvidadas en su
     * forma más útil: el plazo aún no voló, así que hacerla ahora o reprogramarla
     * evita el atraso.
     *
     * Selección idéntica a [smallestOverdueAction] (urgente → más pequeña →
     * prioridad → vencimiento → id) para que el criterio del nudge sea uno solo y
     * predecible entre ambas señales: lo urgente manda aunque no esté atrasado (un
     * compromiso urgente cuyo hueco se pasó es lo más crítico recuperable), y entre
     * iguales gana el "quick win" (la más corta) para romper la parálisis. Solo
     * tareas raíz activas, excluyendo las en curso a mano y las ya atrasadas
     * (partición de `isMissedStart`). Devuelve `null` si no hay ninguna: el nudge
     * cae a los mensajes genéricos siguientes.
     */
    private fun missedStartAction(tasks: List<TaskEntity>, nowMillis: Long): String? {
        val chosen = tasks
            .filter {
                it.parentTaskId == null && TaskRules.isMissedStart(it, nowMillis)
            }
            .minWithOrNull(
                compareByDescending<TaskEntity> { it.priority == TaskPriority.URGENT }
                    .thenBy { TaskRules.plannedDuration(it) }
                    .thenBy { TaskRules.priorityScore(it.priority) }
                    .thenBy { it.dueAt ?: Long.MAX_VALUE }
                    .thenBy { it.id }
            )
            ?: return null
        val minutes = TaskRules.plannedDuration(chosen)
        return if (chosen.priority == TaskPriority.URGENT) {
            "«${chosen.title}» tenía su hueco y se le pasó (~${minutes} min). Es urgente: hazla o reagéndala."
        } else {
            "«${chosen.title}» tenía su hueco y se le pasó (~${minutes} min). Hazla o reagéndala."
        }
    }

    /**
     * Cuarto olvido en el nudge del guardián: un compromiso vencido extraído de una
     * conversación ([CommitmentRules.isOverduePending] — dueAt pasado, PENDING, sin
     * convertir en tarea). Una promesa que se pasó de plazo es un olvido distinto de
     * los tres de tareas: no es tarea hasta que el usuario la convierte, así que la
     * acción es guiar (revisar la conversación), no ejecutar a ciegas — paridad con
     * el asistente (c.286), donde convertir/descartar es decisión del usuario sobre
     * su propia promesa.
     *
     * Nombra el compromiso más atrasado ([CommitmentRules.overduePendingSorted] ya
     * ordena por dueAt ascendente) en la misma superficie existente, sin añadir
     * pantallas. Cuando hay varios, nombra el más atrasado y cuenta el resto para
     * que el usuario sepa la magnitud sin ruido.
     */
    private fun overdueCommitmentAction(overdueCommitments: List<CommitmentEntity>): String {
        val worst = overdueCommitments.first()
        val actor = worst.actor.trim()
        val who = if (actor.isEmpty()) "" else "$actor "
        return if (overdueCommitments.size == 1) {
            "«${who}${worst.action}» es un compromiso vencido de una conversación. Revísalo en Conversaciones."
        } else {
            "«${who}${worst.action}» es un compromiso vencido de una conversación (y otros ${overdueCommitments.size - 1}). Revísalos en Conversaciones."
        }
    }

    /**
     * Cola informativa: cuando el nudge ya nombró una tarea olvidada (atrasada o con
     * hueco pasado) Y hay además un compromiso vencido, este no se calla. Paridad con
     * el asistente (c.286): la acción primaria sigue siendo la tarea, pero la promesa
     * vencida queda visible para no mentir por omisión. No doble señalización de
     * acción: sólo informa del compromiso más atrasado.
     */
    private fun String.withCommitmentTail(overdueCommitments: List<CommitmentEntity>): String {
        if (overdueCommitments.isEmpty()) return this
        val worst = overdueCommitments.first()
        val actor = worst.actor.trim()
        val who = if (actor.isEmpty()) "" else "$actor "
        return "$this Además, «${who}${worst.action}» es un compromiso vencido de una conversación."
    }

    /**
     * Cola informativa del 3.er olvido: cuando el nudge ya nombró una tarea (atrasada
     * o con hueco pasado) Y hay además capturas de bandeja arrinconadas
     * ([TaskRules.isStaleInbox], ≥[TaskRules.STALE_INBOX_DAYS_THRESHOLD] días sin
     * fecha ni hueco), estas no se callan. Cierra la asimetría con
     * [withCommitmentTail] (4.º olvido): antes, un usuario con una tarea atrasada y
     * seis ideas arrinconadas recibía el nudge de la atrasada y NINGUNA señal de las
     * seis capturas olvidadas — la recuperación proactiva del stale-inbox solo
     * disparaba cuando era la candidata #1 ([staleInboxAction]), invisible en
     * cualquier otra rama.
     *
     * No nombra títulos ni pide acción concreta (la acción primaria es la tarea ya
     * señalada): sólo informa del conteo para no mentir por omisión y empujar a
     * revisar la bandeja, igual que la cola de compromiso sólo "informa". La tarea
     * nombrada es mutuamente excluyente con [TaskRules.isStaleInbox] (ésta exige
     * `dueAt == null && startAt == null`, mientras que atrasadas/huecos tienen
     * `dueAt`/`startAt`), así que nunca se cuenta dos veces la misma.
     */
    private fun String.withStaleInboxTail(
        tasks: List<TaskEntity>,
        nowMillis: Long,
        zoneId: ZoneId
    ): String {
        val count = tasks.count {
            it.parentTaskId == null && TaskRules.isStaleInbox(it, nowMillis, zoneId)
        }
        if (count == 0) return this
        val capturas = if (count == 1) "1 captura" else "$count capturas"
        val llevan = if (count == 1) "lleva" else "llevan"
        return "$this Además, $capturas en la bandeja $llevan una semana o más sin agendar."
    }

    /**
     * Cola informativa del 5.º olvido: cuando el nudge nombra UNA tarea atrasada
     * ([smallestOverdueAction]) PERO hay MÁS atrasadas nombrables, las restantes no
     * se callan. Cierra la última asimetría de "mentir por omisión": antes, un
     * usuario con cinco tareas atrasadas recibía el nudge de la más importante y
     * NINGUNA señal de las otras cuatro — resolvía la nombrada y olvidaba que el
     * resto del atraso seguía ahí. Es la dirección "detección de vencidas
     * importantes" + "recuperación de tareas olvidadas": el nudge ya elige a la
     * mejor para arrancar, pero ahora también dice cuánto atraso queda detrás.
     *
     * Paridad con [withCommitmentTail] / [withStaleInboxTail]: sólo INFORMA del
     * conteo (no nombra títulos ni abre una segunda acción), para no romper la
     * regla "una sola acción primaria". El conteo excluye la tarea ya nombrada
     * (otherCount = nombrables − 1) y excluye las "en curso" —usa el mismo filtro
     * que la selección vía [actionableOverdueTasks]— porque una atrasada que el
     * usuario ya está haciendo no es un olvido a recuperar. Cuando sólo hay una,
     * no se añade nada (otherCount == 0): no decir "0 más".
     *
     * "Vencida importante" en la cola (c.621): además del conteo, señala cuántas
     * de las atrasadas restantes son URGENTES ([urgentCount]). Sin esto, un usuario
     * con la nombrada (urgente) MÁS otras 3 atrasadas urgentes leía "3 tareas más
     * atrasadas" sin distinguir que las 3 eran críticas: el atraso urgente quedaba
     * camuflado tras un conteo plano, justo cuando "detección de vencidas
     * importantes" más ayuda hace. La elegida ya es urgente si hay alguna urgente
     * (ver [smallestOverdueAction]); [urgentCount] son las urgentes RESTANTES, así
     * que nunca se cuenta la nombrada dos veces. Determinista, sin random, sin IA
     * fingida: sólo hace visible la prioridad que la tarea ya porta.
     */
    private fun String.withOverdueTail(otherCount: Int, urgentCount: Int = 0): String {
        if (otherCount <= 0) return this
        val tareas = if (otherCount == 1) "1 tarea más atrasada" else "$otherCount tareas más atrasadas"
        if (urgentCount <= 0) return "$this Además, tienes $tareas."
        val urgentes = if (urgentCount == 1) "1 urgente" else "$urgentCount urgentes"
        return "$this Además, tienes $tareas ($urgentes)."
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
