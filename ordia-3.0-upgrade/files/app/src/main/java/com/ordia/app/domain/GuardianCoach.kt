package com.ordia.app.domain

import com.ordia.app.data.local.HabitEntity
import com.ordia.app.data.local.HabitLogEntity
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskStatus
import java.time.ZoneId

object GuardianCoach {
    enum class Tone { CALM, FOCUSED, CELEBRATING, GENTLE }
    data class Insight(val eyebrow: String, val title: String, val message: String, val taskId: Long? = null, val tone: Tone = Tone.CALM)

    fun insight(tasks: List<TaskEntity>, habits: List<HabitEntity>, habitLogs: List<HabitLogEntity>, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Insight {
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val roots = tasks.filter { it.parentTaskId == null && !it.archived && it.status != TaskStatus.CANCELLED }
        val pending = roots.filterNot { it.completed }
        val overdue = pending.filter { TaskRules.isOverdue(it, now) }
        val dueToday = pending.filter { TaskRules.isDueToday(it, now, zone) && !TaskRules.isOverdue(it, now) }
        val completedToday = roots.count { it.completed && it.completedAt?.let { time -> java.time.Instant.ofEpochMilli(time).atZone(zone).toLocalDate() == today } == true }
        if (overdue.isNotEmpty()) {
            val next = TaskRules.nextBestTask(overdue, now)
            return Insight("RECUPERA EL CONTROL", next?.title ?: "Hay algo pendiente", if (overdue.size == 1) "Esta tarea está atrasada. Empieza con un bloque corto." else "Tienes ${overdue.size} tareas atrasadas. Comienza por esta.", next?.id, Tone.GENTLE)
        }
        val urgent = dueToday.filter { it.priority.name == "URGENT" || it.priority.name == "HIGH" }
        if (urgent.isNotEmpty()) {
            val next = TaskRules.nextBestTask(urgent, now)
            return Insight("PROTEGE TU DÍA", next?.title ?: "Prioridad de hoy", "Reserva tiempo para lo más importante antes de llenar la agenda.", next?.id, Tone.FOCUSED)
        }
        TaskRules.nextBestTask(pending, now)?.let { return Insight("SIGUIENTE PASO", it.title, it.details.takeIf(String::isNotBlank) ?: "Ordia la priorizó por fecha, importancia y estado.", it.id, Tone.FOCUSED) }
        habits.firstOrNull { HabitRules.isScheduled(it, today) && HabitRules.countFor(habitLogs, it.id, today) < it.targetPerPeriod }?.let {
            return Insight("UN PEQUEÑO RITUAL", it.title, "Tu lista está despejada. Este hábito puede cerrar el día con intención.", tone = Tone.CALM)
        }
        if (completedToday > 0) return Insight("BIEN HECHO", "Tu día está en orden", "Completaste $completedToday ${if (completedToday == 1) "tarea" else "tareas"} hoy.", tone = Tone.CELEBRATING)
        return Insight("TODO EN CALMA", "No hay pendientes inmediatos", "Captura una idea, revisa un proyecto o conserva este espacio libre.", tone = Tone.CALM)
    }
}
