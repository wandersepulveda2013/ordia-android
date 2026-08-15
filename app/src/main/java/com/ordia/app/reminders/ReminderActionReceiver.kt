package com.ordia.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ordia.app.OrdiaApplication
import com.ordia.app.R
import com.ordia.app.data.local.TaskStatus
import com.ordia.app.domain.ReminderRules
import com.ordia.app.domain.SubtaskRules
import com.ordia.app.domain.TaskMutationGate
import com.ordia.app.domain.TaskSnapshotCodec
import com.ordia.app.widget.OrdiaWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val taskId = intent.getLongExtra(TaskReminderWorker.KEY_TASK_ID, -1L)
        if (taskId <= 0L) { pending.finish(); return }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as OrdiaApplication
                TaskMutationGate.mutex.withLock {
                    val repo = app.container.taskRepository
                    val task = repo.get(taskId) ?: return@withLock
                    when (intent.action) {
                        ACTION_COMPLETE -> {
                            if (task.completed || task.archived || task.status == TaskStatus.CANCELLED) return@withLock
                            val now = System.currentTimeMillis()
                            repo.update(task.copy(completed = true, status = TaskStatus.COMPLETED, completedAt = now, updatedAt = now))
                            app.container.reminderScheduler.cancel(taskId)
                            // Próxima ocurrencia + desglose: misma fuente única que
                            // la app (c.223/c.236/c.266). Antes este camino de la
                            // notificación generaba la ocurrencia SIN clonar el
                            // checklist: completar una recurrente con subtareas
                            // desde el recordatorio perdía el desglose ciclo a ciclo.
                            spawnNextOccurrence(task, now, repo, app.container.tagRepository, app.container.reminderScheduler)
                            context.getSystemService(android.app.NotificationManager::class.java).cancel(taskId.hashCode())
                            // Al cerrar la última subtarea desde la notificación, el padre
                            // se completa automáticamente — mismo efecto que toggleTask en
                            // la app. Sin esto el padre queda "pendiente" para siempre
                            // (tarea olvidada) al completar el último hijo desde afuera.
                            completeParentIfDone(app, repo, task, now)
                        }
                        ACTION_SNOOZE -> if (!task.completed && !task.archived && task.status != TaskStatus.CANCELLED) {
                            // Snooze NO sobrescribe reminderAt: codifica la preferencia
                            // del usuario y, en recurrentes, el offset que reutiliza
                            // RecurrenceEngine en cada ocurrencia futura. Sobrescribirlo
                            // con now+10 corrompería ese offset para siempre. El aplazamiento
                            // vive solo en el worker (persistido por WorkManager).
                            val now = System.currentTimeMillis()
                            val snoozed = ReminderRules.snooze(task, now)
                            repo.update(snoozed.task)
                            app.container.reminderScheduler.scheduleAt(taskId, snoozed.triggerAt)
                        }
                    }
                }
                OrdiaWidgetUpdater.updateAll(context)
            } finally { pending.finish() }
        }
    }

    /**
     * Completa automáticamente la tarea padre cuando su última subtarea pendiente
     * se completa desde la notificación. Refleja `completeParentAutomatically` de
     * `OrdiaViewModel` (mismo criterio de `SubtaskRules.shouldAutoCompleteParent`),
     * pero sin emitir eventos de UI (un BroadcastReceiver no puede hacerlo).
     * Registra la automatización para deshacer, igual que el path de la app, y
     * reprograma la recurrencia del padre si la tenía.
     */
    private suspend fun completeParentIfDone(
        app: OrdiaApplication,
        repo: com.ordia.app.data.repository.TaskRepository,
        completedSubtask: com.ordia.app.data.local.TaskEntity,
        now: Long
    ) {
        val parentId = completedSubtask.parentTaskId ?: return
        val parent = repo.get(parentId) ?: return
        if (parent.completed || parent.archived || parent.status == TaskStatus.CANCELLED) return
        val siblings = repo.subtasks(parentId)
        if (!SubtaskRules.shouldAutoCompleteParent(parent, siblings)) return
        val updated = parent.copy(
            completed = true,
            status = TaskStatus.COMPLETED,
            completedAt = now,
            updatedAt = now
        )
        repo.update(updated)
        app.container.reminderScheduler.cancel(parentId)
        // Próxima ocurrencia + desglose: misma fuente única que la app (c.266).
        spawnNextOccurrence(parent, now, repo, app.container.tagRepository, app.container.reminderScheduler)
        app.container.automationLogRepository.insert(
            com.ordia.app.data.local.AutomationLogEntity(
                type = "subtask_auto",
                description = app.getString(
                    R.string.automation_desc_subtask_auto,
                    parent.title
                ),
                affectedTaskIdsJson = TaskSnapshotCodec.encodeIds(listOf(parentId)),
                undoPayloadJson = TaskSnapshotCodec.encodeMap(mapOf(parentId to parent))
            )
        )
    }

    companion object {
        const val ACTION_COMPLETE = "com.ordia.app.action.COMPLETE_TASK"
        const val ACTION_SNOOZE = "com.ordia.app.action.SNOOZE_TASK"
    }
}
