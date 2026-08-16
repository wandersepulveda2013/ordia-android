package com.ordia.app.domain

import com.ordia.app.data.local.RecurrenceFrequency
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Explica por qué Ordia colocó una tarea en esa posición del plan. */
enum class PlanReason { OVERDUE, URGENT, HIGH_PRIORITY, DUE_TODAY, DUE_ON_DATE, SCHEDULED_TIME, INBOX }

/** Advertencia del planificador sobre cambios sobre datos previos del usuario. */
enum class PlanConflictKind { MOVED_FROM_SCHEDULED_TIME }

/** Builds a realistic local day plan from existing tasks without changing user data. */
object DayPlanner {
    data class Block(
        val taskId: Long,
        val title: String,
        val startMinute: Int,
        val endMinute: Int,
        val priority: TaskPriority,
        val overdue: Boolean,
        val reason: PlanReason = PlanReason.DUE_TODAY
    ) {
        val durationMinutes: Int get() = endMinute - startMinute
    }

    data class PlanConflict(val taskId: Long, val kind: PlanConflictKind)

    data class Plan(
        val date: LocalDate,
        val blocks: List<Block>,
        val unscheduledTaskIds: List<Long>,
        val availableMinutes: Int,
        val scheduledMinutes: Int,
        val conflicts: List<PlanConflict> = emptyList()
    ) {
        val remainingMinutes: Int get() = (availableMinutes - scheduledMinutes).coerceAtLeast(0)
    }

    fun build(
        tasks: List<TaskEntity>,
        date: LocalDate,
        dayStartMinute: Int = 9 * 60,
        dayEndMinute: Int = 18 * 60,
        breakMinutes: Int = 10,
        includeInbox: Boolean = true,
        includeScheduledOnDate: Boolean = false,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Plan {
        require(dayStartMinute in 0 until 24 * 60)
        require(dayEndMinute in 1..24 * 60)
        require(dayEndMinute > dayStartMinute)
        require(breakMinutes in 0..60)

        // Past-safe: un plan "realista de HOY" no puede arrancar en una hora ya
        // pasada. Si se construye el plan de hoy a las 13:00, colocar el primer
        // slot a las 09:00 (dayStartMinute) sembraría tareas con `startAt` anterior
        // a `now` → "inicio perdido" (isMissedStart) con recordatorio nulo, y una
        // vencida re-planificada en un slot pasado seguía vencida con su `due`
        // también en el pasado. "Planificar el día" producía justo lo opuesto a su
        // propósito. Por eso, cuando la fecha del plan es HOY, el cursor arranca en
        // max(dayStart, now redondeado al alza a 15 min) — simétrico con
        // BATCH_QUICK_TASKS y planReminder. Para fechas futuras no se recorta: un
        // plan de mañana usa su ventana completa. (c.211)
        val nowZ = Instant.ofEpochMilli(now).atZone(zone)
        val isToday = nowZ.toLocalDate() == date
        val effectiveStart = if (isToday) {
            val nowMinute = nowZ.hour * 60 + nowZ.minute
            (((nowMinute + 14) / 15) * 15).coerceAtLeast(dayStartMinute)
        } else dayStartMinute

        val candidates = tasks.asSequence()
            .filter { TaskRules.isActive(it) && it.parentTaskId == null }
            // Sacro recurrencia: una tarea periódica (recurrence != NONE) es un
            // compromiso FIJO anclado a su propio dueAt/startAt. Reasignarla a un
            // slot del cursor pisaría ese ancla y, con ella, los offsets
            // (reminderOffset = dueAt - reminderAt, startOffset = dueAt - startAt)
            // que [RecurrenceEngine.nextOccurrence] reutiliza para TODAS las
            // ocurrencias futuras: "pagar el 1 a las 09:00" se desplazaría a otro
            // día/hora para siempre. Su visibilidad en el día la da la agenda
            // (tasksOnDate/PlannerCalendar), no el plan sugerido; este último
            // programa trabajo SIN hueco fijo. Simétrico con el sacro "en curso" y
            // con los filtros de AutomationActionPlanner (RESCHEDULE_OVERDUE,
            // BATCH_QUICK_TASKS). Cierra de forma centralizada las dos rutas que
            // mutan fechas: la automatización (PLAN_DAY) y la UI (Apply/Replan).
            .filter { it.recurrence == RecurrenceFrequency.NONE }
            .filter { task ->
                val dueOnDate = TaskRules.isDueOn(task, date, zone)
                val overdueByDate = task.dueAt?.let { DateRules.toLocalDate(it, zone).isBefore(date) } == true
                val scheduledOnDate = includeScheduledOnDate && task.startAt?.let {
                    DateRules.toLocalDate(it, zone) == date
                } == true
                // Recuperación de "olvido silencioso" (c.244): un compromiso agendado
                // cuyo hueco ya pasó (startAt < now) pero que aún no vence (dueAt
                // futuro o nulo) no cae en dueOnDate/overdueByDate/scheduledOnDate/inbox.
                // El plan de HOY lo recupera igual que ya recupera las vencidas
                // (overdueByDate): esperar a su vencimiento es justamente olvidarlo.
                // Solo aplica al plan de hoy (la recuperación es un concepto "ahora");
                // un plan futuro verá la tarea en su fecha de vencimiento. Simétrico con
                // el guardián (c.201/c.243), What Now (c.203) y el asistente (c.206).
                val missedStartRecoverable = isToday && TaskRules.isMissedStart(task, now)
                // Bandeja = captura SIN vencimiento (`dueAt`) Y SIN hueco (`startAt`):
                // una tarea con `startAt` futuro (programada para otro día) NO es
                // bandeja aunque no tenga `dueAt`. Antes la condición `dueAt == null`
                // la aspiraba al plan de HOY, y `applyBlocks`/`PLAN_DAY` sobrescribían
                // su `startAt` con el slot de hoy, destruyendo la programación explícita
                // del usuario ("reunión el jueves 15:00" se movía a hoy 09:00 sin
                // aviso). Exigir `startAt == null` respeta el hueco que el usuario dio;
                // el compromiso olvidado (startAt pasado) ya se recupera por
                // `missedStartRecoverable`, y el que vence, por `dueOnDate`/`overdueByDate`.
                dueOnDate || overdueByDate || scheduledOnDate ||
                    missedStartRecoverable || (includeInbox && task.dueAt == null && task.startAt == null)
            }
            .sortedWith(
                // Una tarea EN CURSO (el usuario la está haciendo ahora) encabeza el
                // plan: el cursor de hoy arranca en "ahora", así que el primer bloque
                // describe lo que ocurre EN este instante. Sin esta prelación, una
                // tarea en curso de prioridad NORMAL quedaba detrás de una URGENTE no
                // empezada que vence hoy, y el plan decía "empieza la urgente ahora"
                // mientras el usuario estaba a medio hacer otra: justo lo opuesto a
                // un plan realista. Reservar primero lo que FALTA de la tarea en curso
                // ([remainingPlanMinutes]) reproduce la realidad: ese tiempo ya se está
                // gastando. Coherente con [TaskRules.timeRank] (IN_PROGRESS/en-curso
                // es el rango más alto) que comparten What Now, nextBestTask y widget.
                // Condición `isBeingWorkedOn` (status IN_PROGRESS o ventana activa):
                // cubre tanto la marcada a mano (sin `startAt`) como la de ventana activa.
                // Predicado "sacro en curso" centralizado en TaskRules, compartido con
                // What Now, guardián, resúmenes y automatizaciones.
                compareByDescending<TaskEntity> {
                    TaskRules.isBeingWorkedOn(it, now)
                }
                    .thenByDescending { TaskRules.isOverdue(it, now) }
                    .thenByDescending { TaskRules.priorityScore(it.priority) }
                    .thenBy { it.dueAt ?: Long.MAX_VALUE }
                    .thenBy { it.sortOrder }
                    .thenBy { it.createdAt }
            )
            .toList()

        val tasksById = candidates.associateBy { it.id }
        val blocks = mutableListOf<Block>()
        val unscheduled = mutableListOf<Long>()
        // Si el inicio efectivo cae en o después del fin del día, ya no hay ventana
        // hoy: el plan queda vacío en vez de inventar slots pasados.
        if (effectiveStart < dayEndMinute) {
            var cursor = effectiveStart
            candidates.forEach { task ->
                // Una tarea EN CURSO (ventana activa) ya tiene parte de su tiempo
                // consumido: el bloque reserva solo lo que FALTA (remainingPlanMinutes),
                // no la duración completa. Así el plan no sobre-reserva ni empuja a
                // tareas posteriores con un tiempo ya vivido (c.241, simétrico al
                // c.240 de SummaryEngine). Para el resto equivale a plannedDuration.
                val duration = TaskRules.remainingPlanMinutes(task, now)
                val gap = if (blocks.isEmpty()) 0 else breakMinutes
                val proposedStart = cursor + gap
                val proposedEnd = proposedStart + duration
                if (proposedEnd <= dayEndMinute) {
                    blocks += Block(
                        taskId = task.id,
                        title = task.title,
                        startMinute = proposedStart,
                        endMinute = proposedEnd,
                        priority = task.priority,
                        overdue = TaskRules.isOverdue(task, now),
                        reason = planReason(task, now, date, zone)
                    )
                    cursor = proposedEnd
                } else {
                    unscheduled += task.id
                }
            }
        }

        // Conflictos: el plan mueve tareas que ya tenían hora prevista ese día.
        // Solo se compara la hora cuando `startAt` cae realmente en el día del plan:
        // una tarea cuyo `startAt` es otro día (p. ej. empezada ayer, vence hoy) no
        // tiene hora prevista "ese día" y no debe marcarse como movida.
        val conflicts = buildList {
            blocks.forEach { block ->
                val task = tasksById[block.taskId]
                if (task?.startAt != null) {
                    val original = Instant.ofEpochMilli(task.startAt).atZone(zone)
                    if (original.toLocalDate() == date) {
                        val originalMinute = original.hour * 60 + original.minute
                        if (originalMinute != block.startMinute) {
                            add(PlanConflict(block.taskId, PlanConflictKind.MOVED_FROM_SCHEDULED_TIME))
                        }
                    }
                }
            }
        }

        return Plan(
            date = date,
            blocks = blocks,
            unscheduledTaskIds = unscheduled,
            // Tiempo realmente disponible: si el plan es de hoy y ya pasaron las
            // 09:00, no se cuenta el tramo ya consumido. (c.211)
            availableMinutes = (dayEndMinute - effectiveStart).coerceAtLeast(0),
            scheduledMinutes = blocks.sumOf { it.durationMinutes } +
                ((blocks.size - 1).coerceAtLeast(0) * breakMinutes),
            conflicts = conflicts
        )
    }

    /**
     * Razón de colocación. "Vence hoy" solo es cierto cuando la tarea vence
     * el día real de hoy; en un plan construido para otra fecha, una tarea
     * que vence ese día se etiqueta como "vence este día" para no fingir
     * urgencia de hoy.
     */
    private fun planReason(task: TaskEntity, now: Long, date: LocalDate, zone: ZoneId): PlanReason = when {
        TaskRules.isOverdue(task, now) -> PlanReason.OVERDUE
        task.priority == TaskPriority.URGENT -> PlanReason.URGENT
        task.priority == TaskPriority.HIGH -> PlanReason.HIGH_PRIORITY
        task.startAt != null -> PlanReason.SCHEDULED_TIME
        task.dueAt != null -> {
            val dueDate = DateRules.toLocalDate(task.dueAt, zone)
            val today = DateRules.toLocalDate(now, zone)
            if (dueDate == today) PlanReason.DUE_TODAY else PlanReason.DUE_ON_DATE
        }
        else -> PlanReason.INBOX
    }
}
