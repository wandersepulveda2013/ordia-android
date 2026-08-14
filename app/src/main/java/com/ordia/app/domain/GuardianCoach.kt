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

        // Use DayPlanner to figure out free time
        val plan = DayPlanner.build(tasks = roots, date = today, now = now, zone = zone)
        val availableMinutes = plan.remainingMinutes

        if (overdue.isNotEmpty()) {
            val best = TaskRules.nextBestTask(overdue, now, availableMinutes, zone)
            return Insight(
                eyebrow = "RECUPERA EL CONTROL",
                title = best?.task?.title ?: "Hay algo pendiente",
                message = if (overdue.size == 1 && best != null) {
                    "Haz esto ahora porque ${best.reason}."
                } else if (best != null) {
                    "Tienes ${overdue.size} tareas atrasadas. Empieza con esta porque ${best.reason}."
                } else {
                    "Tienes ${overdue.size} tareas atrasadas. Empieza con un bloque corto."
                },
                taskId = best?.task?.id,
                tone = Tone.GENTLE
            )
        }

        val urgentToday = dueToday.filter { it.priority.name == "URGENT" || it.priority.name == "HIGH" }
        if (urgentToday.isNotEmpty()) {
            val best = TaskRules.nextBestTask(urgentToday, now, availableMinutes, zone)
            return Insight(
                eyebrow = "PROTEGE TU DÍA",
                title = best?.task?.title ?: "Prioridad de hoy",
                message = best?.reason?.let { "Haz esto ahora porque $it." } ?: "Es lo más importante para hoy. Reserva tiempo antes de llenar el resto de la agenda.",
                taskId = best?.task?.id,
                tone = Tone.FOCUSED
            )
        }

        val best = TaskRules.nextBestTask(pending, now, availableMinutes, zone)
        if (best != null) {
            return Insight(
                eyebrow = "SIGUIENTE PASO",
                title = best.task.title,
                message = "Haz esto ahora porque ${best.reason}.",
                taskId = best.task.id,
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
}
