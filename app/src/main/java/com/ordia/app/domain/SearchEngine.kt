package com.ordia.app.domain

import java.text.Normalizer
import com.ordia.app.data.local.HabitEntity
import com.ordia.app.data.local.NoteEntity
import com.ordia.app.data.local.ProjectEntity
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.ConversationEntity
import com.ordia.app.data.local.CommitmentEntity
import com.ordia.app.data.local.AutomationRuleEntity
import com.ordia.app.data.local.CommitmentReviewStatus
import com.ordia.app.data.local.TaskPriority

enum class SearchKind { TASK, PROJECT, NOTE, HABIT, CONVERSATION, COMMITMENT, AUTOMATION }

data class SearchResult(val kind: SearchKind, val id: Long, val title: String, val subtitle: String)

object SearchEngine {
    fun search(
        query: String,
        tasks: List<TaskEntity>,
        projects: List<ProjectEntity>,
        notes: List<NoteEntity>,
        habits: List<HabitEntity>,
        conversations: List<ConversationEntity> = emptyList(),
        commitments: List<CommitmentEntity> = emptyList(),
        automations: List<AutomationRuleEntity> = emptyList(),
        now: Long = System.currentTimeMillis()
    ): List<SearchResult> {
        val normalized = query.foldForSearch()
        if (normalized.isBlank()) return emptyList()
        val words = normalized.split(Regex("\\s+")).filterNot { it in STOP_WORDS }
        val wantsTasks = "tarea" in normalized || "pendiente" in normalized || "vencid" in normalized
        val wantsNotes = "nota" in normalized
        val wantsMessages = "mensaje" in normalized || "conversacion" in normalized || "chat" in normalized
        val wantsCommitments = "compromiso" in normalized
        val wantsAutomations = "automatiz" in normalized || "regla" in normalized
        val typed = wantsTasks || wantsNotes || wantsMessages || wantsCommitments || wantsAutomations
        fun matches(vararg values: String): Boolean {
            val haystack = values.joinToString(" ").foldForSearch()
            return haystack.contains(normalized) || words.isNotEmpty() && words.all(haystack::contains)
        }
        fun semanticMatches(ignored: Set<String>, vararg values: String): Boolean {
            val meaningful = words.filterNot { word -> ignored.any(word::startsWith) }
            if (meaningful.isEmpty()) return true
            val haystack = values.joinToString(" ").foldForSearch()
            return meaningful.all(haystack::contains)
        }
        return buildList {
            tasks.filter { task ->
                !task.archived && (!typed || wantsTasks) &&
                    (!normalized.contains("vencid") || TaskRules.isOverdue(task, now)) &&
                    (!normalized.contains("importante") || task.priority in setOf(TaskPriority.HIGH, TaskPriority.URGENT)) &&
                    (!normalized.contains("pendiente") || !task.completed) &&
                    (matches(task.title, task.details) || semanticMatches(TASK_TERMS, task.title, task.details))
            }.forEach {
                add(Ranked(SearchResult(SearchKind.TASK, it.id, it.title, it.details.take(90)), urgencyRank(it, now), it.dueAt ?: Long.MAX_VALUE))
            }
            projects.filter { !typed && !it.archived && matches(it.name, it.description) }.forEach {
                add(Ranked(SearchResult(SearchKind.PROJECT, it.id, it.name, it.description.take(90))))
            }
            notes.filter { (!typed || wantsNotes) && !it.archived && (matches(it.title, it.body) || semanticMatches(NOTE_TERMS, it.title, it.body)) }.forEach {
                add(Ranked(SearchResult(SearchKind.NOTE, it.id, it.title, it.body.take(90))))
            }
            habits.filter { !typed && !it.archived && matches(it.title, it.details) }.forEach {
                add(Ranked(SearchResult(SearchKind.HABIT, it.id, it.title, it.details.take(90))))
            }
            conversations.filter { (!typed || wantsMessages) && (matches(it.title, it.summary, it.participants) || semanticMatches(MESSAGE_TERMS, it.title, it.summary, it.participants)) }
                .forEach { add(Ranked(SearchResult(SearchKind.CONVERSATION, it.id, it.title, it.summary.take(90)))) }
            commitments.filter {
                (!typed || wantsCommitments || wantsMessages) &&
                    (!normalized.contains("pendiente") || it.reviewStatus == CommitmentReviewStatus.PENDING) &&
                    (matches(it.action, it.actor, it.location) || semanticMatches(COMMITMENT_TERMS, it.action, it.actor, it.location))
            }.forEach { add(Ranked(SearchResult(SearchKind.COMMITMENT, it.id, it.action, it.actor.take(90)))) }
            automations.filter { (!typed || wantsAutomations) && matches(it.name, it.instruction, it.explanation) }
                .forEach { add(Ranked(SearchResult(SearchKind.AUTOMATION, it.id, it.name, it.explanation.take(90)))) }
        }.sortedWith(
            compareBy<Ranked> { if (it.result.title.foldForSearch().startsWith(normalized)) 0 else 1 }
                .thenBy { it.urgency }
                .thenBy { it.dueAt }
                .thenBy { it.result.title.foldForSearch() }
        ).map { it.result }
    }

    /**
     * Ordena primero lo más accionable: una tarea atrasada/urgente que coincide con
     * la búsqueda sube por encima de resultados meramente alfabéticos, igual que en
     * "Qué hacer ahora". Sin pantalla nueva: solo reordena lo que ya aparece. Es una
     * heurística local honesta (sin IA simulada).
     */
    private fun urgencyRank(task: TaskEntity, now: Long): Int = when {
        TaskRules.isOverdue(task, now) && task.priority == TaskPriority.URGENT -> 0
        TaskRules.isOverdue(task, now) -> 1
        task.priority == TaskPriority.URGENT && TaskRules.isDueToday(task, now) -> 2
        task.priority == TaskPriority.URGENT -> 3
        task.priority == TaskPriority.HIGH -> 4
        TaskRules.isDueToday(task, now) -> 5
        else -> 6
    }

    private data class Ranked(
        val result: SearchResult,
        val urgency: Int = 6,
        val dueAt: Long = Long.MAX_VALUE
    )

    private val STOP_WORDS = setOf(
        "de", "del", "la", "las", "el", "los", "con", "que", "mis", "mi", "cosas", "mostrar", "muestra"
    )
    private val TASK_TERMS = setOf("tarea", "pendient", "vencid", "important")
    private val NOTE_TERMS = setOf("nota")
    private val MESSAGE_TERMS = setOf("mensaje", "conversacion", "chat")
    private val COMMITMENT_TERMS = setOf("compromiso", "pendient", "sin", "fecha")
}

internal fun String.foldForSearch(): String =
    Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
