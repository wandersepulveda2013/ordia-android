package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity

/**
 * Briefing diario configurable (sección 18). Resumen muy breve del día.
 * No genera un muro de texto.
 */
data class Briefing(
    val importantCount: Int,
    val appointmentCount: Int,
    val overdueCount: Int,
    val suggestion: String?
) {
    val isEmpty: Boolean
        get() = importantCount == 0 && appointmentCount == 0 && overdueCount == 0
}

object BriefingEngine {

    fun build(tasks: List<TaskEntity>, now: Long = System.currentTimeMillis()): Briefing {
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now()
        val startToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val endToday = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val active = tasks.filter { !it.completed && !it.archived }
        val overdue = active.count {
            val due = it.dueAt ?: return@count false
            due < startToday
        }
        val todayTasks = active.count {
            val due = it.dueAt ?: return@count false
            due in startToday until endToday
        }
        val highPriorityToday = active.count {
            val due = it.dueAt ?: return@count false
            due in startToday until endToday && (it.priority == com.ordia.app.data.local.TaskPriority.HIGH || it.priority == com.ordia.app.data.local.TaskPriority.URGENT)
        }
        val important = maxOf(highPriorityToday, minOf(todayTasks, 3))

        val suggestion = when {
            overdue > 0 && todayTasks > 2 -> "Tienes $overdue vencidas y $todayTasks para hoy. Considera mover una a mañana."
            overdue > 0 -> "Tienes $overdue vencidas. ¿Las abordamos primero?"
            todayTasks > 4 -> "Hoy está cargado ($todayTasks tareas). ¿Reorganizamos?"
            todayTasks == 0 && overdue == 0 -> "Hoy está despejado."
            else -> null
        }

        return Briefing(
            importantCount = important,
            appointmentCount = todayTasks - highPriorityToday + 0,
            overdueCount = overdue,
            suggestion = if (suggestion == "Hoy está despejado.") null else suggestion
        )
    }
}
