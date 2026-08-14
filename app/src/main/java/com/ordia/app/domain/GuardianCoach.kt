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

        val dayPlan = DayPlanner.build(tasks = roots, date = today, now = now, zone = zone)
        val freeMinutes = dayPlan.remainingMinutes

        if (overdue.isNotEmpty()) {
            val next = TaskRules.nextBestTask(overdue, now)
            val durationText = next?.durationMinutes?.let { "$it min" } ?: "tiempo flexible"
            return Insight(
                eyebrow = "RECUPERA EL CONTROL",
                title = next?.title ?: "Hay algo pendiente",
                message = "Haz esto ahora porque está atrasado. Toma $durationText y tienes $freeMinutes min libres hoy.",
                taskId = next?.id,
                tone = Tone.GENTLE
            )
        }

        val urgentToday = dueToday.filter { it.priority.name == "URGENT" || it.priority.name == "HIGH" }
        if (urgentToday.isNotEmpty()) {
            val next = TaskRules.nextBestTask(urgentToday, now)
            val durationText = next?.durationMinutes?.let { "$it min" } ?: "tiempo flexible"
            return Insight(
                eyebrow = "PROTEGE TU DÍA",
                title = next?.title ?: "Prioridad de hoy",
                message = "Haz esto ahora porque es lo más urgente para hoy ($durationText). Te quedan $freeMinutes min libres.",
                taskId = next?.id,
                tone = Tone.FOCUSED
            )
        }

        val next = TaskRules.nextBestTask(pending, now)
        if (next != null) {
            val durationText = next.durationMinutes?.let { "$it min" } ?: "tiempo flexible"
            val dueText = if (TaskRules.isDueToday(next, now, zone)) "vence hoy" else "es el siguiente paso"
            return Insight(
                eyebrow = "SIGUIENTE PASO",
                title = next.title,
                message = "Haz esto ahora porque $dueText ($durationText). Tienes $freeMinutes min libres.",
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
                message = "Tu lista está despejada. Haz esto ahora para mantener tu racha.",
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
