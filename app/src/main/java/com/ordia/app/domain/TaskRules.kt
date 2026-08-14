package com.ordia.app.domain

import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.data.local.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object TaskRules {
    /** Límites de duración de una tarea dentro de un plan de día (coherentes entre plan y resumen). */
    const val MIN_PLAN_MINUTES = 10
    const val MAX_PLAN_MINUTES = 180

    /**
     * Duración planificable de una tarea, acotada a [MIN_PLAN_MINUTES, MAX_PLAN_MINUTES].
     * Fuente única de verdad: tanto DayPlanner (plan del día) como SummaryEngine
     * (badge de minutos pendientes) usan este valor, evitando que el resumen
     * muestre minutos que el plan no podría acomodar (ni una tarea sin duración
     * cuente como 0 cuando el plan la trata como [MIN_PLAN_MINUTES]).
     */
    fun plannedDuration(task: TaskEntity): Int =
        task.durationMinutes.coerceIn(MIN_PLAN_MINUTES, MAX_PLAN_MINUTES)

    /**
     * Predicado canónico de "tarea activa": no completada, no archivada, no
     * cancelada. Es la fuente única de verdad para el trio que TODA superficie
     * activa debe respetar (bandeja, What Now, planificador, resumen, guardián,
     * recordatorios, widget, asistente, backup). Centralizarlo aquí previene la
     * clase de bug recurrente en la que una ruta repetía `!completed &&
     * !archived` y OLVIDABA `status != CANCELLED`, haciendo aflorar tareas que
     * el usuario descartó (c.169: 5 rutas; c.170: búsqueda universal). Los
     * sitios que además requieren "tarea raíz" componen con
     * `it.parentTaskId == null` (no se incluye aquí porque algunas superficies
     * cuentan subtareas).
     */
    fun isActive(task: TaskEntity): Boolean =
        !task.completed && !task.archived && task.status != TaskStatus.CANCELLED

    /**
     * Siguiente tarea más importante, con la misma prioridad temporal que
     * [WhatNowEngine.suggest] (widget, asistente y "siguiente paso" del guardián
     * comparten esta lógica): lo que ocurre ahora mismo > atrasado > compromiso a
     * punto de empezar (inminente) > vence hoy > urgente > alta > bandeja; las
     * programadas para más tarde quedan al final.
     * Desempate: prioridad, fecha límite más próxima, hora prevista, orden, creación.
     */
    fun nextBestTask(
        tasks: List<TaskEntity>,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): TaskEntity? =
        tasks.asSequence()
            .filter { isActive(it) && it.parentTaskId == null }
            .sortedWith(
                compareByDescending<TaskEntity> { timeRank(it, now, zone) }
                    .thenByDescending { priorityScore(it.priority) }
                    .thenBy { it.dueAt ?: Long.MAX_VALUE }
                    .thenBy { it.startAt ?: Long.MAX_VALUE }
                    .thenBy { it.sortOrder }
                    .thenBy { it.createdAt }
            )
            .firstOrNull()

    /** Ventana en la que un compromiso programado futuro se considera "ahora mismo". */
    const val IMMINENT_WINDOW_MINUTES = 15

    /**
     * Rango temporal de una tarea respecto a [now]: el componente "qué tan
     * urgente es este instante" del ranking de decisión. Fuente única de
     * verdad compartida con [WhatNowEngine] (tarjeta What Now, asistente,
     * plan mínimo) y con [nextBestTask] (widget y fallback del ViewModel),
     * de forma que TODAS las superficies de "qué hago ahora" ordenen igual.
     *
     * El orden del `when` es deliberado y sutil (es la parte propensa a
     * divergencia silenciosa): una tarea en curso ahora mismo manda sobre
     * una atrasada; una a punto de empezar (inminente) empata con la
     * atrasada por encima de lo que vence hoy; lo programado para más
     * tarde queda último (negativo) para no robar el lugar de lo actual.
     * Centralizarlo aquí evita que una edición en una superficie deje a
     * What Now y al widget sugiriendo tareas distintas para el mismo
     * conjunto (regresión real ya documentada en c.83, antes de c.53).
     */
    fun timeRank(task: TaskEntity, now: Long, zone: ZoneId = ZoneId.systemDefault()): Int = when {
        task.status == TaskStatus.IN_PROGRESS -> 6
        isInProgressNow(task, now) -> 5
        isOverdue(task, now) -> 4
        isImminentStart(task, now) -> 4
        isScheduledLater(task, now) -> -1
        isDueToday(task, now, zone) -> 3
        task.priority == TaskPriority.URGENT -> 2
        task.priority == TaskPriority.HIGH -> 1
        else -> 0
    }

    /**
     * Compromiso ocurriendo ahora mismo: `startAt` ya comenzó y no ha rebasado
     * su duración planificada. Fuente única de verdad compartida con
     * [WhatNowEngine] (rank de "ahora mismo") y con [SummaryEngine] (no
     * sugiere posponer lo que se está ejecutando en este instante).
     */
    fun isInProgressNow(task: TaskEntity, now: Long = System.currentTimeMillis()): Boolean {
        val start = task.startAt ?: return false
        if (now < start) return false
        val duration = task.durationMinutes.coerceAtLeast(10) * 60_000L
        return now <= start + duration
    }

    /**
     * Compromiso a punto de empezar: `startAt` futuro pero dentro de
     * [IMMINENT_WINDOW_MINUTES]. Una reunión/llamada/cita que comienza en pocos
     * minutos es exactamente "qué hago ahora", aunque aún no haya arrancado: la
     * elevamos por encima de la Bandeja para no olvidarla. Las que empiezan más
     * tarde siguen como último recurso ([isScheduledLater]). Fuente única de
     * verdad compartida con [WhatNowEngine].
     */
    fun isImminentStart(task: TaskEntity, now: Long = System.currentTimeMillis()): Boolean {
        val start = task.startAt ?: return false
        return start > now && (start - now) <= IMMINENT_WINDOW_MINUTES * 60_000L
    }

    private fun isScheduledLater(task: TaskEntity, now: Long): Boolean =
        task.startAt != null && task.startAt > now

    fun isOverdue(task: TaskEntity, now: Long = System.currentTimeMillis()): Boolean =
        isActive(task) && task.dueAt?.let { it < now } == true

    fun isDueToday(task: TaskEntity, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (!isActive(task)) return false
        val due = task.dueAt ?: return false
        return Instant.ofEpochMilli(due).atZone(zone).toLocalDate() == Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    }

    fun isDueOn(task: TaskEntity, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        if (!isActive(task)) false
        else task.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == date } ?: false

    fun completionRate(tasks: List<TaskEntity>): Int {
        val relevant = tasks.filter { !it.archived && it.status != TaskStatus.CANCELLED && it.parentTaskId == null }
        if (relevant.isEmpty()) return 0
        return ((relevant.count { it.completed } * 100.0) / relevant.size).toInt()
    }

    /**
     * Tareas raíz completadas que siguen visibles (no archivadas ni descartadas).
     * Fuente única de verdad para el guardián (XP por tareas completadas) y para
     * la tarjeta "Completadas" de la pantalla Tareas: antes la tarjeta contaba
     * también las archivadas y se desincronizaba del filtro "Completadas" (que sí
     * las excluye). Compartir el predicado evita que vuelvan a divergir.
     */
    fun completedRootCount(tasks: List<TaskEntity>): Int =
        tasks.count { it.parentTaskId == null && it.completed && !it.archived && it.status != TaskStatus.CANCELLED }

    /**
     * Puntaje de prioridad compartido por todas las superficies de decisión
     * (What Now, widget/asistente, planificador). Fuente única de verdad para
     * que el desempate por prioridad sea idéntico en todos lados.
     */
    fun priorityScore(priority: TaskPriority): Int = when (priority) {
        TaskPriority.LOW -> 0; TaskPriority.NORMAL -> 1; TaskPriority.HIGH -> 2; TaskPriority.URGENT -> 3
    }

    /**
     * Traslada una tarea a "mañana a la misma hora", preservando la integridad
     * de sus tiempos relativos. Es la acción detrás de la sugerencia de
     * posposición cuando el día está saturado: una sola intención mueve la
     * tarea sin abrir el editor.
     *
     * Requiere [TaskEntity.dueAt] (sin vencimiento "mañana" no está definido y
     * añadirlo cambiaría la semántica de la tarea). Calcula el nuevo vencimiento
     * como el día siguiente al del vencimiento actual, **a la misma hora local**
     * (vía `ZonedDateTime`, correcto frente a cambios horarios/DST en lugar de
     * sumar 24 h a ciegas). Todo lo demás se desplaza por el mismo delta:
     *
     * - [TaskEntity.startAt]: se traslada `startAt + delta`, conservando la
     *   distancia inicio→vencimiento.
     * - [TaskEntity.reminderAt]: se traslada `reminderAt + delta`, conservando
     *   el offset "X min antes" exacto —crítico para recurrentes, donde
     *   [RecurrenceEngine] reutiliza `dueAt - reminderAt` en cada ocurrencia—.
     * - [TaskEntity.recurrence]/`recurrenceInterval`/`recurrenceDays` quedan
     *   intactos: se posponen ESTA instancia, no la cadencia.
     *
     * No muta la entrada; devuelve una copia con `updatedAt = now`.
     */
    fun deferToNextDay(
        task: TaskEntity,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): TaskEntity? {
        val due = task.dueAt ?: return null
        val zoned = Instant.ofEpochMilli(due).atZone(zone)
        val newDue = zoned.plusDays(1).toInstant().toEpochMilli()
        val delta = newDue - due
        return task.copy(
            dueAt = newDue,
            startAt = task.startAt?.plus(delta),
            reminderAt = task.reminderAt?.plus(delta),
            updatedAt = now
        )
    }
}
