package com.ordia.app.automation

import com.ordia.app.data.local.AutomationAction
import com.ordia.app.data.local.AutomationCondition
import com.ordia.app.data.local.AutomationRuleEntity
import com.ordia.app.data.local.CommitmentReviewStatus
import com.ordia.app.data.local.RecurrenceFrequency
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskStatus
import com.ordia.app.domain.DateRules
import com.ordia.app.domain.DayPlanner
import com.ordia.app.domain.TaskRules
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

data class AutomationPlan(
    val updates: List<TaskEntity> = emptyList(),
    val creates: List<TaskEntity> = emptyList(),
    val message: String,
    val matched: Boolean = true
)

object AutomationActionPlanner {
    fun build(
        rule: AutomationRuleEntity,
        tasks: List<TaskEntity>,
        pendingCommitments: Int,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): AutomationPlan {
        // Solo tareas raíz (parentTaskId == null): las subtareas son anidadas y
        // contarlas además del padre infla los conteos y dispara condiciones de
        // automatización sin tareas raíz reales (p. ej. HAS_INBOX_TASKS cierto por
        // una subtarea con dueAt == null). Misma fuente única de verdad que
        // WhatNowEngine, GuardianEngine, SummaryEngine y DayPlanner.
        val active = tasks.filter { TaskRules.isActive(it) && it.parentTaskId == null }
        // Sacro "en curso": las tareas que el usuario ejecuta ahora (status IN_PROGRESS o
        // ventana activa) se excluyen de los candidatos a MUTAR. Una automatización que
        // reprograme vencidas o agrupe rápidas no debe pisar el trabajo activo: resetearía
        // el estado a PLANNED, borraría el startAt o empujaría el vencimiento, descarrilando
        // lo que el usuario hace en este instante. Simétrico con GuardianEngine (no señala
        // vencidas en curso) y con timeRank (las coloca arriba). El filtro fluye también a
        // las condiciones HAS_OVERDUE_TASKS/HAS_QUICK_TASKS: si las únicas vencidas/rápidas
        // están en curso, no hay nada que automatizar y la condición no se cumple (correcto).
        //
        // Sacro "recurrencia": las tareas recurrentes (recurrence != NONE) se excluyen
        // IGUALMENTE de todo candidato a MUTAR. Su `dueAt`/`startAt`/`reminderAt` son el
        // ANCLA de la cadencia: [RecurrenceEngine.nextOccurrence] deriva de ellos los
        // offsets (`dueAt - reminderAt`, `dueAt - startAt`) que reutiliza para TODAS las
        // ocurrencias futuras. Reprogramar/agrupar una recurrente pisaría ese ancla y
        // desplazaría cada ciclo venidero (p. ej. "pagar el 1" vencida movida a "mañana
        // 18:00" arrastra toda la serie mensual). Su ciclo lo gestiona el motor de
        // recurrencia: completarla engendra la siguiente ocurrencia; la automatización no
        // debe "mover" un compromiso periódico, sino dejarlo a su motor. Simétrico con el
        // sacro "en curso" y con GuardianEngine, que recupera las vencidas por otros
        // caminos (nudge, What Now). El filtro fluye a las mismas condiciones: si las únicas
        // vencidas/rápidas son recurrentes, no hay nada que automatizar sin corromper datos.
        val mutable = active.filter { it.recurrence == RecurrenceFrequency.NONE }
        val overdue = mutable.filter { TaskRules.isOverdue(it, now) && !TaskRules.isBeingWorkedOn(it, now) }
        val quick = mutable.filter { it.durationMinutes <= 10 && !TaskRules.isBeingWorkedOn(it, now) }
        val conditionMet = when (rule.condition) {
            AutomationCondition.ALWAYS -> true
            AutomationCondition.HAS_OVERDUE_TASKS -> overdue.isNotEmpty()
            AutomationCondition.HAS_INBOX_TASKS -> mutable.any { it.status == TaskStatus.INBOX || it.dueAt == null }
            AutomationCondition.HAS_QUICK_TASKS -> quick.isNotEmpty()
            AutomationCondition.HAS_PENDING_COMMITMENTS -> pendingCommitments > 0
        }
        if (!conditionMet) return AutomationPlan(message = "La condición no se cumple; no se cambió nada.", matched = false)

        return when (rule.action) {
            AutomationAction.PLAN_DAY -> {
                // La fecha del plan se deriva del `now` inyectado (igual que
                // BATCH_QUICK_TASKS), no del reloj del sistema: así la decisión es
                // determinista y verificable con un `now` fijo.
                val nowZ = Instant.ofEpochMilli(now).atZone(zone)
                val date = nowZ.toLocalDate()
                // Past-safe: DayPlanner.build ya arranca el cursor en
                // max(dayStart, now redondeado al alza) cuando el plan es de hoy (c.211),
                // así que aquí no se recalcula el inicio. Solo se detecta el caso en que
                // ya no queda ventana hoy para dar un mensaje claro y no invocar a build
                // en vano. Simétrico con BATCH_QUICK_TASKS.
                val nowMinute = nowZ.hour * 60 + nowZ.minute
                val dayEnd = 18 * 60
                if ((((nowMinute + 14) / 15) * 15) >= dayEnd) {
                    return AutomationPlan(message = "Es tarde para planificar hoy; no se cambió nada.", matched = false)
                }
                val plan = DayPlanner.build(mutable, date, dayStartMinute = 9 * 60, dayEndMinute = dayEnd, now = now, zone = zone)
                val byId = tasks.associateBy { it.id }
                val updates = plan.blocks.take(12).mapNotNull { block ->
                    val start = DateRules.toEpochMillis(date, LocalTime.of(block.startMinute / 60, block.startMinute % 60), zone)
                    val end = DateRules.toEpochMillis(date, LocalTime.of(block.endMinute / 60, block.endMinute % 60), zone)
                    byId[block.taskId]?.copy(
                        startAt = start,
                        // Vencimiento coherente con el slot: si este empieza después del due
                        // original (tarea vencida/temprana en un bloque posterior), el due
                        // sigue al fin del slot. Evita `startAt > dueAt`, estado que
                        // [BackupManager] rechaza al restaurar (backup irrestaurable).
                        dueAt = TaskRules.dueAtForPlannedSlot(byId[block.taskId]?.dueAt, start, end),
                        // Recordatorio past-safe: se respeta un aviso previo del usuario SOLO si sigue
                        // siendo futuro. Si era pasado (tarea vencida cuyo aviso ya disparó), conservarlo
                        // tal cual lo dejaría SIN nudge para el nuevo slot: [ReminderSync] descarta
                        // trigger <= now, así la tarea planificada volvería a olvidarse. En ese caso se
                        // recae al default (inicio del slot, si es futuro). Simétrico con
                        // RESCHEDULE_OVERDUE (c.187) y [ReminderRules.resolveReminderAt] (c.183).
                        reminderAt = planReminder(byId[block.taskId]?.reminderAt, start, now),
                        status = TaskStatus.PLANNED,
                        updatedAt = now
                    )
                }
                AutomationPlan(updates = updates, message = "${updates.size} tareas preparadas para hoy.", matched = updates.isNotEmpty())
            }
            AutomationAction.RESCHEDULE_OVERDUE -> {
                // El día base de reprogramación se deriva del `now` inyectado, no del
                // reloj del sistema: coherente con PLAN_DAY/BATCH_QUICK_TASKS y
                // determinista. Antes LocalDate.now(zone) ignoraba el `now` fijo de
                // los tests (que solo pasaban porque "mañana real" > now inyectado).
                val base = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                val updates = overdue.sortedBy { it.dueAt }.take(8).mapIndexed { index, task ->
                    val due = DateRules.toEpochMillis(base.plusDays(1L + index / 3), LocalTime.of(18, 0), zone)
                    // Recordatorio:
                    // - Si la tarea ya tenía uno, se conserva el OFFSET original del usuario
                    //   (dueAt - reminderAt), aplicándolo al nuevo vencimiento. Esto evita
                    //   corromper la cadencia de recordatorios recurrentes, que [RecurrenceEngine]
                    //   reutiliza como offset para todas las ocurrencias futuras.
                    // - Si no tenía recordatorio, se añade uno 1 h antes del nuevo vencimiento
                    //   (siempre futuro): una tarea vencida reprogramada no debe quedar al olvido.
                    //   Coherente con PLAN_DAY/BATCH_QUICK_TASKS (c.129), que añaden un recordatorio
                    //   por defecto en el inicio cuando no existía.
                    // - Si el offset trasladado cae en el PASADO (offset grande, p. ej. "recuérdame
                    //   2 días antes" sobre una vencida de ayer → reminder ~27 h atrás), cae al
                    //   mismo default de 1 h antes (futuro). Un reminder pasado lo descarta
                    //   [ReminderSync] (trigger <= now → null), así sin este respaldo la tarea
                    //   reprogramada volvería a quedar SIN aviso → el usuario la olvidaba otra
                    //   vez, justo lo que esta acción debe evitar. Simétrico con
                    //   [ReminderRules.resolveReminderAt] (c.183), que también recae a un default
                    //   past-safe cuando la traslación del offset no cabe en el futuro.
                    val reminder = if (task.reminderAt != null && task.dueAt != null) {
                        val offset = task.dueAt - task.reminderAt
                        val translated = due - offset
                        if (translated > now) translated else due - 60 * 60_000L
                    } else {
                        due - 60 * 60_000L
                    }
                    task.copy(
                        startAt = null,
                        dueAt = due,
                        reminderAt = reminder,
                        status = TaskStatus.PLANNED,
                        updatedAt = now
                    )
                }
                AutomationPlan(updates = updates, message = "${updates.size} tareas vencidas reprogramadas.", matched = updates.isNotEmpty())
            }
            AutomationAction.BATCH_QUICK_TASKS -> {
                val current = Instant.ofEpochMilli(now).atZone(zone)
                val date = if (current.hour >= 20) current.toLocalDate().plusDays(1) else current.toLocalDate()
                val firstMinute = if (date != current.toLocalDate()) 9 * 60 else (((current.hour * 60 + current.minute + 29) / 15) * 15)
                val dayEndMinute = 21 * 60
                // Compromisos FIJOS del día que el agrupador NO debe pisar (reuniones
                // agendadas con `startAt` hoy que NO son candidatas rápidas). Simétrico
                // con DayPlanner.build (c.559): el cursor lineal PRE-fix avanzaba sin
                // rodear reuniones y colocaba tareas rápidas ENCIMA de un compromiso
                // agendado, pisándolo al aplicarse la automatización. Las candidatas
                // (rápidas) se excluyen del bloqueo: el agrupador las RE-coloca, así su
                // hora original se libera (no se reserva). Fuente única compartida
                // [DayPlanner.fixedBusyIntervals]. (c.560)
                val quickIds = quick.mapTo(HashSet()) { it.id }
                val busy = DayPlanner.fixedBusyIntervals(mutable, date, quickIds, firstMinute, dayEndMinute, zone)
                var cursor = firstMinute.coerceAtMost(dayEndMinute)
                val updates = quick.sortedBy { it.dueAt ?: Long.MAX_VALUE }.take(8).mapNotNull { task ->
                    val duration = task.durationMinutes.coerceIn(5, 10)
                    var proposedStart = cursor
                    var proposedEnd = proposedStart + duration
                    // Rodea reuniones fijas: si el slot cae encima de un compromiso, salta
                    // a su fin y recomprueba. Sin este paso el cursor atravesaba la
                    // reunión. (c.560)
                    val placed = DayPlanner.skipBusy(proposedStart, proposedEnd, busy, dayEndMinute)
                    if (placed == null) return@mapNotNull null
                    proposedStart = placed.first
                    proposedEnd = placed.second
                    val start = DateRules.toEpochMillis(date, LocalTime.of(proposedStart / 60, proposedStart % 60), zone)
                    val end = DateRules.toEpochMillis(date, LocalTime.of(proposedEnd / 60, proposedEnd % 60), zone)
                    cursor = proposedEnd + 5
                    task.copy(
                        startAt = start,
                        // Vencimiento coherente con el slot (igual que PLAN_DAY): si el slot
                        // empieza después del due original, el due sigue al fin del slot. Evita
                        // `startAt > dueAt` que [BackupManager] rechaza al restaurar.
                        dueAt = TaskRules.dueAtForPlannedSlot(task.dueAt, start, end),
                        // Recordatorio past-safe (igual que PLAN_DAY): se respeta un aviso previo
                        // futuro, pero uno pasado se reemplaza por el default del slot futuro. Sin
                        // esto, una tarea rápida vencida agrupada en un slot futuro quedaría sin aviso.
                        reminderAt = planReminder(task.reminderAt, start, now),
                        status = TaskStatus.PLANNED,
                        updatedAt = now
                    )
                }
                AutomationPlan(updates = updates, message = "${updates.size} tareas rápidas agrupadas.", matched = updates.isNotEmpty())
            }
            AutomationAction.REVIEW_COMMITMENTS -> {
                val marker = "Ordía · revisión automática de compromisos"
                if (active.any { marker in it.details }) {
                    AutomationPlan(message = "Ya existe una revisión pendiente; no se creó un duplicado.", matched = false)
                } else {
                    AutomationPlan(
                        creates = listOf(
                            TaskEntity(
                                title = "Revisar $pendingCommitments compromisos de mensajes",
                                details = marker,
                                durationMinutes = 10,
                                status = TaskStatus.INBOX,
                                createdAt = now,
                                updatedAt = now
                            )
                        ),
                        message = "Se creó una revisión de compromisos."
                    )
                }
            }
        }
    }

    /**
     * Recordatorio para una tarea que pasa a un slot planificado: respeta un aviso previo
     * del usuario solo si sigue siendo futuro; si era pasado (ya disparó) recae al inicio
     * del slot cuando este es futuro, y a `null` cuando el slot ya pasó (sin avisos tardíos).
     * Contrato past-safe compartido por PLAN_DAY y BATCH_QUICK_TASKS; simétrico con
     * RESCHEDULE_OVERDUE y con [com.ordia.app.domain.ReminderRules.resolveReminderAt].
     */
    private fun planReminder(existing: Long?, start: Long, now: Long): Long? = when {
        existing != null && existing > now -> existing
        start > now -> start
        else -> null
    }
}
