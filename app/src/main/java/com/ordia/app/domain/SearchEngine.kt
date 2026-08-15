package com.ordia.app.domain

import java.text.Normalizer
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import com.ordia.app.data.local.HabitEntity
import com.ordia.app.data.local.NoteEntity
import com.ordia.app.data.local.ProjectEntity
import com.ordia.app.data.local.RoutineEntity
import com.ordia.app.data.local.RoutineStepEntity
import com.ordia.app.data.local.TagEntity
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskTagCrossRef
import com.ordia.app.data.local.ConversationEntity
import com.ordia.app.data.local.CommitmentEntity
import com.ordia.app.data.local.AutomationRuleEntity
import com.ordia.app.data.local.CommitmentReviewStatus
import com.ordia.app.data.local.RecurrenceFrequency
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.data.local.TaskStatus

enum class SearchKind { TASK, PROJECT, NOTE, HABIT, ROUTINE, CONVERSATION, COMMITMENT, AUTOMATION }

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
private enum class DateScope { YESTERDAY, TODAY, TOMORROW, THIS_WEEK, NEXT_WEEK, LAST_WEEK, THIS_MONTH, NEXT_MONTH, LAST_MONTH, OVERDUE, MISSED, UNDATED, TARDE, NOCHE, MADRUGADA, WEEKDAY }

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
        routines: List<RoutineEntity> = emptyList(),
        routineSteps: List<RoutineStepEntity> = emptyList(),
        tags: List<TagEntity> = emptyList(),
        taskTags: List<TaskTagCrossRef> = emptyList(),
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
        // "alta prioridad"/"prioridad alta" → exactamente HIGH; "baja
        // prioridad"/"prioridad baja" → exactamente LOW. Simétrico a
        // "importante" (HIGH+URGENT) y "urgente" (URGENT): permite recuperar por
        // nivel de prioridad sin recorrer la lista. Se exige la palabra
        // "prioridad" como desambiguador: así "alta" sola no dispara (alta
        // médica, alta en el sistema) ni "baja" sola (baja del auto). La
        // detección es por PALABRA (no subcadena) para evitar falsos positivos
        // como "saltar prioridad" o "exaltar prioridad" (la subcadena "alta"
        // vive dentro de "saltar"/"exaltar"). Es una heurística local honesta,
        // no un botón ni una pantalla nueva.
        val hasPriorityWord = "prioridad" in words || "prioridades" in words
        val hasHighPriorityIntent = hasPriorityWord && ("alta" in words || "altas" in words)
        val hasLowPriorityIntent = hasPriorityWord && ("baja" in words || "bajas" in words)
        // "completadas"/"hechas"/"terminadas" recuperan las tareas ya terminadas
        // aunque su título no contenga esa palabra, simétrico a "pendiente" (no
        // terminadas), "urgente" e "importante". Es la recuperación de "¿qué
        // terminé?": sin este filtro solo aparecían si "completad..." estaba en el
        // título. Detección por PALABRA (no subcadena) para excluir el infinitivo
        // "completar"/"terminar" (acción por hacer, no hecha) y evitar colisiones
        // como "hechizo". Heurística local honesta: sin botón ni pantalla nueva.
        val wantsCompleted = COMPLETED_TOKENS.any { it in words }
        val completedTerms = if (wantsCompleted) COMPLETED_TOKENS.filter { it in words }.toSet() else emptySet()
        // "marcadas"/"destacadas" (y masculino) recuperan las tareas que el usuario
        // marcó como importantes (flagged), aunque su título no contenga esa palabra,
        // simétrico a "completadas"/"urgente"/"importante". La marca es la señal que
        // el usuario dejó para encontrar algo después; sin este filtro, una tarea
        // marcada cuyo título no dice "marcada" era irrecuperable por búsqueda.
        // Detección por PALABRA (no subcadena) para excluir el verbo "marcar"
        // (acción por hacer) y evitar colisiones como "remarcable"/"desmarcar".
        val wantsFlagged = FLAGGED_TOKENS.any { it in words }
        val flaggedTerms = if (wantsFlagged) FLAGGED_TOKENS.filter { it in words }.toSet() else emptySet()
        // "fijadas" (notas pinned) — análogo de "marcadas" para tareas.
        val wantsPinned = PINNED_TOKENS.any { it in words }
        val pinnedTerms = if (wantsPinned) PINNED_TOKENS.filter { it in words }.toSet() else emptySet()
        // "repetitivas"/"recurrentes" (tareas con recurrence) — análogo de
        // "marcadas"/"completadas" para el atributo de recurrencia.
        val wantsRecurring = RECURRING_TOKENS.any { it in words }
        val recurringTerms = if (wantsRecurring) RECURRING_TOKENS.filter { it in words }.toSet() else emptySet()
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
        // Palabras que describen la intención de prioridad ("prioridad",
        // "alta"/"baja") no deben exigirse en el contenido de la tarea: son
        // filtros léxicos, igual que "urgente"/"importante". Se descartan solo
        // cuando hay intención de prioridad para no romper búsquedas de
        // contenido como "baja del auto" (sin "prioridad").
        val priorityTerms = if (hasHighPriorityIntent || hasLowPriorityIntent) {
            setOf("prioridad", "alta", "baja")
        } else emptySet()
        fun semanticMatches(ignored: Set<String>, vararg values: String): Boolean {
            val source = if (dateScope != null) textWords else words
            val meaningful = source.filterNot { word -> ignored.any(word::startsWith) }
            if (meaningful.isEmpty()) return true
            val haystack = values.joinToString(" ").foldForSearch()
            return meaningful.all(haystack::contains)
        }
        val zone = ZoneId.systemDefault()
        // Búsqueda por día de la semana ("lunes", "viernes"...): recupera las
        // tareas que vencen ese día de la semana aunque su título no lo diga, sin
        // nueva pantalla ni botón. Es la recuperación natural de "¿qué tengo el
        // viernes?" llevada a la búsqueda universal, simétrica a "hoy"/"mañana"/
        // "esta semana". Resolución idéntica al parser de captura
        // ([NaturalTaskParser] nextWeekdayOrSame/nextWeekday) para que buscar y
        // capturar signifiquen lo mismo: "lunes" dicho un lunes incluye hoy; con
        // modificador "próximo"/"que viene" salta al estricto siguiente. Un día
        // dicho a mitad de semana resuelve a su próxima ocurrencia (hacia adelante),
        // que es la lectura útil para "qué me espera ese día".
        val weekdayTarget = if (dateScope == DateScope.WEEKDAY) resolveWeekdayTarget(words, now, zone) else null
        // Mapa de proyectos activos por id: permite que una tarea o nota que pertenece
        // a un proyecto sea recuperada al buscar por el nombre del proyecto, aunque su
        // título no lo contenga. Así la relación tarea↔proyecto (y nota↔proyecto) se
        // vuelve visible en la búsqueda universal, sin nueva pantalla ni botón: buscar
        // "mudanza" encuentra las tareas "comprar cajas" si viven en el proyecto
        // "Mudanza". Es recuperación de información importante vía contexto.
        val projectById = projects.filterNot { it.archived }.associateBy { it.id }
        fun projectHaystack(projectId: Long?): Array<String> {
            val p = projectId?.let(projectById::get) ?: return emptyArray()
            return if (p.description.isEmpty()) arrayOf(p.name) else arrayOf(p.name, p.description)
        }
        // Tareas raíz indexadas por id: permite que una subtarea sea recuperada al
        // buscar el título/detalle de su tarea padre, aunque el de la propia
        // subtarea no contenga esa palabra. Así la relación subtarea↔padre (que la
        // UI ya explota anidándolas) se vuelve visible en la búsqueda universal,
        // sin nueva pantalla ni botón: buscar "mudanza" encuentra la subtarea
        // "comprar cajas" si su padre se llama "Mudanza". Simétrico a la
        // membresía de proyecto (relación tarea↔proyecto) y a "marcadas"/
        // "completadas" (atributo). Recuperación de información importante vía
        // contexto de jerarquía.
        val taskById = tasks.associateBy { it.id }
        fun parentHaystack(task: TaskEntity): Array<String> {
            val parent = task.parentTaskId?.let(taskById::get) ?: return emptyArray()
            return arrayOf(parent.title) + if (parent.details.isEmpty()) emptyArray() else arrayOf(parent.details)
        }
        // Pasos de rutina indexados por routineId: permite que una rutina sea
        // recuperada al buscar el título de cualquiera de sus pasos, aunque el
        // nombre o la descripción de la rutina no contengan esa palabra. Así la
        // relación rutina↔pasos (que la UI ya explota listándolos) se vuelve
        // visible en la búsqueda universal, sin nueva pantalla ni botón: buscar
        // "dientes" encuentra la rutina "Noche" si tiene el paso "lavarme los
        // dientes". Simétrico a la membresía de proyecto (relación
        // tarea↔proyecto) y a la jerarquía subtarea↔padre. Recuperación de
        // información importante vía contexto.
        val stepsByRoutine = routineSteps.groupBy { it.routineId }
        fun stepHaystack(routineId: Long): Array<String> =
            stepsByRoutine[routineId]?.map { it.title }?.toTypedArray() ?: emptyArray()
        // Etiquetas indexadas por taskId: permite que una tarea sea recuperada al
        // buscar el nombre de cualquiera de sus etiquetas, aunque su título o
        // detalle no lo contengan. Así la relación tarea↔etiqueta (que la UI ya
        // explota con chips de color) se vuelve visible en la búsqueda universal,
        // sin nueva pantalla ni botón: buscar "trabajo" encuentra la tarea
        // "Llamar al cliente" si lleva la etiqueta "trabajo". Simétrico a la
        // membresía de proyecto (c.159), a la jerarquía subtarea↔padre (c.162) y
        // a los pasos de rutina (c.195). Recuperación de información importante
        // vía contexto de etiquetado.
        val tagById = tags.associateBy { it.id }
        val tagsByTask = taskTags.groupBy { it.taskId }
        fun tagHaystack(taskId: Long): Array<String> =
            tagsByTask[taskId]?.mapNotNull { it.tagId.let(tagById::get)?.name }?.toTypedArray() ?: emptyArray()
        return buildList {
            tasks.filter { task ->
                val ph = projectHaystack(task.projectId)
                val pa = parentHaystack(task)
                val th = tagHaystack(task.id)
                !task.archived && task.status != TaskStatus.CANCELLED && (!typed || wantsTasks) &&
                    (!normalized.contains("vencid") || TaskRules.isOverdue(task, now)) &&
                    (!normalized.contains("importante") || task.priority in setOf(TaskPriority.HIGH, TaskPriority.URGENT)) &&
                    (!normalized.contains("urgente") || task.priority == TaskPriority.URGENT) &&
                    (!hasHighPriorityIntent || task.priority == TaskPriority.HIGH) &&
                    (!hasLowPriorityIntent || task.priority == TaskPriority.LOW) &&
                    (!normalized.contains("pendiente") || !task.completed) &&
                    (!wantsCompleted || task.completed) &&
                    (!wantsFlagged || task.flagged) &&
                    (!wantsRecurring || task.recurrence != RecurrenceFrequency.NONE) &&
                    (dateScope == null || taskMatchesDateScope(task, dateScope, now, zone, anchorOnCompleted = wantsCompleted, weekdayTarget = weekdayTarget)) &&
                    (matches(task.title, task.details, *ph, *pa, *th) || semanticMatches(TASK_TERMS + priorityTerms + completedTerms + flaggedTerms + recurringTerms, task.title, task.details, *ph, *pa, *th))
            }.forEach {
                add(Ranked(SearchResult(SearchKind.TASK, it.id, it.title, it.dueAt?.let(DateRules::formatDate) ?: it.details.take(90)), urgencyRank(it, now), it.dueAt ?: Long.MAX_VALUE))
            }
            projects.filter { !typed && !it.archived && !pureDateScope && matches(it.name, it.description) }.forEach {
                add(Ranked(SearchResult(SearchKind.PROJECT, it.id, it.name, it.description.take(90))))
            }
            notes.filter { (!typed || wantsNotes) && !it.archived && !pureDateScope && (!wantsPinned || it.pinned) }.filter {
                val ph = projectHaystack(it.projectId)
                matches(it.title, it.body, *ph) || semanticMatches(NOTE_TERMS + pinnedTerms, it.title, it.body, *ph)
            }.forEach {
                add(Ranked(SearchResult(SearchKind.NOTE, it.id, it.title, it.body.take(90))))
            }
            habits.filter { !typed && !it.archived && !pureDateScope && matches(it.title, it.details) }.forEach {
                add(Ranked(SearchResult(SearchKind.HABIT, it.id, it.title, it.details.take(90))))
            }
            routines.filter { !typed && !it.archived && !pureDateScope }.filter {
                val sh = stepHaystack(it.id)
                matches(it.name, it.description, *sh)
            }.forEach { r ->
                // Subtítulo útil aunque la rutina no tenga descripción: los
                // primeros pasos unidos por " · ", recortados. Reusa datos
                // existentes, sin nueva cadena ni pantalla.
                val subtitle = r.description.ifBlank {
                    stepsByRoutine[r.id]?.joinToString(" · ") { s -> s.title } ?: ""
                }.take(90)
                add(Ranked(SearchResult(SearchKind.ROUTINE, r.id, r.name, subtitle)))
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
    private val TASK_TERMS = setOf("tarea", "pendient", "vencid", "important", "urgente")
    private val NOTE_TERMS = setOf("nota")
    private val MESSAGE_TERMS = setOf("mensaje", "conversacion", "chat")
    private val COMMITMENT_TERMS = setOf("compromiso", "pendient", "sin", "fecha")

    // --- Búsqueda por fecha (intención semántica) ---

    private val OVERDUE_TOKENS = setOf("atrasada", "atrasadas", "atrasado", "atrasados", "vencida", "vencidas", "vencido", "vencidos")
    // "olvidadas"/"olvidados" recupera lo que el usuario olvidó: una tarea
    // vencida (plazo incumplido) O una cuyo hueco planificado ya pasó sin
    // completarse ([TaskRules.isMissedStart] — el "olvido silencioso"). Antes
    // estas últimas eran irrecuperables por búsqueda: no son "vencidas" (sin
    // dueAt o con dueAt futuro) y su título no dice "olvidada". Es el tema #1 de
    // recuperación del producto (GuardianCoach "RECUPERA EL CONTROL",
    // WhatNowEngine "tenía su hueco y se pasó") llevado a la superficie de
    // búsqueda universal, sin nueva pantalla. isMissedStart excluye por
    // definición a las vencidas, así que la unión con isOverdue no duplica.
    // Detección por palabra exacta (participio, no el infinitivo "olvidar" ni
    // el sustantivo "olvido") para no activarse con "olvidar hacer X".
    private val MISSED_TOKENS = setOf("olvidada", "olvidadas", "olvidado", "olvidados")
    // Formas del participio "completado/hecho/terminado/finalizado/acabado" (no el
    // infinitivo "completar"/"terminar"). Detectadas por palabra exacta.
    private val COMPLETED_TOKENS = setOf(
        "completada", "completadas", "completado", "completados",
        "hecha", "hechas", "hecho", "hechos",
        "terminada", "terminadas", "terminado", "terminados",
        "finalizada", "finalizadas", "finalizado", "finalizados",
        "acabada", "acabadas", "acabado", "acabados"
    )
    // "marcadas"/"destacadas" recupera las tareas que el usuario marcó (flagged).
    // Formas del participio (no el infinitivo "marcar"/"destacar"). Coincide con la
    // etiqueta de la UI ("Marcada") y el filtro "Importantes" que ya existe.
    private val FLAGGED_TOKENS = setOf(
        "marcada", "marcadas", "marcado", "marcados",
        "destacada", "destacadas", "destacado", "destacados"
    )
    // "fijadas" recupera las NOTAS que el usuario fijó (pinned). Es el análogo de
    // "marcadas" para tareas: la fijación es la señal que el usuario dejó (UI:
    // "Fijar"/"Desfijar") para encontrar algo después. Sin este filtro, una nota
    // fijada cuyo contenido no dijera "fijada" era irrecuperable por búsqueda
    // universal. Vocabulario propio (no "marcadas", que es de tareas) para honrar
    // la etiqueta real de la UI y evitar ruido cruzado. Formas del participio
    // (no el infinitivo "fijar"). Detectadas por palabra exacta.
    private val PINNED_TOKENS = setOf(
        "fijada", "fijadas", "fijado", "fijados"
    )
    // "repetitivas"/"recurrentes" recupera las TAREAS que se repiten
    // (recurrence != NONE: horaria/diaria/semanal/mensual/anual), aunque su título no
    // contenga esa palabra. Es el análogo de "marcadas"/"completadas" para el
    // atributo de recurrencia: la repetición es la señal que el usuario dejó
    // (UI: "Cambiar repetición"/"No repetir"/"Cada mes") para auditar sus
    // compromisos periódicos (renta, gimnasio, pagos). Sin este filtro, una
    // tarea recurrente "Pagar renta" era irrecuperable al buscar "recurrentes"
    // salvo que su título dijera literalmente esa palabra. Vocabulario de
    // ADJETIVO (no el sustantivo "repetición", que sí aparece en títulos de
    // tareas únicas como "repetición del examen") para minimizar colisiones y
    // evitar excluir tareas únicas con esa palabra. Detectadas por palabra
    // exacta (no el infinitivo "repetir").
    private val RECURRING_TOKENS = setOf(
        "repetitiva", "repetitivas", "repetitivo", "repetitivos",
        "recurrente", "recurrentes"
    )
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

    // --- Búsqueda por mes ("este mes"/"próximo mes"/"mes que viene"/"mes pasado") ---
    // Simétrico al scope de semana: recuperar lo que vence en un mes natural sin
    // recorrer la lista. "mes" SUELTA no activa scope (es ambigua: ¿este mes? ¿el
    // concepto "mes"?); se exige un calificador (este/próximo/pasado/viene) para
    // que "resumen del mes" siga siendo búsqueda por contenido.
    private val MONTH_TOKENS = setOf("mes")
    private val NEXT_MONTH_TOKENS = setOf("proximo", "proximos", "proxima", "proximas", "viene")
    private val LAST_MONTH_TOKENS = setOf("pasada", "pasadas", "pasado", "pasados", "ultima", "ultimas", "ultimo", "ultimos")

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

    // Días de la semana para búsqueda universal ("lunes", "viernes"...): recuperan
    // las tareas que vencen ese día sin exigirlo en el título, igual que "hoy" o
    // "esta semana". Tokens sin acento (foldForSearch): miércoles→miercoles,
    // sábado→sabado. Mapa a DayOfWeek (ISO lun=1..dom=7) compartido con la
    // resolución, para que detección y cómputo de fecha usen la misma fuente.
    private val WEEKDAY_TOKENS = setOf("lunes", "martes", "miercoles", "jueves", "viernes", "sabado", "domingo")
    private val WEEKDAY_BY_TOKEN = mapOf(
        "lunes" to DayOfWeek.MONDAY,
        "martes" to DayOfWeek.TUESDAY,
        "miercoles" to DayOfWeek.WEDNESDAY,
        "jueves" to DayOfWeek.THURSDAY,
        "viernes" to DayOfWeek.FRIDAY,
        "sabado" to DayOfWeek.SATURDAY,
        "domingo" to DayOfWeek.SUNDAY
    )
    // Modificador de "próximo"/"que viene"/"siguiente"/"posterior" para saltar al
    // día estricto siguiente (excluye hoy). Coincide con el parser de captura
    // (NaturalTaskParser nextExplicit). "que" es stop word y ya se elimina antes,
    // así que "viene" basta para detectar "lunes que viene".
    private val WEEKDAY_NEXT_MODIFIERS = setOf("proximo", "proximos", "proxima", "proximas", "viene", "siguiente", "siguientes", "posterior", "posteriores")

    private fun detectDateScope(words: List<String>): DateScope? = when {
        "sin" in words && UNDATED_HINTS.any { it in words } -> DateScope.UNDATED
        OVERDUE_TOKENS.any { it in words } -> DateScope.OVERDUE
        MISSED_TOKENS.any { it in words } -> DateScope.MISSED
        TODAY_TOKENS.any { it in words } -> DateScope.TODAY
        TOMORROW_TOKENS.any { it in words } -> DateScope.TOMORROW
        YESTERDAY_TOKENS.any { it in words } -> DateScope.YESTERDAY
        // Día de la semana suelto o con modificador. Se evalúa antes que las partes
        // del día para que "sábado" (y "viernes tarde") se resuelvan al día de la
        // semana y no a la franja de hoy; "tarde"/"noche" solas siguen llegando aquí
        // porque ningún weekday está presente. No colisiona con hoy/mañana/ayer/
        // semana/mes (tokens distintos). Resolución estricta-vs-inclusiva en
        // resolveWeekdayTarget, no aquí: el scope solo indica "es un weekday".
        WEEKDAY_TOKENS.any { it in words } -> DateScope.WEEKDAY
        // La parte del día se evalúa DESPUÉS de hoy/mañana/ayer: así "hoy tarde"
        // o "mañana tarde" resuelven al día explícito (más amplio) en vez de
        // quedarse solo con la franja de hoy. Sin palabra de día, "tarde"/"noche"/
        // "madrugada" solas sí activan la franja de hoy.
        LATE_AFTERNOON_TOKENS.any { it in words } -> DateScope.TARDE
        NIGHT_TOKENS.any { it in words } -> DateScope.NOCHE
        EARLY_MORNING_TOKENS.any { it in words } -> DateScope.MADRUGADA
        // Mes: "este mes"/"próximo mes"/"mes que viene"/"mes pasado". Requiere
        // "mes" + un calificador para no activarse con "mes" sola (ambigua) ni
        // con "resumen del mes" (búsqueda de contenido). "viene" señala próximo
        // ("mes que viene"); "pasado"/"última" señalan el mes anterior. El
        // modificador "este"/"esta" señala el mes en curso.
        MONTH_TOKENS.any { it in words } && NEXT_MONTH_TOKENS.any { it in words } -> DateScope.NEXT_MONTH
        MONTH_TOKENS.any { it in words } && LAST_MONTH_TOKENS.any { it in words } -> DateScope.LAST_MONTH
        MONTH_TOKENS.any { it in words } && ("este" in words || "esta" in words) -> DateScope.THIS_MONTH
        WEEK_TOKENS.any { it in words } && NEXT_WEEK_TOKENS.any { it in words } -> DateScope.NEXT_WEEK
        WEEK_TOKENS.any { it in words } && LAST_WEEK_TOKENS.any { it in words } -> DateScope.LAST_WEEK
        WEEK_TOKENS.any { it in words } -> DateScope.THIS_WEEK
        else -> null
    }

    private fun dateScopeTokens(words: List<String>): Set<String> =
        words.filter { it in OVERDUE_TOKENS || it in MISSED_TOKENS || it in TODAY_TOKENS || it in TOMORROW_TOKENS || it in YESTERDAY_TOKENS || it in WEEK_TOKENS || it in NEXT_WEEK_TOKENS || it in LAST_WEEK_TOKENS || it in MONTH_TOKENS || it in NEXT_MONTH_TOKENS || it in LAST_MONTH_TOKENS || it in DATE_MODIFIERS || (it == "sin" && UNDATED_HINTS.any { hint -> hint in words }) || it in UNDATED_HINTS || it in LATE_AFTERNOON_TOKENS || it in NIGHT_TOKENS || it in EARLY_MORNING_TOKENS || it in WEEKDAY_TOKENS || it in WEEKDAY_NEXT_MODIFIERS }.toSet()

    /**
     * Resuelve el día calendario objetivo de un scope WEEKDAY desde la consulta
     * original (sin normalizar): extrae el primer token de día de la semana y
     * decide entre inclusivo (incluye hoy si hoy es ese día) y estricto (salta al
     * siguiente, con "próximo"/"que viene"). Semántica idéntica al parser de
     * captura ([NaturalTaskParser] nextWeekdayOrSame/nextWeekday) para que buscar
     * y capturar signifiquen lo mismo. Devuelve null si por algún motivo no hay
     * token (no debería ocurrir cuando el scope es WEEKDAY, pero se defiende).
     */
    private fun resolveWeekdayTarget(words: List<String>, now: Long, zone: ZoneId): LocalDate? {
        val token = words.firstOrNull { it in WEEKDAY_TOKENS } ?: return null
        val target = WEEKDAY_BY_TOKEN[token] ?: return null
        val today = DateRules.toLocalDate(now, zone)
        val strict = WEEKDAY_NEXT_MODIFIERS.any { it in words }
        val delta = (target.value - today.dayOfWeek.value + 7) % 7
        val days = if (strict) (if (delta == 0) 7 else delta).toLong() else delta.toLong()
        return today.plusDays(days)
    }

    private fun taskMatchesDateScope(
        task: TaskEntity,
        scope: DateScope,
        now: Long,
        zone: ZoneId,
        anchorOnCompleted: Boolean = false,
        weekdayTarget: LocalDate? = null
    ): Boolean {
        if (scope == DateScope.OVERDUE) return TaskRules.isOverdue(task, now)
        // "olvidadas": unión de lo vencido (plazo incumplido) y lo cuyo hueco
        // planificado ya pasó sin completarse (olvido silencioso). isMissedStart
        // ya excluye completadas/canceladas/en-curso/vencidas, así que la unión
        // con isOverdue es limpia (sin duplicados). Se resuelve antes que el
        // anclaje en completedAt: una tarea olvidada NO es "completada hoy".
        if (scope == DateScope.MISSED) return TaskRules.isMissedStart(task, now) || TaskRules.isOverdue(task, now)
        // Tareas sin vencimiento: el motivo de este scope es recuperar lo pendiente
        // que nunca se agendó. Se excluyen completadas (ya resueltas) y canceladas,
        // igual que los scopes presentes/futuros; las archivadas ya se filtraron.
        if (scope == DateScope.UNDATED) return !task.completed && task.status != TaskStatus.CANCELLED && task.dueAt == null
        // Búsqueda por día de la semana: la fecha objetivo ya está resuelta en
        // search() (resolveWeekdayTarget) con la misma semántica que el parser de
        // captura. Compara el día calendario (sin hora) del dueAt —o del
        // completedAt bajo anclaje "completadas lunes"— contra ese día. Se excluyen
        // canceladas siempre y, para la lectura hacia adelante (día futuro/hoy),
        // las completadas: una tarea ya terminada no es "lo que tengo el viernes".
        // La nota/task sin dueAt no aporta fecha y por tanto no entra (su título
        // pudo mencionar el día, pero la fuente de verdad de "cuándo" es dueAt,
        // igual que en "hoy"/"mañana"; quien escribió "lunes" en el título quería
        // fijar una fecha y el parser existe para convertirla en dueAt).
        if (scope == DateScope.WEEKDAY) {
            val target = weekdayTarget ?: return false
            if (task.status == TaskStatus.CANCELLED) return false
            if (anchorOnCompleted) {
                val completedAt = task.completedAt ?: return false
                return DateRules.toLocalDate(completedAt, zone) == target
            }
            if (task.completed) return false
            val due = task.dueAt ?: return false
            return DateRules.toLocalDate(due, zone) == target
        }
        // Los scopes pasados ("ayer", "semana pasada", "mes pasado") recuperan
        // tareas ya completadas: su propósito es revisar qué había en ese período.
        // Para los scopes presentes/futuros se excluyen completadas. Las canceladas
        // se excluyen siempre (no son información útil de un período pasado).
        val pastScope = scope == DateScope.YESTERDAY || scope == DateScope.LAST_WEEK || scope == DateScope.LAST_MONTH
        if (task.status == TaskStatus.CANCELLED) return false
        // Cuando el usuario busca "completadas <scope presente>" el anclaje pasa a
        // completedAt (cuándo la terminó) y NO se excluyen completadas: la lectura
        // natural de "completadas hoy" es "qué terminé hoy", no "qué vencía hoy".
        // Si la tarea está marcada completed pero sin completedAt, no hay fecha de
        // terminación que anclar → no entra (no se inventa una fecha).
        if (anchorOnCompleted) {
            val completedAt = task.completedAt ?: return false
            return anchorMatchesScope(scope, completedAt, now, zone, fullCalendarWeek = true)
        }
        if (!pastScope && task.completed) return false
        val due = task.dueAt ?: return false
        // Partes del día: franja horaria de HOY (presente → excluye completadas,
        // igual que TODAY). Recupera "lo que me espera esta tarde/noche" sin
        // requerir la palabra en el título. Coherente con TODAY (mismo día).
        val partOfDay = scopeBand(scope)
        if (partOfDay != null) {
            val zonedDue = Instant.ofEpochMilli(due).atZone(zone)
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            return zonedDue.toLocalDate() == today && zonedDue.hour in partOfDay
        }
        return anchorMatchesScope(scope, due, now, zone, fullCalendarWeek = false)
    }

    // Comprueba si un instante (epoch) cae dentro del rango calendario del scope.
    // Anclaje canónico: lunes-domingo para semanas, mes natural para meses. Sin
    // día-fecha relativo (ayer/mañana) ni "hoy" absoluto, siempre dentro de la
    // semana/mes en curso relativo a `now`. Usado para dueAt (tareas pendientes)
    // y para completedAt (tareas terminadas por el usuario en un período).
    // fullCalendarWeek: cuando es true (anclaje en completedAt), THIS_WEEK abarca
    // la semana calendario completa (lunes-domingo) en vez de hoy→domingo: la
    // lectura de "completadas esta semana" es "qué terminé esta semana", que
    // incluye lo terminado el lunes aunque hoy sea jueves.
    private fun anchorMatchesScope(scope: DateScope, anchorEpoch: Long, now: Long, zone: ZoneId, fullCalendarWeek: Boolean): Boolean {
        val zoned = Instant.ofEpochMilli(anchorEpoch).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val date = zoned.toLocalDate()
        val partOfDay = scopeBand(scope)
        if (partOfDay != null) {
            return date == today && zoned.hour in partOfDay
        }
        return when (scope) {
            DateScope.YESTERDAY -> date == today.minusDays(1)
            DateScope.TODAY -> date == today
            DateScope.TOMORROW -> date == today.plusDays(1)
            DateScope.THIS_WEEK -> {
                // Semana de lunes a domingo (Monday=1..Sunday=7). Para dueAt el
                // inicio es HOY (lo que me espera esta semana); para completedAt
                // (fullCalendarWeek) el inicio es el LUNES de esta semana, así
                // "completadas esta semana" recupera lo terminado desde el lunes.
                // El `% 7` es crítico en domingo: `(7 - 7) % 7 = 0` → la semana
                // termina HOY. Sin él, `7 - (7 % 7) = 7` arrastraba la siguiente.
                val daysToSunday = (7 - today.dayOfWeek.value) % 7
                val endOfWeek = today.plusDays(daysToSunday.toLong())
                val startOfWeek = if (fullCalendarWeek) today.minusDays((today.dayOfWeek.value - 1).toLong()) else today
                !date.isBefore(startOfWeek) && !date.isAfter(endOfWeek)
            }
            DateScope.NEXT_WEEK -> {
                // Próxima semana (lunes-domingo) a partir del fin de la actual.
                // daysToSunday ya incluye el fin de esta semana; +1 = lunes próximo,
                // +6 = domingo próximo.
                val daysToSunday = (7 - today.dayOfWeek.value) % 7
                val startNextWeek = today.plusDays((daysToSunday + 1).toLong())
                val endNextWeek = startNextWeek.plusDays(6)
                !date.isBefore(startNextWeek) && !date.isAfter(endNextWeek)
            }
            DateScope.LAST_WEEK -> {
                // Semana pasada completa (lunes-domingo) inmediatamente anterior
                // a la actual. daysToSunday ubica el domingo de esta semana; restando
                // 7 → domingo pasado, y otros 6 → lunes pasado.
                val daysToSunday = (7 - today.dayOfWeek.value) % 7
                val endLastWeek = today.plusDays((daysToSunday - 7).toLong())
                val startLastWeek = endLastWeek.minusDays(6)
                !date.isBefore(startLastWeek) && !date.isAfter(endLastWeek)
            }
            DateScope.THIS_MONTH -> {
                // Mes natural en curso (del 1 al último día del mes de hoy).
                // Recupera todo lo del mes, incluso lo ya pasado a principios de mes.
                val thisMonth = YearMonth.from(today)
                !date.isBefore(thisMonth.atDay(1)) && !date.isAfter(thisMonth.atEndOfMonth())
            }
            DateScope.NEXT_MONTH -> {
                // Mes natural siguiente al de hoy.
                val nextMonth = YearMonth.from(today).plusMonths(1)
                !date.isBefore(nextMonth.atDay(1)) && !date.isAfter(nextMonth.atEndOfMonth())
            }
            DateScope.LAST_MONTH -> {
                // Mes natural anterior al de hoy (recuperación, incluye completadas).
                val lastMonth = YearMonth.from(today).minusMonths(1)
                !date.isBefore(lastMonth.atDay(1)) && !date.isAfter(lastMonth.atEndOfMonth())
            }
            DateScope.OVERDUE -> false // resuelto antes (return temprano)
            DateScope.MISSED -> false // resuelto antes (return temprano)
            DateScope.UNDATED -> false
            DateScope.TARDE -> false
            DateScope.NOCHE -> false
            DateScope.MADRUGADA -> false
            DateScope.WEEKDAY -> false // resuelto antes en taskMatchesDateScope
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
