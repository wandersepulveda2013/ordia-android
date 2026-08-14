package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity

/**
 * Lógica pura de sincronización de recordatorios (misma regla de disparo que
 * [com.ordia.app.reminders.ReminderScheduler.schedule]: trigger = reminderAt
 * si existe, si no dueAt).
 *
 * La re-sincronización solo re-encola disparos futuros: los pasados ya fueron
 * atendidos (o se atenderán con retraso) por WorkManager, y re-encolarlos
 * provocaría notificaciones duplicadas.
 */
object ReminderSync {

    fun triggers(tasks: List<TaskEntity>, now: Long): List<Pair<Long, Long>> =
        tasks.asSequence()
            .filter { TaskRules.isActive(it) }
            .mapNotNull { task ->
                val trigger = task.reminderAt ?: task.dueAt ?: return@mapNotNull null
                if (trigger <= now) null else task.id to trigger
            }
            .toList()
}
