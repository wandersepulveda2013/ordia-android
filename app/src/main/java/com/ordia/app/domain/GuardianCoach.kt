package com.ordia.app.domain

import com.ordia.app.data.local.CommitmentEntity
import com.ordia.app.data.local.CommitmentReviewStatus
import com.ordia.app.data.local.HabitEntity
import com.ordia.app.data.local.HabitLogEntity
import com.ordia.app.data.local.ProjectEntity
import com.ordia.app.data.local.ProjectStatus
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskStatus
import java.time.ZoneId

/**
 * Guardianes 3.0 (ORD-G3): observa la bandeja, los atrasos, la carga, los
 * proyectos, los compromisos y las rutinas para sugerir una sola acción útil,
 * siempre sin lenguaje de culpa y con una salida clara (descartar).
 */
object GuardianCoach {
    enum class Tone { CALM, FOCUSED, CELEBRATING, GENTLE }

    /** Tipos de observación del Guardián, para que la UI decida qué acciones ofrecer. */
    enum class Kind {
        OVERDUE,
        URGENT_TODAY,
        INBOX_CLUTTER,
        OVERLOAD,
        STALE_PROJECT,
        UPCOMING_COMMITMENT,
        PROCRASTINATION,
        FORGOTTEN_HABIT,
        NEXT_STEP,
        CELEBRATION,
        CALM
    }

    data class Insight(
        val kind: Kind = Kind.CALM,
        val eyebrow: String,
        val title: String,
        val message: String,
        val taskId: Long? = null,
        val tone: Tone = Tone.CALM,
        /** False solo para el mensaje genérico de calma, ya cubierto por el What Now. */
        val showOnHome: Boolean = true
    ) {
        /** Identidad estable para deduplicar descartes en memoria. */
        val dismissKey: String get() = "$kind|$eyebrow|$taskId|$title"
    }

    private const val INBOX_CLUTTER_THRESHOLD = 6
    private const val OVERLOAD_THRESHOLD = 6
    private const val COMMITMENT_WINDOW_DAYS = 3L
    private const val STALE_PROJECT_DAYS = 14L
    private const val PROCRASTINATION_DELAY_DAYS = 2L
    private const val FORGOTTEN_HABIT_DAYS = 3L

    fun insight(
        tasks: List<TaskEntity>,
        habits: List<HabitEntity>,
        habitLogs: List<HabitLogEntity>,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        projects: List<ProjectEntity> = emptyList(),
        commitments: List<CommitmentEntity> = emptyList()
    ): Insight {
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val roots = tasks.filter { it.parentTaskId == null && !it.archived && it.status != TaskStatus.CANCELLED }
        val pending = roots.filterNot { it.completed }
        val overdue = pending.filter { TaskRules.isOverdue(it, now) }
        val dueToday = pending.filter { TaskRules.isDueToday(it, now, zone) && !TaskRules.isOverdue(it, now) }
        val completedToday = roots.count { it.completed && it.completedAt?.let { time -> java.time.Instant.ofEpochMilli(time).atZone(zone).toLocalDate() == today } == true }

        if (overdue.isNotEmpty()) {
            postponed(tasks, now)?.let { task ->
                return Insight(
                    Kind.PROCRASTINATION,
                    "SIN ATRASCARTE",
                    task.title,
                    "Esta tarea se ha pospuesto varias veces. Divídela en pasos pequeños, reprograma o suéltala.",
                    task.id,
                    Tone.GENTLE
                )
            }
            val next = TaskRules.nextBestTask(overdue, now)
            return Insight(
                Kind.OVERDUE,
                "RECUPERA EL CONTROL",
                next?.title ?: "Hay algo pendiente",
                if (overdue.size == 1) "Esta tarea está atrasada. Empieza con un bloque corto." else "Tienes ${overdue.size} tareas atrasadas. Comienza por esta.",
                next?.id,
                Tone.GENTLE
            )
        }
        val urgent = dueToday.filter { it.priority.name == "URGENT" || it.priority.name == "HIGH" }
        if (urgent.isNotEmpty()) {
            val next = TaskRules.nextBestTask(urgent, now)
            return Insight(
                Kind.URGENT_TODAY,
                "PROTEGE TU DÍA",
                next?.title ?: "Prioridad de hoy",
                "Reserva tiempo para lo más importante antes de llenar la agenda.",
                next?.id,
                Tone.FOCUSED
            )
        }
        val inbox = pending.filter { it.status == TaskStatus.INBOX }
        if (inbox.size >= INBOX_CLUTTER_THRESHOLD) {
            return Insight(
                Kind.INBOX_CLUTTER,
                "TU BANDEJA SE ACUMULA",
                "Hay ${inbox.size} cosas sin organizar",
                "Ordía puede organizarlas por fecha e importancia. Revisa y decide qué vale la pena.",
                tone = Tone.CALM
            )
        }
        val todayLoad = overdue.size + dueToday.size
        if (todayLoad >= OVERLOAD_THRESHOLD) {
            return Insight(
                Kind.OVERLOAD,
                "DÍA CARGADO",
                "$todayLoad tareas para hoy",
                "Protege las esenciales y mueve o replanifica el resto sin culpa.",
                tone = Tone.CALM
            )
        }
        upcomingCommitment(commitments, now, zone)?.let { (actor, action, due) ->
            return Insight(
                Kind.UPCOMING_COMMITMENT,
                "COMPROMISO PRÓXIMO",
                action,
                buildString {
                    append("Tienes un compromiso")
                    if (actor.isNotBlank()) append(" con $actor")
                    append(due?.let { " cerca del ${DateRules.formatDate(it)}" } ?: "")
                    append(".")
                },
                tone = Tone.FOCUSED
            )
        }
        staleProject(tasks, projects, now)?.let { (project, openTasks) ->
            val task = TaskRules.nextBestTask(openTasks, now)
            return Insight(
                Kind.STALE_PROJECT,
                "PROYECTO EN PAUSA",
                project.name,
                "Lleva tiempo sin avanzar. Elige un siguiente paso pequeño para retomarlo.",
                task?.id,
                Tone.CALM
            )
        }
        forgottenHabit(habits, habitLogs, today)?.let { habit ->
            return Insight(
                Kind.FORGOTTEN_HABIT,
                "UN RITUAL PARA RETOMAR",
                habit.title,
                "No lo has hecho en varios días. Un bloque corto basta para retomar el ritmo.",
                tone = Tone.CALM
            )
        }
        TaskRules.nextBestTask(pending, now)?.let {
            return Insight(
                Kind.NEXT_STEP,
                "SIGUIENTE PASO",
                it.title,
                it.details.takeIf(String::isNotBlank) ?: "Ordía la priorizó por fecha, importancia y estado.",
                it.id,
                Tone.FOCUSED
            )
        }
        habits.firstOrNull { HabitRules.isScheduled(it, today) && HabitRules.countFor(habitLogs, it.id, today) < it.targetPerPeriod }?.let {
            return Insight(
                Kind.FORGOTTEN_HABIT,
                "UN PEQUEÑO RITUAL",
                it.title,
                "Tu lista está despejada. Este hábito puede cerrar el día con intención.",
                tone = Tone.CALM
            )
        }
        if (completedToday > 0) return Insight(
            Kind.CELEBRATION,
            "BIEN HECHO",
            "Tu día está en orden",
            "Completaste $completedToday ${if (completedToday == 1) "tarea" else "tareas"} hoy.",
            tone = Tone.CELEBRATING
        )
        return Insight(
            Kind.CALM,
            "TODO EN CALMA",
            "No hay pendientes inmediatos",
            "Captura una idea, revisa un proyecto o conserva este espacio libre.",
            tone = Tone.CALM,
            showOnHome = false
        )
    }

    private fun upcomingCommitment(
        commitments: List<CommitmentEntity>,
        now: Long,
        zone: ZoneId
    ): Triple<String, String, Long?>? {
        val horizon = now + COMMITMENT_WINDOW_DAYS * 86_400_000L
        return commitments
            .filter { it.reviewStatus == CommitmentReviewStatus.PENDING }
            .filter { it.dueAt != null && it.dueAt in now..horizon }
            .sortedBy { it.dueAt }
            .firstOrNull()
            ?.let { Triple(it.actor, it.action, it.dueAt) }
    }

    private fun staleProject(
        tasks: List<TaskEntity>,
        projects: List<ProjectEntity>,
        now: Long
    ): Pair<ProjectEntity, List<TaskEntity>>? {
        val projectTasks = tasks
            .filter { it.projectId != null && !it.archived && it.status != TaskStatus.CANCELLED }
            .groupBy { it.projectId!! }
        return projects
            .asSequence()
            .filter { it.status == ProjectStatus.ACTIVE && !it.archived }
            .mapNotNull { project ->
                val open = projectTasks[project.id]?.filterNot { it.completed }.orEmpty()
                if (open.isEmpty()) return@mapNotNull null
                val recentlyActive = project.updatedAt > now - STALE_PROJECT_DAYS * 86_400_000L ||
                    open.any { it.updatedAt > now - STALE_PROJECT_DAYS * 86_400_000L }
                if (recentlyActive) null else project to open
            }
            .sortedByDescending { it.second.size }
            .firstOrNull()
    }

    private fun postponed(tasks: List<TaskEntity>, now: Long): TaskEntity? {
        val window = PROCRASTINATION_DELAY_DAYS * 86_400_000L
        return tasks
            .asSequence()
            .filter { !it.completed && !it.archived && it.status != TaskStatus.CANCELLED }
            .filter { it.dueAt != null && it.dueAt < now - window }
            .filter { it.updatedAt != null && it.updatedAt > it.dueAt!! + window }
            .maxByOrNull { it.updatedAt - it.dueAt!! }
    }

    private fun forgottenHabit(
        habits: List<HabitEntity>,
        habitLogs: List<HabitLogEntity>,
        today: java.time.LocalDate
    ): HabitEntity? = habits
        .asSequence()
        .filter { !it.archived }
        .filter { habit ->
            val lastDone = habitLogs
                .filter { it.habitId == habit.id }
                .maxOfOrNull { it.epochDay }
            lastDone != null && today.toEpochDay() - lastDone >= FORGOTTEN_HABIT_DAYS
        }
        .maxByOrNull { habit ->
            today.toEpochDay() - (habitLogs
                .filter { it.habitId == habit.id }
                .maxOfOrNull { it.epochDay } ?: 0L)
        }
}
