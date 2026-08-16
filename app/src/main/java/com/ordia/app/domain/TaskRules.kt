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
     * Desempate: prioridad, compromiso cuyo hueco ya pasó ([isMissedStart] — el
     * usuario le dio hora y se le olvidó; dentro de su banda de urgencia + prioridad
     * se recupera primero), fecha límite más próxima, hora prevista, orden, creación.
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
                    .thenByDescending { isMissedStart(it, now) }
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
     * atrasada por encima de lo que vence hoy; lo programado para más tarde
     * SIN vencimiento hoy queda último (negativo) para no robar el lugar de
     * lo actual — pero si VENCE hoy, el plazo prevalece y se queda por encima
     * del inbox (c.363: isDueToday se evalúa antes que isScheduledLater, en
     * sintonía con [WhatNowEngine.reason]). Centralizarlo aquí evita que una
     * edición en una superficie deje a What Now y al widget sugiriendo tareas
     * distintas para el mismo conjunto (regresión real ya documentada en
     * c.83, antes de c.53).
     */
    fun timeRank(task: TaskEntity, now: Long, zone: ZoneId = ZoneId.systemDefault()): Int = when {
        task.status == TaskStatus.IN_PROGRESS -> 6
        isInProgressNow(task, now) -> 5
        isOverdue(task, now) -> 4
        isImminentStart(task, now) -> 4
        isDueToday(task, now, zone) -> 3
        // isScheduledLater va DESPUÉS de isDueToday (c.363): una tarea que VENCE hoy
        // pero está programada para empezar más tarde sigue siendo urgente por su
        // plazo; evaluar isScheduledLater antes la hundía a rank -1 (último recurso)
        // por debajo de una captura del inbox sin fecha (rank 0), y What Now sugería
        // una idea aleatoria en lugar de la que vence hoy — el usuario podía olvidar
        // una tarea con plazo de hoy. Así el ranking coincide con reason(), que ya
        // muestra "vence hoy" para este caso (consistencia etiqueta ↔ ranking). Lo
        // programado para más tarde SIN vencimiento hoy sigue al fondo (-1) para no
        // robar el lugar de lo actual.
        isScheduledLater(task, now) -> -1
        task.priority == TaskPriority.URGENT -> 2
        task.priority == TaskPriority.HIGH -> 1
        else -> 0
    }

    /**
     * Compromiso ocurriendo ahora mismo: `startAt` ya comenzó y no ha rebasado
     * su duración planificada. Fuente única de verdad compartida con
     * [WhatNowEngine] (rank de "ahora mismo") y con [SummaryEngine] (no
     * sugiere posponer lo que se está ejecutando en este instante).
     *
     * La ventana usa [plannedDuration] (acotada a `[MIN_PLAN_MINUTES,
     * MAX_PLAN_MINUTES]`) — la misma fuente que DayPlanner y SummaryEngine —
     * para que una tarea de duración desproporcionada (p. ej. "congreso 10
     * horas" → 600 min) no permanezca "en curso" más allá del slot que el
     * planificador le asignaría. Sin este tope, el rank de "ahora mismo"
     * silenciaba la detección de inicio olvidado ([isMissedStart]) durante
     * horas tras rebasar el bloque planificable real.
     */
    fun isInProgressNow(task: TaskEntity, now: Long = System.currentTimeMillis()): Boolean {
        val start = task.startAt ?: return false
        if (now < start) return false
        val duration = plannedDuration(task) * 60_000L
        return now <= start + duration
    }

    /**
     * Minutos de trabajo planificado que VERDADERAMENTE faltan para una tarea.
     *
     * Para una tarea sin empezar (o sin `startAt`), es su [plannedDuration]. Para una
     * tarea EN CURSO ([isInProgressNow] — su ventana `startAt..startAt+duración` está
     * activa ahora), descuenta el tiempo ya transcurrido desde `startAt`: el minuto
     * vivido trabajando ya no es carga pendiente. Acotado a `[0, plannedDuration]`.
     *
     * Fuente única de verdad compartida por [SummaryEngine] (la badge "te quedan X min"
     * y el veredicto de carga del día) y [DayPlanner] (el tamaño del bloque que el plan
     * reserva): así una tarea en curso aporta solo su tiempo restante AMBAS superficies,
     * evitando contar dos veces lo ya gastado frente a una capacidad que se mide desde
     * AHORA (`freeMinutes` hasta el fin de jornada, o la ventana del plan desde `now`).
     * Antes cada una sumaba la duración COMPLETA y el tiempo ya consumido se contaba
     * dos veces (consumido + pendiente): el día parecía más saturado de lo que era y el
     * plan sobre-reservaba, llegando a dejar fuera tareas que sí cabían.
     */
    fun remainingPlanMinutes(task: TaskEntity, now: Long = System.currentTimeMillis()): Int {
        val planned = plannedDuration(task)
        val start = task.startAt
        if (start == null || now < start || !isInProgressNow(task, now)) return planned
        val elapsedMin = ((now - start) / 60_000L).toInt()
        return (planned - elapsedMin).coerceIn(0, planned)
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

    /**
     * Compromiso planificado cuyo hueco ya pasó sin completarse — el "olvido silencioso".
     *
     * [startAt] sólo lo asignan acciones explícitas de planificación
     * (`applyBlocks`/`PLAN_DAY`/`BATCH_QUICK_TASKS`/editor): cuando el usuario le
     * dio a una tarea un hueco concreto, decidió trabajarla en ese momento. Si `now`
     * rebasó la ventana `start + duración` y la tarea sigue activa, ese compromiso se
     * le pasó. Sin esta señal cae al limbo: no es [isInProgressNow] (pasó la ventana),
     * no es [isImminentStart] (el inicio ya ocurrió), no es [isScheduledLater] (no es
     * futuro), y —si no tiene `dueAt` vencido— tampoco es [isOverdue]. En [timeRank]
     * decae al rango de pura prioridad; sin este predicado competiría como una tarea
     * cualquiera de la bandeja y el compromiso agendado se volvería invisible. Hoy lo
     * usan dos superficies de recuperación (sin añadir pantallas): el nudge del
     * guardián ([com.ordia.app.domain.GuardianEngine]) y el desempate de "qué hago
     * ahora" ([WhatNowEngine.ordered]/[nextBestTask] la elevan dentro de su banda de
     * urgencia + prioridad, y [WhatNowEngine] la etiqueta como "tenía su hueco y se
     * pasó"). Es justo el hueco de "recuperación de tareas olvidadas".
     *
     * Partición con [isOverdue] (deliberada, no redundante): si la tarea ADEMÁS tiene
     * `dueAt` vencido, es `isOverdue` quien la señala (plazo incumplido > hueco
     * incumplido); aquí se excluyen las vencidas para que el predicado describa
     * exactamente "se le pasó el turno pero el plazo aún no voló" —el caso limpiamente
     * recuperable, donde reprogramar o hacerla ahora aún evita el atraso. Una tarea sin
     * `dueAt` cuyo hueco pasó también entra: el usuario la agendó (le dio hueco) y no la
     * hizo; no hay plazo que contar como atrasado, pero sí un compromiso olvidado.
     *
     * Excluye las que el usuario está ejecutando a mano (`status == IN_PROGRESS`):
     * aunque la ventana planificada haya expirado, si la marcó en curso está sobre ella
     * y no es un olvido. Excluye también las aún dentro de su ventana
     * ([isInProgressNow]). [isActive] descarta completadas/canceladas/archivadas.
     */
    fun isMissedStart(task: TaskEntity, now: Long = System.currentTimeMillis()): Boolean {
        val start = task.startAt ?: return false
        if (!isActive(task)) return false
        if (isBeingWorkedOn(task, now)) return false
        if (isOverdue(task, now)) return false
        return now > start
    }

    /**
     * Tarea que el usuario está ejecutando ahora mismo, de forma explícita o por ventana:
     * la marcó en curso (`status == IN_PROGRESS`) o su slot `startAt..startAt+duración`
     * está activo ([isInProgressNow]). Es la noción "sacro en curso" compartida: algo en
     * este estado NO debe ser desplazado, pospuesto ni reprogramado por una automatización
     * o por el guardián, porque pisaría el trabajo activo (resetearía el estado, borraría
     * el `startAt` o empujaría el vencimiento). La usan [isMissedStart] (no señalar como
     * inicio olvidado lo que se hace ahora), [timeRank] (colocarlo arriba) y los
     * orquestadores de automatización (no mutar lo en curso). Es el OR canónico de las dos
     * señales de "en curso": mantenerlo en un solo lugar evita que una superficie olvide
     * una de las dos y deje de proteger el trabajo activo.
     */
    fun isBeingWorkedOn(task: TaskEntity, now: Long = System.currentTimeMillis()): Boolean =
        task.status == TaskStatus.IN_PROGRESS || isInProgressNow(task, now)

    fun isOverdue(task: TaskEntity, now: Long = System.currentTimeMillis()): Boolean =
        isActive(task) && task.dueAt?.let { it < now } == true

    /**
     * Días de calendario que una tarea lleva creada (desde [TaskEntity.createdAt]
     * hasta hoy), en la zona del usuario. Cuenta días completos, no millis/24h,
     * igual que el cómputo de días vencidos del guardián: así es correcta aunque se consulte a
     * primera hora y es robusta frente al horario de verano (DST), donde un "día"
     * no siempre son 24 h. Es la edad "pura" de la captura; el predicado de
     * "olvidada" ([isStaleInbox]) añade encima la condición de bandeja sin fecha.
     *
     * Fuente única de verdad compartida con [GuardianCoach] (etiqueta de edad de
     * la captura olvidada) y con el asistente ("¿qué olvidé?" recupera la
     * captura arrinconada). Centralizarla evita que dos superficies de
     * recuperación diverjan sobre cuánto lleva esperando una idea.
     */
    fun inboxAgeDays(task: TaskEntity, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Int {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return java.time.temporal.ChronoUnit.DAYS.between(
            Instant.ofEpochMilli(task.createdAt).atZone(zone).toLocalDate(),
            today
        ).toInt()
    }

    /**
     * Umbral de "olvidada" para una captura de la bandeja SIN fecha: como no
     * incumple ningún vencimiento, le damos más margen que a una vencida
     * ([GuardianCoach.FORGOTTEN_DAYS_THRESHOLD], orientado al plazo incumplido).
     * Una semana esperando sin agendar es la señal honesta de que la captura
     * quedó arrinconada. Fuente única de verdad para el guardián y el asistente.
     */
    const val STALE_INBOX_DAYS_THRESHOLD = 7

    /**
     * Captura de la bandeja "olvidada": una idea que el usuario registró, no le
     * dio fecha (`dueAt`) ni hueco (`startAt`) y lleva
     * [STALE_INBOX_DAYS_THRESHOLD] o más días esperando ([inboxAgeDays]). Es el
     * tercer olvido de Ordía, junto a [isOverdue] (plazo incumplido) e
     * [isMissedStart] (hueco incumplido): un compromiso nunca agendado también se
     * olvida. Lo usan el nudge del guardián (RECUPERA EL CONTROL) y el asistente
     * ("¿qué olvidé?").
     *
     * Partición con [isOverdue]/[isMissedStart] (deliberada): si la tarea tiene
     * `dueAt` vencido o un `startAt` que se pasó, esas señales más fuertes la
     * recuperan; aquí se exige expresamente la AUSENCIA de ambos para describir
     * exactamente "captura arrinconada" — el caso limpio donde agendarla o
     * hacerla hoy aún evita que se pierda del todo. [isActive] descarta
     * completadas/canceladas/archivadas.
     */
    fun isStaleInbox(task: TaskEntity, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Boolean =
        isActive(task) && task.dueAt == null && task.startAt == null &&
            inboxAgeDays(task, now, zone) >= STALE_INBOX_DAYS_THRESHOLD

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
     * Tareas raíz completadas HOY (según la zona del usuario). Fuente única de
     * verdad para las TRES superficies que derivan "completadas hoy": el
     * guardián ([GuardianEngine.snapshot].completedToday -> ánimo/energía/metas
     * diarias), el resumen del día ([SummaryEngine.summarize].completedToday ->
     * badge "Completadas hoy") y la tarjeta de insight ([GuardianCoach.insight]).
     *
     * Antes cada superficie re-implementaba el predicado inline y divergieron:
     * GuardianEngine contaba tareas con `completedAt` hoy PERO `completed=false`
     * (datos inconsistentes vía backup restore o caminos futuros), inflando el
     * ánimo del guardián sobre actividad que no ocurrió (IA deshonesta);
     * SummaryEngine contaba archivadas y canceladas-completadas-hoy; GuardianCoach
     * ya filtraba correctamente. Ahora todas comparten este predicado canónico,
     * idéntico a [completedRootCount] más el filtro temporal "completedAt == hoy".
     */
    fun completedTodayCount(
        tasks: List<TaskEntity>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Int {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return tasks.count { task ->
            task.parentTaskId == null &&
                task.completed &&
                !task.archived &&
                task.status != TaskStatus.CANCELLED &&
                task.completedAt?.let { DateRules.toLocalDate(it, zone) == today } == true
        }
    }

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
     *   Si el instante trasladado cae en el pasado (vencimiento muy atrasado o
     *   offset enorme) cae a [ReminderRules.defaultReminderAt] (nunca pasado),
     *   igual que el editor (c.183) y la recurrencia (c.189): un recordatorio
     *   pasado lo descarta [ReminderSync] y la tarea pospuesta se olvidaría de
     *   nuevo, justo lo que esta acción debe evitar.
     * - [TaskEntity.recurrence]/`recurrenceInterval`/`recurrenceDays` quedan
     *   intactos: se posponen ESTA instancia, no la cadencia.
     *
     * Past-safe del vencimiento: si la tarea está vencida por más de un día,
     * "el día siguiente al vencimiento" caería HOY o antes (todavía vencida), y
     * posponerla no adelantaría nada. Se avanza día a día (misma hora local)
     * hasta que el nuevo vencimiento quede en el futuro: un "posponer a mañana"
     * siempre deja la tarea fuera de lo vencido. Para tareas no vencidas el
     * resultado es exactamente +1 día (mañana), inalterado.
     *
     * No muta la entrada; devuelve una copia con `updatedAt = now`.
     */
    fun deferToNextDay(
        task: TaskEntity,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): TaskEntity? {
        val due = task.dueAt ?: return null
        var newDue = Instant.ofEpochMilli(due).atZone(zone).plusDays(1).toInstant().toEpochMilli()
        while (newDue <= now) {
            newDue = Instant.ofEpochMilli(newDue).atZone(zone).plusDays(1).toInstant().toEpochMilli()
        }
        val delta = newDue - due
        val newReminder = task.reminderAt?.let { r ->
            val translated = r + delta
            if (translated > now) translated else ReminderRules.defaultReminderAt(newDue, now)
        }
        // Inicio past-safe (simétrico al reminder de arriba y a
        // [RecurrenceEngine.pastSafeStart] c.189): el inicio trasladado
        // (`startAt + delta`) se conserva cuando sigue siendo futuro. Si cae en
        // el pasado (tarea vencida con antelación grande: el lead original es
        // mayor que el tiempo hasta el nuevo vencimiento), conservarlo tal cual
        // dejaría la tarea pospuesta como "inicio perdido" (isMissedStart) sin
        // que el usuario la hubiese empezado, y GuardianEngine/WhatNowEngine la
        // marcarían de inmediato. En ese caso se recae a un inicio útil futuro y
        // <= dueAt (invariante de backup). Antes solo el recordatorio era
        // past-safe (c.187/c.190); el inicio se trasladaba sin más.
        val newStart = task.startAt?.let { s ->
            val translated = s + delta
            if (translated > now) translated else pastSafeStart(newDue, now, due - s)
        }
        return task.copy(
            dueAt = newDue,
            startAt = newStart,
            reminderAt = newReminder,
            updatedAt = now
        )
    }

    /**
     * Vencimiento coherente al planificar una tarea en un slot `[slotStart, slotEnd]`.
     *
     * Planificar es reagendar: la tarea pasa a trabajarse en ese slot. Si el slot
     * empieza DESPUÉS del vencimiento original (tarea vencida o temprana colocada en
     * un bloque posterior), conservar el due original dejaría `startAt > dueAt`, un
     * estado que [BackupManager] rechaza al restaurar ("Una tarea comienza después
     * de su vencimiento") y que es incoherente (la tarea vencería antes de empezar).
     * En ese caso el due sigue al fin del slot: la tarea vence al terminar de
     * trabajarla, nunca antes de empezar. En el resto de casos el due previo se
     * conserva intacto (sin due → sin due; due posterior al slot → due original).
     *
     * Garantiza el invariante `startAt <= dueAt` (cuando ambos son no nulos) que
     * validan las 3 superficies de planificación: `applyBlocks` (plan/replan manual),
     * `AutomationActionPlanner.PLAN_DAY` y `BATCH_QUICK_TASKS`.
     */
    fun dueAtForPlannedSlot(existingDueAt: Long?, slotStart: Long, slotEnd: Long): Long? = when {
        existingDueAt == null -> null
        existingDueAt >= slotStart -> existingDueAt
        else -> slotEnd
    }

    /**
     * Mantiene el invariante `startAt <= dueAt` (cuando ambos son no nulos) que
     * [BackupManager] exige al restaurar ("Una tarea comienza después de su
     * vencimiento" → backup irrestaurable). El editor de tareas expone `dueAt` pero
     * NO `startAt`: al editar el vencimiento de una tarea planificada (que ya tiene
     * `startAt` por `applyBlocks`/`PLAN_DAY`/`BATCH_QUICK_TASKS`) a un instante
     * ANTERIOR a su `startAt`, conservar el `startAt` heredado de `.copy()` dejaría
     * `startAt > dueAt`. Aquí se descarta el `startAt` incoherente (`null`): la
     * tarea conserva el vencimiento que el usuario eligió explícitamente y pierde un
     * inicio que ya no tiene sentido (no es editable en el editor). En el resto de
     * casos el `startAt` se conserva intacto (sin due → startAt libre; startAt <=
     * dueAt → coherente).
     */
    fun coerceStartAt(startAt: Long?, dueAt: Long?): Long? = when {
        startAt == null || dueAt == null -> startAt
        startAt <= dueAt -> startAt
        else -> null
    }

    /**
     * Inicio past-safe para un pospuesto/reagendado cuyo `startAt` trasladado
     * quedó en el pasado. Preserva la antelación preferida del usuario
     * ([preferredLead]) cuando es posible; si no, reclampa a la mitad del tiempo
     * restante hasta el vencimiento (piso de 1 min) para que la tarea nazca "a
     * punto de empezar" en lugar de "ya perdida". Devuelve `null` si no queda
     * ventana útil antes del vencimiento. Simétrico a
     * [RecurrenceEngine.pastSafeStart] (c.189) y a [ReminderRules.defaultReminderAt].
     * Garantiza `result <= dueAt` y `result > now` (o `null`).
     */
    private fun pastSafeStart(dueAt: Long, now: Long, preferredLead: Long): Long? {
        val lead = preferredLead.coerceAtLeast(0L)
        val ideal = dueAt - lead
        if (ideal > now) return ideal
        val remaining = dueAt - now
        if (remaining <= MIN_START_LEAD_MS) return null
        val clampedLead = minOf(lead, maxOf(MIN_START_LEAD_MS, remaining / 2))
        val clamped = dueAt - clampedLead
        return if (clamped > now) clamped else null
    }

    private const val MIN_START_LEAD_MS = 60_000L
}
