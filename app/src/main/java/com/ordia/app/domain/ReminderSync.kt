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

    /**
     * Tareas activas cuyo disparo (`reminderAt` ?: `dueAt`) YA venció. Es el espejo
     * simétrico de [CommitmentReminderSync.overdueNow] para tareas: una tarea con
     * `dueAt`/`reminderAt` pasado cuyo WorkManager job se perdió (p.ej. tras
     * [com.ordia.app.backup.BackupManager] restore, que cancela todos los jobs y
     * sólo re-encola los futuros) quedaba SILENCIOSAMENTE sin aviso — justo el
     * dominio "evitar olvidos". Se avisa de inmediato (delay 0) en lugar de dejar
     * la tarea olvidada hasta que el usuario abra la app y la vea en la lista de
     * atrasadas.
     *
     * `triggers` descarta los pasados a propósito para no duplicar notificaciones
     * en re-sincronizaciones rutinarias (cambio de zona/hora), donde el job
     * persistido de WorkManager ya disparó o disparará con retraso. Pero tras un
     * restore los jobs se acaban de cancelar (`cancelAllAndAwait`): un disparo
     * pasado YA NO tiene job que lo dispare, así que aquí se recupera. Simétrico y
     * consistente con cómo `overdueNow` trata a los compromisos vencidos.
     */
    fun overdueNow(tasks: List<TaskEntity>, now: Long): List<Long> =
        tasks.asSequence()
            .filter { TaskRules.isActive(it) }
            .filter { (it.reminderAt ?: it.dueAt)?.let { trigger -> trigger <= now } == true }
            .map { it.id }
            .toList()
}
