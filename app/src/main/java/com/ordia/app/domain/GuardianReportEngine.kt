package com.ordia.app.domain

import com.ordia.app.data.local.CommitmentEntity
import com.ordia.app.data.local.CommitmentReviewStatus
import com.ordia.app.data.local.HabitEntity
import com.ordia.app.data.local.HabitLogEntity
import com.ordia.app.data.local.NoteEntity
import com.ordia.app.data.local.ProjectEntity
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskStatus

/**
 * Guardianes 2.0: cuatro guardianes funcionales con propósito claro.
 * Cada guardián observa un aspecto del sistema y propone una acción directa.
 *
 * - PENDIENTES: tareas que llevan mucho tiempo sin avanzar.
 * - CAOS: notas/tareas sin organizar.
 * - COMPROMISOS: cosas que dijiste que harías.
 * - RUTINA: hábitos/actividades omitidos repetidamente.
 */
data class GuardianReport(
    val pending: GuardianCard?,
    val chaos: GuardianCard?,
    val commitments: GuardianCard?,
    val routine: GuardianCard?
) {
    val cards: List<GuardianCard>
        get() = listOfNotNull(pending, chaos, commitments, routine)

    companion object {
        fun allClear(): GuardianReport = GuardianReport(null, null, null, null)
    }
}

/**
 * Una tarjeta de guardián con propósito, explicación y acción directa.
 */
data class GuardianCard(
    val kind: GuardianKind,
    val title: String,
    val message: String,
    val count: Int,
    val actionLabel: String,
    val whyLabel: String
)

enum class GuardianKind { PENDING, CHAOS, COMMITMENTS, ROUTINE }

object GuardianReportEngine {

    private const val STALE_TASK_DAYS = 7L
    private const val CHAOS_NOTE_THRESHOLD = 8
    private const val COMMITMENT_WINDOW_DAYS = 7L
    private const val HABIT_SKIP_THRESHOLD = 3

    fun report(
        tasks: List<TaskEntity>,
        notes: List<NoteEntity>,
        habits: List<HabitEntity>,
        habitLogs: List<HabitLogEntity>,
        projects: List<ProjectEntity>,
        commitments: List<CommitmentEntity>,
        now: Long = System.currentTimeMillis()
    ): GuardianReport {
        val pending = pendingGuardian(tasks, now)
        val chaos = chaosGuardian(notes, tasks)
        val commitmentsCard = commitmentsGuardian(commitments, now)
        val routine = routineGuardian(habits, habitLogs, now)

        return if (pending == null && chaos == null && commitmentsCard == null && routine == null) {
            GuardianReport.allClear()
        } else {
            GuardianReport(pending, chaos, commitmentsCard, routine)
        }
    }

    private fun pendingGuardian(tasks: List<TaskEntity>, now: Long): GuardianCard? {
        val staleMs = STALE_TASK_DAYS * 86_400_000L
        val stale = tasks.filter { task ->
            !task.completed && !task.archived &&
                task.status != TaskStatus.INBOX &&
                task.updatedAt < now - staleMs
        }
        if (stale.isEmpty()) return null
        return GuardianCard(
            kind = GuardianKind.PENDING,
            title = "${stale.size} ${plural(stale.size, "tarea", "tareas")} sin avanzar",
            message = "Llevan más de una semana en movimiento. ¿Las revisamos?",
            count = stale.size,
            actionLabel = "Revisar",
            whyLabel = "¿Por qué veo esto?"
        )
    }

    private fun chaosGuardian(notes: List<NoteEntity>, tasks: List<TaskEntity>): GuardianCard? {
        val unorganizedNotes = notes.filter { !it.archived && it.projectId == null && !it.pinned }
        val inboxTasks = tasks.filter { it.status == TaskStatus.INBOX && !it.archived }
        val total = unorganizedNotes.size + inboxTasks.size
        if (total < CHAOS_NOTE_THRESHOLD) return null
        return GuardianCard(
            kind = GuardianKind.CHAOS,
            title = "Encontré $total ${plural(total, "elemento", "elementos")} sin organizar",
            message = "Hay notas y capturas sueltas. ¿Las organizamos juntas?",
            count = total,
            actionLabel = "Organizar conmigo",
            whyLabel = "¿Por qué veo esto?"
        )
    }

    private fun commitmentsGuardian(commitments: List<CommitmentEntity>, now: Long): GuardianCard? {
        val windowMs = COMMITMENT_WINDOW_DAYS * 86_400_000L
        val pending = commitments.filter {
            it.reviewStatus == CommitmentReviewStatus.PENDING &&
                it.resultTaskId == null &&
                (it.dueAt != null && it.dueAt < now + windowMs || it.suggestedReminderAt != null && it.suggestedReminderAt < now + windowMs)
        }
        if (pending.isEmpty()) return null
        val first = pending.first()
        val who = first.actor.ifBlank { "alguien" }
        return GuardianCard(
            kind = GuardianKind.COMMITMENTS,
            title = "Dijiste que harías algo con $who",
            message = "Hay ${pending.size} ${plural(pending.size, "compromiso", "compromisos")} pendiente${if (pending.size == 1) "" else "s"} esta semana.",
            count = pending.size,
            actionLabel = "Crear recordatorio",
            whyLabel = "¿Por qué veo esto?"
        )
    }

    private fun routineGuardian(habits: List<HabitEntity>, habitLogs: List<HabitLogEntity>, now: Long): GuardianCard? {
        val today = java.time.LocalDate.now()
        val zone = java.time.ZoneId.systemDefault()
        val skipped = habits.filter { habit ->
            !habit.archived &&
                countRecentSkips(habit.id, habitLogs, today, zone) >= HABIT_SKIP_THRESHOLD
        }
        if (skipped.isEmpty()) return null
        return GuardianCard(
            kind = GuardianKind.ROUTINE,
            title = "${skipped.size} ${plural(skipped.size, "rutina", "rutinas")} se omitió",
            message = "Esta actividad se omitió varias veces. ¿Revisamos el horario?",
            count = skipped.size,
            actionLabel = "Revisar horario",
            whyLabel = "¿Por qué veo esto?"
        )
    }

    private fun countRecentSkips(
        habitId: Long,
        logs: List<HabitLogEntity>,
        today: java.time.LocalDate,
        zone: java.time.ZoneId
    ): Int {
        var skips = 0
        for (i in 0 until 7) {
            val day = today.minusDays(i.toLong())
            val epochDay = day.toEpochDay()
            val logged = logs.any { it.habitId == habitId && it.epochDay == epochDay }
            if (!logged) skips++
        }
        return skips
    }

    private fun plural(n: Int, singular: String, plural: String): String =
        if (n == 1) singular else plural
}
