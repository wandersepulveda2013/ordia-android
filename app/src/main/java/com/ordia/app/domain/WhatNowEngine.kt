package com.ordia.app.domain

import com.ordia.app.data.local.HabitEntity
import com.ordia.app.data.local.HabitLogEntity
import com.ordia.app.data.local.TaskEntity
import java.time.Instant
import java.time.ZoneId

object WhatNowEngine {
    data class Recommendation(
        val taskId: Long,
        val title: String,
        val reason: String,
        val durationMinutes: Int,
        val isOverdue: Boolean
    )

    fun evaluate(
        tasks: List<TaskEntity>,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        dayEndMinute: Int = 22 * 60 // 10 PM
    ): Recommendation? {
        val localTime = Instant.ofEpochMilli(now).atZone(zone).toLocalTime()
        val currentMinute = localTime.hour * 60 + localTime.minute
        val availableMinutes = (dayEndMinute - currentMinute).coerceAtLeast(0)

        val pendingTasks = tasks.filter { !it.completed && !it.archived && it.parentTaskId == null }
        if (pendingTasks.isEmpty()) return null

        val overdue = pendingTasks.filter { TaskRules.isOverdue(it, now) }
        val dueToday = pendingTasks.filter { TaskRules.isDueToday(it, now, zone) }

        // 1. Overdue
        if (overdue.isNotEmpty()) {
            val next = TaskRules.nextBestTask(overdue, now) ?: overdue.first()
            val freeTimeStr = if (availableMinutes > 0) "tienes $availableMinutes min libres" else "el día ya terminó"
            return Recommendation(
                taskId = next.id,
                title = next.title,
                reason = "${next.durationMinutes} min · atrasada · $freeTimeStr.",
                durationMinutes = next.durationMinutes,
                isOverdue = true
            )
        }

        // 2. Urgent / High Today
        val urgentToday = dueToday.filter { it.priority.name == "URGENT" || it.priority.name == "HIGH" }
        if (urgentToday.isNotEmpty()) {
            val next = TaskRules.nextBestTask(urgentToday, now) ?: urgentToday.first()
            val freeTimeStr = if (availableMinutes > 0) "tienes $availableMinutes min libres" else "el día casi termina"
            return Recommendation(
                taskId = next.id,
                title = next.title,
                reason = "${next.durationMinutes} min · alta prioridad · $freeTimeStr.",
                durationMinutes = next.durationMinutes,
                isOverdue = false
            )
        }

        // 3. Normal Today
        if (dueToday.isNotEmpty()) {
            val next = TaskRules.nextBestTask(dueToday, now) ?: dueToday.first()
            val freeTimeStr = if (availableMinutes > 0) "tienes $availableMinutes min libres" else "poco tiempo"
            return Recommendation(
                taskId = next.id,
                title = next.title,
                reason = "${next.durationMinutes} min · para hoy · $freeTimeStr.",
                durationMinutes = next.durationMinutes,
                isOverdue = false
            )
        }

        // 4. Any other task
        val otherTasks = pendingTasks.filter { !dueToday.contains(it) && !overdue.contains(it) }
            .sortedBy { it.priority.ordinal }.reversed()

        if (otherTasks.isNotEmpty()) {
            val next = TaskRules.nextBestTask(otherTasks, now) ?: otherTasks.first()
            val freeTimeStr = if (availableMinutes > 0) "tienes $availableMinutes min libres" else "el día casi termina"
            return Recommendation(
                taskId = next.id,
                title = next.title,
                reason = "${next.durationMinutes} min · sugerencia libre · $freeTimeStr.",
                durationMinutes = next.durationMinutes,
                isOverdue = false
            )
        }

        return null
    }
}
