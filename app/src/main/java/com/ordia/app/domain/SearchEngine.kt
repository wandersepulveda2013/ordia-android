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
 *
 * [TARDE]/[NOCHE]/[MADRUGADA] añaden búsqueda por parte del día de HOY
 * ("tarde", "esta tarde", "noche", "madrugada"): recupera lo que vence hoy
 * en esa franja sin nueva pantalla ni botón. Se excluye "mañana" (mañana) como
 * parte del día por su colisión con el scope TOMORROW, igual que hace el
 * parser en su variante compacta de parte del día.
 */
private enum class DateScope { YESTERDAY, TODAY, TOMORROW, THIS_WEEK, NEXT_WEEK, LAST_WEEK, OVERDUE, UNDATED, TARDE, NOCHE, MADRUGADA }

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
        // Un scope de fecha PURO ("hoy", "esta semana", "vencidas", "ayer"...)
        // solo aplica a entidades con fecha (tareas). Proyectos, notas,
        // hábitos, conversaciones, compromisos y automatizaciones no tienen
        // fecha que filtrar: cuando la búsqueda es solo de fecha devolverlos
        // todos (como ocurría vía matches()/semanticMatches() con textWords
        // vacío) inunda los resultados de ruido sin señal —buscar "hoy"
        // devolvía cada nota y cada proyecto aunque nada tuviera que ver con
        // hoy. Se suprimen salvo que haya palabras de contenido reales.
        val pureDateScope = dateScope != null && textWords.isEmpty()
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
            projects.filter { !typed && !it.archived && !pureDateScope && matches(it.name, it.description) }.forEach {
                add(Ranked(SearchResult(SearchKind.PROJECT, it.id, it.name, it.description.take(90))))
            }
            notes.filter { (!typed || wantsNotes) && !it.archived && !pureDateScope && (matches(it.title, it.body) || semanticMatches(NOTE_TERMS, it.title, it.body)) }.forEach {
                add(Ranked(SearchResult(SearchKind.NOTE, it.id, it.title, it.body.take(90))))
            }
            habits.filter { !typed && !it.archived && !pureDateScope && matches(it.title, it.details) }.forEach {
                add(Ranked(SearchResult(SearchKind.HABIT, it.id, it.title, it.details.take(90))))
            }
            conversations.filter { (!typed || wantsMessages) && !pureDateScope && (matches(it.title, it.summary, it.participants) || semanticMatches(MESSAGE_TERMS, it.title, it.summary, it.participants)) }
                .forEach { add(Ranked(SearchResult(SearchKind.CONVERSATION, it.id, it.title, it.summary.take(90)))) }
            commitments.filter {
                (!typed || wantsCommitments || wantsMessages) && !pureDateScope &&
                    (!normalized.contains("pendiente") || it.reviewStatus == CommitmentReviewStatus.PENDING) &&
                    (matches(it.action, it.actor, it.location) || semanticMatches(COMMITMENT_TERMS, it.action, it.actor, it.location))
            }.forEach { add(Ranked(SearchResult(SearchKind.COMMITMENT, it.id, it.action, it.actor.take(90)))) }
            automations.filter { (!typed || wantsAutomations) && !pureDateScope && matches(it.name, it.instruction, it.explanation) }
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
    private val YESTERDAY_TOKENS = setOf("ayer")
    private val WEEK_TOKENS = setOf("semana")
    // Modificadores que señalan "semana que viene"/"próxima semana": cuando
    // acompañan a "semana" el scope pasa de THIS_WEEK a NEXT_WEEK.
    private val NEXT_WEEK_TOKENS = setOf("proxima", "proximas", "viene")
    // Modificadores que señalan "semana pasada"/"última semana": cuando acompañan
    // a "semana" el scope pasa de THIS_WEEK a LAST_WEEK (recuperación de tareas).
    private val LAST_WEEK_TOKENS = setOf("pasada", "pasadas", "pasado", "pasados", "ultima", "ultimas")
    // Modificadores que acompañan a las palabras de fecha ("esta semana") y no
    // deben exigirse en el contenido de la tarea.
    private val DATE_MODIFIERS = setOf("esta", "este", "la", "el", "las", "los", "mis")

    // Tareas sin vencimiento ("sin fecha"/"sin vencimiento"/"sin día"/"sin plazo"):
    // el objetivo es recuperar lo capturado pero nunca agendado, justo lo que
    // tiende a olvidarse. Se exige "sin" acompañado de uno de estos sustantivos
    // para no activarse con "sin leche" u otras negaciones ajenas a la fecha.
    private val UNDATED_HINTS = setOf("fecha", "vencimiento", "dia", "plazo")

    // Partes del día para buscar lo que vence HOY en esa franja. "tarde",
    // "noche" y "madrugada" son inequívocas (no colisionan con otros scopes).
    // "mañana" se excluye: también significa "tomorrow" y ya activa TOMORROW.
    // Las franjas son por hora local (mismo día que TODAY), coherentes con el
    // anclaje canónico del parser (tarde≈15, noche≈21, madrugada≈04).
    private val LATE_AFTERNOON_TOKENS = setOf("tarde")
    private val NIGHT_TOKENS = setOf("noche")
    private val EARLY_MORNING_TOKENS = setOf("madrugada")

    private fun detectDateScope(words: List<String>): DateScope? = when {
        "sin" in words && UNDATED_HINTS.any { it in words } -> DateScope.UNDATED
        OVERDUE_TOKENS.any { it in words } -> DateScope.OVERDUE
        TODAY_TOKENS.any { it in words } -> DateScope.TODAY
        TOMORROW_TOKENS.any { it in words } -> DateScope.TOMORROW
        YESTERDAY_TOKENS.any { it in words } -> DateScope.YESTERDAY
        // La parte del día se evalúa DESPUÉS de hoy/mañana/ayer: así "hoy tarde"
        // o "mañana tarde" resuelven al día explícito (más amplio) en vez de
        // quedarse solo con la franja de hoy. Sin palabra de día, "tarde"/"noche"/
        // "madrugada" solas sí activan la franja de hoy.
        LATE_AFTERNOON_TOKENS.any { it in words } -> DateScope.TARDE
        NIGHT_TOKENS.any { it in words } -> DateScope.NOCHE
        EARLY_MORNING_TOKENS.any { it in words } -> DateScope.MADRUGADA
        WEEK_TOKENS.any { it in words } && NEXT_WEEK_TOKENS.any { it in words } -> DateScope.NEXT_WEEK
        WEEK_TOKENS.any { it in words } && LAST_WEEK_TOKENS.any { it in words } -> DateScope.LAST_WEEK
        WEEK_TOKENS.any { it in words } -> DateScope.THIS_WEEK
        else -> null
    }

    private fun dateScopeTokens(words: List<String>): Set<String> =
        words.filter { it in OVERDUE_TOKENS || it in TODAY_TOKENS || it in TOMORROW_TOKENS || it in YESTERDAY_TOKENS || it in WEEK_TOKENS || it in NEXT_WEEK_TOKENS || it in LAST_WEEK_TOKENS || it in DATE_MODIFIERS || (it == "sin" && UNDATED_HINTS.any { hint -> hint in words }) || it in UNDATED_HINTS || it in LATE_AFTERNOON_TOKENS || it in NIGHT_TOKENS || it in EARLY_MORNING_TOKENS }.toSet()

    private fun taskMatchesDateScope(task: TaskEntity, scope: DateScope, now: Long, zone: ZoneId): Boolean {
        if (scope == DateScope.OVERDUE) return TaskRules.isOverdue(task, now)
        // Tareas sin vencimiento: el motivo de este scope es recuperar lo pendiente
        // que nunca se agendó. Se excluyen completadas (ya resueltas) y canceladas,
        // igual que los scopes presentes/futuros; las archivadas ya se filtraron.
        if (scope == DateScope.UNDATED) return !task.completed && task.status != TaskStatus.CANCELLED && task.dueAt == null
        // Los scopes pasados ("ayer", "semana pasada") recuperan tareas ya
        // completadas: su propósito es revisar qué había en ese período. Para
        // los scopes presentes/futuros se excluyen completadas. Las canceladas
        // se excluyen siempre (no son información útil de un período pasado).
        val pastScope = scope == DateScope.YESTERDAY || scope == DateScope.LAST_WEEK
        if (task.status == TaskStatus.CANCELLED) return false
        if (!pastScope && task.completed) return false
        val due = task.dueAt ?: return false
        val zonedDue = Instant.ofEpochMilli(due).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        // Partes del día: franja horaria de HOY (presente → excluye completadas,
        // igual que TODAY). Recupera "lo que me espera esta tarde/noche" sin
        // requerir la palabra en el título. Coherente con TODAY (mismo día).
        val partOfDay = scopeBand(scope)
        if (partOfDay != null) {
            return zonedDue.toLocalDate() == today && zonedDue.hour in partOfDay
        }
        val dueDate = zonedDue.toLocalDate()
        return when (scope) {
            DateScope.YESTERDAY -> dueDate == today.minusDays(1)
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
            DateScope.NEXT_WEEK -> {
                // Próxima semana (lunes-domingo) a partir del fin de la actual.
                // daysToSunday ya incluye el fin de esta semana; +1 = lunes próximo,
                // +6 = domingo próximo.
                val daysToSunday = (7 - today.dayOfWeek.value) % 7
                val startNextWeek = today.plusDays((daysToSunday + 1).toLong())
                val endNextWeek = startNextWeek.plusDays(6)
                !dueDate.isBefore(startNextWeek) && !dueDate.isAfter(endNextWeek)
            }
            DateScope.LAST_WEEK -> {
                // Semana pasada completa (lunes-domingo) inmediatamente anterior
                // a la actual. daysToSunday ubica el domingo de esta semana; restando
                // 7 → domingo pasado, y otros 6 → lunes pasado.
                val daysToSunday = (7 - today.dayOfWeek.value) % 7
                val endLastWeek = today.plusDays((daysToSunday - 7).toLong())
                val startLastWeek = endLastWeek.minusDays(6)
                !dueDate.isBefore(startLastWeek) && !dueDate.isAfter(endLastWeek)
            }
            DateScope.OVERDUE -> TaskRules.isOverdue(task, now)
            // UNDATED se resuelve antes (return temprano); aquí es inalcanzable.
            DateScope.UNDATED -> false
            // Partes del día resueltas vía scopeBand() antes de llegar aquí.
            DateScope.TARDE -> false
            DateScope.NOCHE -> false
            DateScope.MADRUGADA -> false
        }
    }

    // Franja horaria (en horas 0-23) de cada parte del día, o null si el scope
    // no es una parte del día. Bandas por hora local del día de vencimiento.
    private fun scopeBand(scope: DateScope): IntRange? = when (scope) {
        DateScope.MADRUGADA -> 0..5
        DateScope.TARDE -> 12..17
        DateScope.NOCHE -> 18..23
        else -> null
    }
}

internal fun String.foldForSearch(): String =
    Normalizer.normalize(trim().lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
