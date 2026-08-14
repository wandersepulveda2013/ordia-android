package com.ordia.app.domain

import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import com.ordia.app.data.local.HabitEntity
import com.ordia.app.data.local.NoteEntity
import com.ordia.app.data.local.ProjectEntity
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.ConversationEntity
import com.ordia.app.data.local.CommitmentEntity
import com.ordia.app.data.local.AutomationRuleEntity
import com.ordia.app.data.local.CommitmentReviewStatus
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.data.local.TaskStatus

enum class SearchKind { TASK, PROJECT, NOTE, HABIT, CONVERSATION, COMMITMENT, AUTOMATION }

/**
 * Intención de búsqueda por fecha. Permite escribir "hoy", "mañana",
 * "esta semana" o "atrasadas"/"vencidas" y obtener las tareas de ese rango
 * aunque su título no contenga esa palabra. Es una heurística local honesta.
 */
private enum class DateScope { TODAY, TOMORROW, THIS_WEEK, OVERDUE }

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
        val dateScope = detectDateScope(words)
        // Cuando la búsqueda expresa un rango de fecha ("hoy", "mañana", ...),
        // las palabras de fecha no se exigen en el contenido: se filtra por fecha.
        val dateWords = if (dateScope != null) dateScopeTokens(words) else emptySet()
        val textWords = words.filterNot { it in dateWords }
        fun matches(vararg values: String): Boolean {
            if (dateScope != null) {
                if (textWords.isEmpty()) return true
                val haystack = values.joinToString(" ").foldForSearch()
                return textWords.all(haystack::contains)
            }
            val haystack = values.joinToString(" ").foldForSearch()
            return haystack.contains(normalized) || words.isNotEmpty() && words.all(haystack::contains)
        }
        fun semanticMatches(ignored: Set<String>, vararg values: String): Boolean {
            val source = if (dateScope != null) textWords else words
            val meaningful = source.filterNot { word -> ignored.any(word::startsWith) }
            if (meaningful.isEmpty()) return true
            val haystack = values.joinToString(" ").foldForSearch()
            return meaningful.all(haystack::contains)
        }
        val zone = ZoneId.systemDefault()
        return buildList {
            tasks.filter { task ->
                !task.archived && (!typed || wantsTasks) &&
                    (!normalized.contains("vencid") || TaskRules.isOverdue(task, now)) &&
                    (!normalized.contains("importante") || task.priority in setOf(TaskPriority.HIGH, TaskPriority.URGENT)) &&
                    (!normalized.contains("pendiente") || !task.completed) &&
                    (dateScope == null || taskMatchesDateScope(task, dateScope, now, zone)) &&
                    (matches(task.title, task.details) || semanticMatches(TASK_TERMS, task.title, task.details))
            }.forEach {
                add(Ranked(SearchResult(SearchKind.TASK, it.id, it.title, it.dueAt?.let(DateRules::formatDate) ?: it.details.take(90)), urgencyRank(it, now), it.dueAt ?: Long.MAX_VALUE))
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

    // --- Búsqueda por fecha (intención semántica) ---

    private val OVERDUE_TOKENS = setOf("atrasada", "atrasadas", "atrasado", "atrasados", "vencida", "vencidas", "vencido", "vencidos")
    private val TODAY_TOKENS = setOf("hoy")
    private val TOMORROW_TOKENS = setOf("manana")
    private val WEEK_TOKENS = setOf("semana")
    // Modificadores que acompañan a las palabras de fecha ("esta semana") y no
    // deben exigirse en el contenido de la tarea.
    private val DATE_MODIFIERS = setOf("esta", "este", "la", "el", "las", "los", "mis")

    private fun detectDateScope(words: List<String>): DateScope? = when {
        OVERDUE_TOKENS.any { it in words } -> DateScope.OVERDUE
        TODAY_TOKENS.any { it in words } -> DateScope.TODAY
        TOMORROW_TOKENS.any { it in words } -> DateScope.TOMORROW
        WEEK_TOKENS.any { it in words } -> DateScope.THIS_WEEK
        else -> null
    }

    private fun dateScopeTokens(words: List<String>): Set<String> =
        words.filter { it in OVERDUE_TOKENS || it in TODAY_TOKENS || it in TOMORROW_TOKENS || it in WEEK_TOKENS || it in DATE_MODIFIERS }.toSet()

    private fun taskMatchesDateScope(task: TaskEntity, scope: DateScope, now: Long, zone: ZoneId): Boolean {
        if (scope == DateScope.OVERDUE) return TaskRules.isOverdue(task, now)
        if (task.completed || task.status == TaskStatus.CANCELLED) return false
        val due = task.dueAt ?: return false
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val dueDate = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
        return when (scope) {
            DateScope.TODAY -> dueDate == today
            DateScope.TOMORROW -> dueDate == today.plusDays(1)
            DateScope.THIS_WEEK -> {
                // Semana de lunes a domingo (Monday=1..Sunday=7). El `% 7` es
                // crítico en domingo: `(7 - 7) % 7 = 0` → la semana termina HOY.
                // Sin él, `7 - (7 % 7) = 7` arrastraba la semana siguiente.
                val daysToSunday = (7 - today.dayOfWeek.value) % 7
                val endOfWeek = today.plusDays(daysToSunday.toLong())
                !dueDate.isBefore(today) && !dueDate.isAfter(endOfWeek)
            }
            DateScope.OVERDUE -> TaskRules.isOverdue(task, now)
        }
    }
}

internal fun String.foldForSearch(): String =
    Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
