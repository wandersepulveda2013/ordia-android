package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Resumen del estado del día y de la semana, calculado de forma
 * determinista a partir de las tareas y un instante inyectado.
 *
 * Es un resumen "asistente": combina lo completado, lo pendiente de hoy,
 * las atrasadas, la bandeja por revisar y el ritmo semanal en una sola
 * estructura que la interfaz puede mostrar en una tarjeta.
 */
data class DaySummary(
    val completedToday: Int,
    val remainingToday: Int,
    val remainingMinutesToday: Int,
    val overdue: Int,
    val inboxPending: Int,
    val completedThisWeek: Int,
    val weekDailyAverage: Float
)

object SummaryEngine {

    /**
     * Calcula el resumen para el día de `now` y los últimos 7 días.
     *
     * - completedToday: tareas completadas cuyo `completedAt` cae hoy.
     * - remainingToday: tareas activas con `dueAt` u `startAt` hoy.
     * - remainingMinutesToday: suma de `durationMinutes` de las anteriores.
     * - overdue: tareas activas vencidas según `TaskRules.isOverdue`.
     * - inboxPending: tareas en estado INBOX sin archivar (por revisar).
     * - completedThisWeek: completadas entre hoy-6 y hoy (inclusive).
     * - weekDailyAverage: completedThisWeek / 7.
     */
    fun summarize(
        tasks: List<TaskEntity>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): DaySummary {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val firstOfWeek = today.minusDays(6)

        val active = { task: TaskEntity ->
            !task.completed && !task.archived && task.status != TaskStatus.CANCELLED
        }
        val completedToday = tasks.count { task ->
            task.completed && task.completedAt?.let { DateRules.toLocalDate(it, zone) == today } == true
        }
        val remainingTodayTasks = tasks.filter { task ->
            active(task) && (onDate(task.dueAt, today, zone) || onDate(task.startAt, today, zone))
        }
        val remainingToday = remainingTodayTasks.size
        val remainingMinutesToday = remainingTodayTasks.sumOf { it.durationMinutes }
        val overdue = tasks.count { TaskRules.isOverdue(it, now) }
        val inboxPending = tasks.count { it.status == TaskStatus.INBOX && !it.archived }
        val completedThisWeek = tasks.count { task ->
            task.completed && task.completedAt?.let {
                val date = DateRules.toLocalDate(it, zone)
                !date.isBefore(firstOfWeek) && !date.isAfter(today)
            } == true
        }
        val weekDailyAverage = completedThisWeek / 7f

        return DaySummary(
            completedToday = completedToday,
            remainingToday = remainingToday,
            remainingMinutesToday = remainingMinutesToday,
            overdue = overdue,
            inboxPending = inboxPending,
            completedThisWeek = completedThisWeek,
            weekDailyAverage = weekDailyAverage
        )
    }

    private fun onDate(epochMillis: Long?, date: LocalDate, zone: ZoneId): Boolean =
        epochMillis?.let { DateRules.toLocalDate(it, zone) == date } ?: false
}
