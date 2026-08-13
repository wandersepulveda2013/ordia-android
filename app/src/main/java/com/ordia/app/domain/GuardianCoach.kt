package com.ordia.app.domain

import com.ordia.app.data.local.HabitEntity
import com.ordia.app.data.local.HabitLogEntity
import com.ordia.app.data.local.TaskEntity
import java.time.ZoneId

/**
 * Small, deterministic coaching layer used by the guardian.
 *
 * It intentionally does not use a remote model: the same local state always produces the same
 * recommendation, which keeps the feature private, predictable and testable.
 */
object GuardianCoach {
    enum class Tone { CALM, FOCUSED, CELEBRATING, GENTLE }

    data class Insight(
        val eyebrow: String,
        val title: String,
        val message: String,
        val taskId: Long? = null,
        val tone: Tone = Tone.CALM
    )

    fun insight(
        tasks: List<TaskEntity>,
        habits: List<HabitEntity>,
        habitLogs: List<HabitLogEntity>,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Insight {
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val roots = tasks.filter { it.parentTaskId == null && !it.archived }
        val pending = roots.filterNot { it.completed }
        val overdue = pending.filter { TaskRules.isOverdue(it, now) }
        val dueToday = pending.filter { TaskRules.isDueToday(it, now, zone) }
        val completedToday = roots.count { task ->
            task.completed && task.completedAt?.let {
                java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == today
            } == true
        }

        if (overdue.isNotEmpty()) {
            val next = TaskRules.nextBestTask(overdue, now)
            if (next != null) {
                return Insight(
                    eyebrow = "RECUPERA EL CONTROL",
                    title = next.title,
                    message = buildReason(next, "Atrasada", overdue.size),
                    taskId = next.id,
                    tone = Tone.GENTLE
                )
            }
        }

        val urgentToday = dueToday.filter { it.priority.name == "URGENT" || it.priority.name == "HIGH" }
        if (urgentToday.isNotEmpty()) {
            val next = TaskRules.nextBestTask(urgentToday, now)
            if (next != null) {
                return Insight(
                    eyebrow = "PROTEGE TU DÍA",
                    title = next.title,
                    message = buildReason(next, "Prioridad alta para hoy"),
                    taskId = next.id,
                    tone = Tone.FOCUSED
                )
            }
        }

        val next = TaskRules.nextBestTask(pending, now)
        if (next != null) {
            val context = if (TaskRules.isDueToday(next, now, zone)) "Vence hoy" else "Siguiente paso"
            return Insight(
                eyebrow = "SIGUIENTE PASO",
                title = next.title,
                message = buildReason(next, context),
                taskId = next.id,
                tone = Tone.FOCUSED
            )
        }

        val pendingHabit = habits.firstOrNull { habit ->
            HabitRules.isScheduled(habit, today) &&
                HabitRules.countFor(habitLogs, habit.id, today) < habit.targetPerPeriod
        }
        if (pendingHabit != null) {
            return Insight(
                eyebrow = "UN PEQUEÑO RITUAL",
                title = pendingHabit.title,
                message = "Tu lista está despejada. Este hábito es una buena forma de cerrar el día con intención.",
                tone = Tone.CALM
            )
        }

        if (completedToday > 0) {
            return Insight(
                eyebrow = "BIEN HECHO",
                title = "Tu día está en orden",
                message = "Completaste $completedToday ${if (completedToday == 1) "tarea" else "tareas"} hoy. Puedes descansar o elegir algo pequeño para mañana.",
                tone = Tone.CELEBRATING
            )
        }

        return Insight(
            eyebrow = "TODO EN CALMA",
            title = "No hay pendientes inmediatos",
            message = "Captura una idea, revisa un proyecto o simplemente conserva este espacio libre.",
            tone = Tone.CALM
        )
    }

    private fun buildReason(task: TaskEntity, context: String, overdueCount: Int = 0): String {
        val parts = mutableListOf<String>()
        val duration = task.durationMinutes
        if (duration > 0) {
            parts.add("$duration min")
        }
        parts.add(context.lowercase())

        val urgency = when (task.priority.name) {
            "URGENT" -> "urgente"
            "HIGH" -> "importante"
            else -> ""
        }
        if (urgency.isNotEmpty()) {
            parts.add(urgency)
        }

        val baseMessage = parts.joinToString(" · ").replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }

        return if (overdueCount > 1) {
            "$baseMessage. Tienes $overdueCount tareas atrasadas, comienza por esta."
        } else {
            "$baseMessage. Empieza ahora para mantener el ritmo."
        }
    }
}
