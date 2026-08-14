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
        val durationMinutes: Int? = null,
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

        // Calculate dynamic free time from the local day plan
        val plan = DayPlanner.build(tasks, today, now = now, zone = zone)
        val remainingMinutes = plan.remainingMinutes

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
            val duration = next?.durationMinutes ?: 25
            val contextMsg = if (remainingMinutes >= duration) {
                "Tienes ${overdue.size} tareas atrasadas, pero dispones de $remainingMinutes min libres hoy. Empieza por esta para recuperar el control."
            } else {
                "Tienes ${overdue.size} tareas atrasadas. El día está ajustado, así que enfócate solo en sacar esto adelante."
            }
            return Insight(
                eyebrow = "AHORA · ATENCIÓN",
                title = next?.title ?: "Hay algo pendiente",
                message = contextMsg,
                taskId = next?.id,
                durationMinutes = duration,
                tone = Tone.GENTLE
            )
        }

        val urgentToday = dueToday.filter { it.priority.name == "URGENT" || it.priority.name == "HIGH" }
        if (urgentToday.isNotEmpty()) {
            val next = TaskRules.nextBestTask(urgentToday, now)
            val duration = next?.durationMinutes ?: 25
            val contextMsg = if (remainingMinutes >= duration) {
                "Haz esto ahora porque es la mayor prioridad del día y tienes $remainingMinutes min libres en tu plan."
            } else {
                "Haz esto ahora porque es tu mayor prioridad y el tiempo restante de hoy es escaso."
            }
            return Insight(
                eyebrow = "AHORA · PROTEGE TU DÍA",
                title = next?.title ?: "Prioridad de hoy",
                message = contextMsg,
                taskId = next?.id,
                durationMinutes = duration,
                tone = Tone.FOCUSED
            )
        }

        val next = TaskRules.nextBestTask(pending, now)
        if (next != null) {
            val duration = next.durationMinutes.coerceAtLeast(15)
            val contextMsg = if (remainingMinutes >= duration) {
                "Ordía priorizó esta tarea. Te tomará unos $duration min y tienes $remainingMinutes min disponibles hoy."
            } else {
                "Ordía priorizó esta tarea por fecha e importancia."
            }
            return Insight(
                eyebrow = "SIGUIENTE ACCIÓN",
                title = next.title,
                message = contextMsg,
                taskId = next.id,
                durationMinutes = duration,
                tone = Tone.FOCUSED
            )
        }

        val pendingHabit = habits.firstOrNull { habit ->
            HabitRules.isScheduled(habit, today) &&
                HabitRules.countFor(habitLogs, habit.id, today) < habit.targetPerPeriod
        }
        if (pendingHabit != null) {
            return Insight(
                eyebrow = "DESPUÉS · UN PEQUEÑO RITUAL",
                title = pendingHabit.title,
                message = "Tu lista está despejada y tienes $remainingMinutes min libres. Este hábito es ideal para avanzar con calma.",
                tone = Tone.CALM
            )
        }

        if (completedToday > 0) {
            return Insight(
                eyebrow = "BIEN HECHO",
                title = "Tu día está en orden",
                message = "Completaste $completedToday ${if (completedToday == 1) "tarea" else "tareas"} hoy. Tienes $remainingMinutes min para descansar o planificar.",
                tone = Tone.CELEBRATING
            )
        }

        return Insight(
            eyebrow = "TODO EN CALMA",
            title = "Día sin carga",
            message = "Dispones de $remainingMinutes min libres. Puedes capturar una idea o simplemente conservar este espacio libre.",
            tone = Tone.CALM
        )
    }
}
