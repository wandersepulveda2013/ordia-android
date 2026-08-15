package com.ordia.app.assistant

import com.ordia.app.data.local.CommitmentEntity
import com.ordia.app.data.local.CommitmentReviewStatus
import com.ordia.app.data.local.ConversationEntity
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.domain.DateRules
import com.ordia.app.domain.TaskRules
import com.ordia.app.domain.WhatNowEngine
import com.ordia.app.domain.foldForSearch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class AssistantAction { NONE, OPEN_PLANNER, OPEN_CONVERSATIONS, RUN_REPLAN, CREATE_NOTE, OPEN_SEARCH }

data class AssistantAnswer(
    val text: String,
    val action: AssistantAction = AssistantAction.NONE,
    val actionPayload: String = "",
    val relatedTaskIds: List<Long> = emptyList()
)

/** Asistente determinista y local; nunca necesita red ni una clave de API. */
object AssistantEngine {
    fun answer(
        request: String,
        tasks: List<TaskEntity>,
        conversations: List<ConversationEntity>,
        commitments: List<CommitmentEntity>,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): AssistantAnswer {
        val clean = request.trim().take(2_000)
        val query = clean.foldForSearch()
        if (query.isBlank()) return AssistantAnswer("Escribe qué necesitas organizar.")
        // Solo tareas raíz (parentTaskId == null): las subtareas son anidadas y
        // contarlas además del padre infla los conteos que el usuario lee ("3
        // pendientes" por un proyecto descompuesto en 2 partes, "3 vencidas" por
        // una entrega con 2 subtareas vencidas). Es la misma fuente única de
        // verdad que usan SummaryEngine, GuardianEngine y WhatNowEngine, de forma
        // que el asistente no mienta sobre cuántos compromisos reales hay.
        val active = tasks.filter { TaskRules.isActive(it) && it.parentTaskId == null }
        val overdue = active.filter { TaskRules.isOverdue(it, now) }
        val pendingCommitments = commitments.filter { it.reviewStatus == CommitmentReviewStatus.PENDING }
        return when {
            "organiza mi dia" in query || "organizar mi dia" in query || "organiza el dia" in query -> {
                val pending = if (active.size == 1) "1 tarea pendiente" else "${active.size} tareas pendientes"
                val venc = if (overdue.size == 1) "1 vencida" else "${overdue.size} vencidas"
                AssistantAnswer(
                    "Hay $pending y $venc. Puedo preparar un plan realista y reversible.",
                    AssistantAction.OPEN_PLANNER
                )
            }
            "que hago ahora" in query || "siguiente accion" in query -> {
                val suggestion = WhatNowEngine.suggest(active, now)
                if (suggestion == null) {
                    AssistantAnswer("No encuentro tareas pendientes. Puedes capturar algo nuevo o descansar.")
                } else {
                    val why = WhatNowEngine.reasonLabel(suggestion.reason)
                    // "Además, tienes N vencidas" se refiere a las vencidas DISTINTAS a la
                    // sugerida: si la propia tarea sugerida está vencida, ya lo dijimos en
                    // "está vencida" — repetir "además tienes 1 vencida" cuando es esa misma
                    // tarea confunde al usuario (¿otra? ¿cuál?).
                    val otherOverdue = overdue.count { it.id != suggestion.task.id }
                    val tail = if (otherOverdue > 0) {
                        " Además, tienes $otherOverdue vencid${if (otherOverdue == 1) "a" else "as"}."
                    } else ""
                    val minutes = TaskRules.plannedDuration(suggestion.task)
                    AssistantAnswer(
                        "Empieza por “${suggestion.task.title}”: $why. Estimo $minutes minutos.$tail",
                        relatedTaskIds = listOf(suggestion.task.id)
                    )
                }
            }
            isAgendaQuery(query) -> agendaAnswer(query, active, now)
            "que olvide" in query || "olvidado" in query || "vencid" in query -> {
                // Partición honesta: "vencid" pregunta por vencidas (dueAt pasado);
                // "qué olvidé"/"olvidado" pregunta por olvidos, y un compromiso
                // agendado cuyo hueco pasó (TaskRules.isMissedStart — el "olvido
                // silencioso") ES un olvido aunque el plazo aún no vuele. Antes esto
                // decía "No tienes tareas vencidas" frente a una llamada agendada que
                // se pasó: mentía por omisión en la superficie de recuperación. Cierra
                // la simetría con What Now (c.203) y el guardián (c.201), reusando
                // WhatNowEngine.ordered para elegir el olvido más urgente.
                val forgottenIntent = "que olvide" in query || "olvidado" in query
                if (overdue.isNotEmpty()) {
                    if (forgottenIntent) {
                        // "¿Qué olvidé?" pide recuperar QUÉ se pasó, no un conteo frío.
                        // Nombramos la vencida más urgente (mismo orden que What Now:
                        // overdue primero, luego prioridad/fecha) y dejamos el resto
                        // para reprogramar. Simétrico con la rama sin-vencidas, que
                        // nombra el missed-start en lugar de decir "no hay vencidas".
                        val top = WhatNowEngine.ordered(active, now).first { TaskRules.isOverdue(it, now) }
                        val minutes = TaskRules.plannedDuration(top)
                        val tail = if (overdue.size == 1) {
                            "Puedo reprogramarla."
                        } else {
                            "y tienes ${overdue.size - 1} más. Puedo reprogramarlas."
                        }
                        AssistantAnswer(
                            "“${top.title}” está vencida (~$minutes min) $tail",
                            AssistantAction.RUN_REPLAN,
                            relatedTaskIds = overdue.take(8).map { it.id }
                        )
                    } else {
                        val venc = if (overdue.size == 1) "1 tarea vencida" else "${overdue.size} tareas vencidas"
                        AssistantAnswer(
                            "Tienes $venc. Puedo reprogramarlas sin mostrarte una pared de alertas.",
                            AssistantAction.RUN_REPLAN,
                            relatedTaskIds = overdue.take(8).map { it.id }
                        )
                    }
                } else {
                    val missed = WhatNowEngine.ordered(active, now)
                        .firstOrNull { TaskRules.isMissedStart(it, now) }
                    if (missed == null) {
                        // Tercer olvido de Ordía: una captura arrinconada en la
                        // bandeja SIN fecha (dueAt/startAt) que lleva
                        // [TaskRules.STALE_INBOX_DAYS_THRESHOLD] o más días
                        // esperando. El guardián ya la reencuadraba (RECUPERA EL
                        // CONTROL, c.201) pero "¿qué olvidé?" la ignoraba y decía
                        // "no hay vencidas ni olvidadas" frente a una idea olvidada
                        // — mentía por omisión en la superficie de recuperación
                        // explícita. Partición honesta: vencida (dueAt) → missed-
                        // start (startAt); aquí sólo capturas SIN ambos, así no se
                        // duplica ni se simula urgencia. La más olvidada = la más
                        // antigua. Sólo para "qué olvidé", no para "vencidas".
                        if (forgottenIntent) {
                            val stale = active
                                .filter { TaskRules.isStaleInbox(it, now, zone) }
                                .maxByOrNull { TaskRules.inboxAgeDays(it, now, zone) }
                            if (stale != null) {
                                val ageLabel = DateRules.ageLabel(TaskRules.inboxAgeDays(stale, now, zone))
                                AssistantAnswer(
                                    "«${stale.title}» lleva $ageLabel en tu bandeja sin fecha. Hazla hoy, agéndala o quítala: no la dejes pasar otra vez.",
                                    relatedTaskIds = listOf(stale.id)
                                )
                            } else {
                                AssistantAnswer("No tienes tareas vencidas ni compromisos olvidados.")
                            }
                        } else {
                            AssistantAnswer("No tienes tareas vencidas.")
                        }
                    } else if (forgottenIntent) {
                        val minutes = TaskRules.plannedDuration(missed)
                        AssistantAnswer(
                            "«${missed.title}» tenía su hueco y se pasó (~$minutes min). Hazla o reagéndala.",
                            relatedTaskIds = listOf(missed.id)
                        )
                    } else {
                        AssistantAnswer("No tienes tareas vencidas.")
                    }
                }
            }
            "resume" in query && ("conversacion" in query || "mensaje" in query) ->
                AssistantAnswer(
                    "Hay ${conversations.size} conversaciones guardadas y ${pendingCommitments.size} compromisos por revisar.",
                    AssistantAction.OPEN_CONVERSATIONS
                )
            "compromiso" in query && ("sin fecha" in query || "pendiente" in query) -> {
                val undated = pendingCommitments.filter { it.dueAt == null }
                AssistantAnswer(
                    "Encontré ${undated.size} compromisos pendientes sin fecha.",
                    AssistantAction.OPEN_CONVERSATIONS
                )
            }
            "plan minimo" in query || "minimo para hoy" in query -> {
                val minimal = WhatNowEngine.ordered(active, now).take(3)
                AssistantAnswer(
                    if (minimal.isEmpty()) "Tu plan mínimo está vacío." else "Plan mínimo: " + minimal.joinToString(" · ") { it.title },
                    relatedTaskIds = minimal.map { it.id }
                )
            }
            "15 minutos" in query || "rapido" in query -> {
                val quick = WhatNowEngine.ordered(active, now).filter { it.durationMinutes <= 15 }.take(6)
                AssistantAnswer(
                    if (quick.isEmpty()) "No encuentro tareas de 15 minutos o menos." else "Puedes completar: " + quick.joinToString(" · ") { it.title },
                    relatedTaskIds = quick.map { it.id }
                )
            }
            query.startsWith("convierte esto en una nota") || query.startsWith("guardar como nota") -> {
                val content = clean.substringAfter(":", "").trim()
                if (content.isBlank()) AssistantAnswer("Añade el contenido después de dos puntos para crear la nota.")
                else AssistantAnswer("La nota está lista para guardarse: “${content.take(120)}”.", AssistantAction.CREATE_NOTE, content)
            }
            query.startsWith("busca ") || query.startsWith("muestra ") || query.startsWith("pendientes con") ->
                AssistantAnswer("Abriré la búsqueda con esa consulta.", AssistantAction.OPEN_SEARCH, clean)
            else -> AssistantAnswer(
                "Puedo organizar tu día, decirte qué hacer ahora, qué tienes mañana, mostrar lo vencido, resumir conversaciones, buscar pendientes o preparar un plan mínimo."
            )
        }
    }

    /**
     * "¿Qué tengo mañana/hoy?" — agenda a demanda. El usuario pregunta qué tiene
     * agendado para un día concreto; antes caía al mensaje genérico (debía abrir el
     * planificador y filtrar a mano). Lista las tareas raíz activas cuyo vencimiento
     * cae en ese día (reusa `TaskRules.isDueOn`/`isDueToday`, fuente única de verdad
     * compartida con la búsqueda y el planificador), ordenadas por urgencia/fecha
     * (mismo `WhatNowEngine.ordered`). Sin nueva pantalla: la superficie del
     * asistente ya existe. Determinista y local (sin IA fingida).
     */
    private fun isAgendaQuery(query: String): Boolean {
        if (!("que tengo" in query || "tengo para" in query || "que hay" in query)) return false
        return "manana" in query || "hoy" in query || "semana" in query
    }

    private fun agendaAnswer(query: String, active: List<TaskEntity>, now: Long): AssistantAnswer {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val (start, end, label) = when {
            "manana" in query -> Triple(today.plusDays(1), today.plusDays(1), "mañana")
            "hoy" in query -> Triple(today, today, "hoy")
            else -> Triple(monday, monday.plusDays(6), "esta semana")
        }
        val ranked = WhatNowEngine.ordered(active, now)
        val due = ranked.filter { isDueInRange(it, start, end, zone) }
        if (due.isEmpty()) {
            return AssistantAnswer("Para $label no tienes tareas agendadas.")
        }
        val titles = due.joinToString(" · ") { "“${it.title}”" }
        val ids = due.take(8).map { it.id }
        // Para "hoy", avisar además de las atrasadas de días anteriores (vencidas
        // antes de hoy): son parte de "lo que tienes" pendiente y el usuario las
        // olvidaría si la agenda sólo mirara el día de hoy. Coherente con el "además"
        // de "qué hago ahora". Para mañana/semana (futuro) no aplica.
        val tail = if (label == "hoy") {
            val earlierOverdue = active.count { TaskRules.isOverdue(it, now) && !TaskRules.isDueToday(it, now, zone) }
            if (earlierOverdue > 0) {
                " Además, tienes $earlierOverdue atrasad${if (earlierOverdue == 1) "a" else "as"} de días anteriores."
            } else ""
        } else ""
        val head = if (label == "hoy") "Hoy" else label.replaceFirstChar { it.uppercase() }
        return AssistantAnswer("$head: $titles.$tail", relatedTaskIds = ids)
    }

    private fun isDueInRange(task: TaskEntity, start: LocalDate, end: LocalDate, zone: ZoneId): Boolean {
        val due = task.dueAt ?: return false
        val d = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
        return d >= start && d <= end
    }
}
