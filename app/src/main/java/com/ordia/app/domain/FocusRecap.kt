package com.ordia.app.domain

import com.ordia.app.data.local.FocusSessionEntity
import com.ordia.app.data.local.TaskEntity
import java.time.ZoneId

/**
 * Resumen determinista del tiempo de enfoque invertido hoy, agregando las
 * sesiones de enfoque COMPLETADAS del día local por tarea. Fuente única para
 * el asistente ("¿en qué gasto mi tiempo?") y cualquier superficie que nombre
 * en qué se invirtió el día: mismas reglas que GuardianEngine (solo sesiones
 * completadas, tope defensivo por sesión), sin inferencia ni IA.
 */
object FocusRecap {

    data class TopTask(val taskId: Long, val title: String, val minutes: Int)

    data class Recap(val totalMinutes: Int, val topTasks: List<TopTask>)

    const val MAX_TOP_TASKS = 3

    // Mismo tope defensivo por sesión que GuardianEngine.MAX_FOCUS_MINUTES_PER_SESSION.
    private const val MAX_MINUTES_PER_SESSION = 180

    fun today(
        tasks: List<TaskEntity>,
        sessions: List<FocusSessionEntity>,
        nowMillis: Long,
        zone: ZoneId
    ): Recap {
        val today = DateRules.toLocalDate(nowMillis, zone)
        val titlesById = tasks.associateBy({ it.id }, { it.title })
        val minutesByTask = sessions
            .asSequence()
            .filter { it.completed && DateRules.toLocalDate(it.startedAt, zone) == today }
            .groupBy({ it.taskId }, { it.actualMinutes.coerceIn(0, MAX_MINUTES_PER_SESSION) })
        val totalMinutes = minutesByTask.values.sumOf { it.sum() }
        val topTasks = minutesByTask
            .mapNotNull { (taskId, minutes) ->
                val title = taskId?.let { titlesById[it] }?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val total = minutes.sum()
                if (total <= 0) null else TopTask(taskId!!, title, total)
            }
            .sortedByDescending { it.minutes }
            .take(MAX_TOP_TASKS)
        return Recap(totalMinutes = totalMinutes, topTasks = topTasks)
    }

    /** "45 min", "2 h", "1 h 35 min". */
    fun humanMinutes(minutes: Int): String {
        val safe = minutes.coerceAtLeast(0)
        val hours = safe / 60
        val rest = safe % 60
        return when {
            hours == 0 -> "$rest min"
            rest == 0 -> "$hours h"
            else -> "$hours h $rest min"
        }
    }
}
