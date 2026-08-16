package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskStatus

/**
 * Cierre del día (sección 19). Opera varios pendientes de una sola vez;
 * no fuerza abrir Ordia para acciones simples.
 */
data class DayClosingReport(
    val remaining: List<TaskEntity>
) {
    val count: Int get() = remaining.size
    val isEmpty: Boolean get() = remaining.isEmpty()
}

object DayClosingEngine {

    fun build(tasks: List<TaskEntity>, now: Long = System.currentTimeMillis()): DayClosingReport {
        val zone = java.time.ZoneId.systemDefault()
        val startToday = java.time.LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        val remaining = tasks.filter {
            !it.completed && !it.archived &&
                (it.status == TaskStatus.PLANNED || it.status == TaskStatus.IN_PROGRESS) &&
                (it.dueAt == null || it.dueAt < startToday + 86_400_000L)
        }.sortedWith(compareBy(nullsLast()) { it.dueAt })
        return DayClosingReport(remaining)
    }
}
