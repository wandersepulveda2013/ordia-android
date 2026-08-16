package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskStatus

/**
 * Acciones de IA reversibles (sección 9/17 del rediseño).
 * Produce propuestas de cambio presentables como DIFF. Nunca modifica
 * silenciosamente; el usuario aplica tras revisar.
 */
sealed interface OrganizeChange {
    val taskId: Long
    val summary: String

    /** Tarea vencida → reagendar a hoy/mañana. */
    data class RescheduleOverdue(
        override val taskId: Long,
        val title: String,
        val newDueAt: Long
    ) : OrganizeChange {
        override val summary: String
            get() = "Mover \"$title\" (vencida) a ${DateRules.formatDate(newDueAt)}"
    }

    /** Tarea sin fecha y planificable → asignar fecha. */
    data class AssignDue(
        override val taskId: Long,
        val title: String,
        val newDueAt: Long
    ) : OrganizeChange {
        override val summary: String
            get() = "Asignar fecha ${DateRules.formatDate(newDueAt)} a \"$title\""
    }

    /** Tarea en INBOX con fecha detectable → planificar. */
    data class PromoteInbox(
        override val taskId: Long,
        val title: String,
        val newDueAt: Long?
    ) : OrganizeChange {
        override val summary: String
            get() = "Planificar \"$title\"" + (newDueAt?.let { " para ${DateRules.formatDate(it)}" } ?: "")
    }

    /** Duplicado detectado → marcar para revisión. */
    data class FlagDuplicate(
        override val taskId: Long,
        val title: String,
        val duplicateOfId: Long
    ) : OrganizeChange {
        override val summary: String
            get() = "\"$title\" parece duplicada de otra tarea"
    }
}

data class OrganizeProposal(
    val changes: List<OrganizeChange>
) {
    val count: Int get() = changes.size
    val isEmpty: Boolean get() = changes.isEmpty()
}

object OrganizeActionsEngine {

    private const val MAX_CHANGES = 20

    fun proposeWeek(tasks: List<TaskEntity>, now: Long = System.currentTimeMillis()): OrganizeProposal {
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now()
        val tomorrow = today.plusDays(1)
        val changes = mutableListOf<OrganizeChange>()

        val active = tasks.filter { !it.completed && !it.archived }

        for (task in active) {
            val due = task.dueAt
            if (due != null && due < now && task.status != TaskStatus.INBOX) {
                changes += OrganizeChange.RescheduleOverdue(
                    task.id, task.title, tomorrow.atStartOfDay(zone).toInstant().toEpochMilli()
                )
            }
        }

        val inboxWithDue = active.filter { it.status == TaskStatus.INBOX }
        for (task in inboxWithDue) {
            if (changes.size >= MAX_CHANGES) break
            changes += OrganizeChange.PromoteInbox(task.id, task.title, task.dueAt)
        }

        val noDate = active.filter {
            it.dueAt == null && it.status != TaskStatus.INBOX &&
                it.id !in changes.map { c -> c.taskId }
        }
        for (task in noDate) {
            if (changes.size >= MAX_CHANGES) break
            changes += OrganizeChange.AssignDue(
                task.id, task.title, today.plusDays(3).atStartOfDay(zone).toInstant().toEpochMilli()
            )
        }

        changes += detectDuplicates(active)

        return OrganizeProposal(changes.take(MAX_CHANGES))
    }

    private fun detectDuplicates(tasks: List<TaskEntity>): List<OrganizeChange.FlagDuplicate> {
        val byKey = tasks.groupBy { normKey(it.title) }
        val out = mutableListOf<OrganizeChange.FlagDuplicate>()
        for ((_, group) in byKey) {
            if (group.size < 2) continue
            val keeper = group.maxByOrNull { it.updatedAt } ?: continue
            for (t in group) {
                if (t.id == keeper.id) continue
                out += OrganizeChange.FlagDuplicate(t.id, t.title, keeper.id)
            }
        }
        return out
    }

    private fun normKey(title: String): String =
        title.lowercase().trim().replace(Regex("[^a-z0-9áéíóúñ ]"), "")
}
