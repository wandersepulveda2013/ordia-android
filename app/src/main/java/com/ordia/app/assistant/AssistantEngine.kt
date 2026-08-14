package com.ordia.app.assistant

import com.ordia.app.data.local.CommitmentEntity
import com.ordia.app.data.local.CommitmentReviewStatus
import com.ordia.app.data.local.ConversationEntity
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.domain.TaskRules
import com.ordia.app.domain.WhatNowEngine
import com.ordia.app.domain.foldForSearch

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
        now: Long = System.currentTimeMillis()
    ): AssistantAnswer {
        val clean = request.trim().take(2_000)
        val query = clean.foldForSearch()
        if (query.isBlank()) return AssistantAnswer("Escribe qué necesitas organizar.")
        val active = tasks.filter { !it.completed && !it.archived }
        val overdue = active.filter { TaskRules.isOverdue(it, now) }
        val pendingCommitments = commitments.filter { it.reviewStatus == CommitmentReviewStatus.PENDING }
        return when {
            "organiza mi dia" in query || "organizar mi dia" in query || "organiza el dia" in query ->
                AssistantAnswer(
                    "Hay ${active.size} tareas pendientes y ${overdue.size} vencidas. Puedo preparar un plan realista y reversible.",
                    AssistantAction.OPEN_PLANNER
                )
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
            "que olvide" in query || "olvidado" in query || "vencid" in query ->
                if (overdue.isEmpty()) AssistantAnswer("No tienes tareas vencidas.")
                else AssistantAnswer(
                    "Tienes ${overdue.size} tareas vencidas. Puedo reprogramarlas sin mostrarte una pared de alertas.",
                    AssistantAction.RUN_REPLAN,
                    relatedTaskIds = overdue.take(8).map { it.id }
                )
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
                "Puedo organizar tu día, decirte qué hacer ahora, mostrar lo vencido, resumir conversaciones, buscar pendientes o preparar un plan mínimo."
            )
        }
    }
}
