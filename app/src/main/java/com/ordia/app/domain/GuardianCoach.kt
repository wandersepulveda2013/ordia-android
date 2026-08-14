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

        val plan = DayPlanner.build(tasks = tasks, date = today, now = now, zone = zone)
        val freeTime = plan.remainingMinutes

        if (overdue.isNotEmpty()) {
            val next = TaskRules.nextBestTask(overdue, now)
            val baseMsg = if (overdue.size == 1) {
                "Haz esto ahora porque esta tarea está atrasada. Empieza con un bloque corto y vuelve a poner el día en movimiento."
            } else {
                "Haz esto ahora porque tienes ${overdue.size} tareas atrasadas. No intentes resolverlas todas: comienza por esta."
            }
            val detailsMsg = next?.let { " (${it.durationMinutes} min · atrasada · tienes $freeTime min libres)" } ?: ""
            return Insight(
                eyebrow = "RECUPERA EL CONTROL",
                title = next?.title ?: "Hay algo pendiente",
                message = baseMsg + detailsMsg,
                taskId = next?.id,
                tone = Tone.GENTLE
            )
        }

        val urgentToday = dueToday.filter { it.priority.name == "URGENT" || it.priority.name == "HIGH" }
        if (urgentToday.isNotEmpty()) {
            val next = TaskRules.nextBestTask(urgentToday, now)
            val detailsMsg = next?.let { " (${it.durationMinutes} min · vence hoy · tienes $freeTime min libres)" } ?: ""
            return Insight(
                eyebrow = "PROTEGE TU DÍA",
                title = next?.title ?: "Prioridad de hoy",
                message = "Haz esto ahora porque es lo más importante para hoy. Reserva tiempo antes de llenar el resto de la agenda." + detailsMsg,
                taskId = next?.id,
                tone = Tone.FOCUSED
            )
        }

        val next = TaskRules.nextBestTask(pending, now)
        if (next != null) {
            val dueStr = if (TaskRules.isDueToday(next, now, zone)) "vence hoy" else if (next.dueAt != null) "tiene fecha" else "sin fecha"
            val detailsMsg = " (${next.durationMinutes} min · $dueStr · tienes $freeTime min libres)"
            return Insight(
                eyebrow = "SIGUIENTE PASO",
                title = next.title,
                message = "Haz esto ahora porque Ordia priorizó esta tarea por fecha, importancia y estado." + detailsMsg,
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
}
