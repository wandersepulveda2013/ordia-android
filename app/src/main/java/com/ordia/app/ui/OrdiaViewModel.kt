package com.ordia.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ordia.app.backup.BackupManager
import com.ordia.app.backup.RestorePhase
import com.ordia.app.R
import com.ordia.app.data.local.AttachmentEntity
import com.ordia.app.data.local.AttachmentOwnerType
import com.ordia.app.data.local.AutomationLogEntity
import com.ordia.app.data.local.AutomationRuleEntity
import com.ordia.app.data.local.AutomationRuleResult
import com.ordia.app.data.local.CaptureDraftEntity
import com.ordia.app.data.local.CaptureEntity
import com.ordia.app.data.local.CaptureSource
import com.ordia.app.data.local.CaptureStatus
import com.ordia.app.data.local.CaptureTarget
import com.ordia.app.data.local.CommitmentEntity
import com.ordia.app.data.local.CommitmentReviewStatus
import com.ordia.app.data.local.ConversationEntity
import com.ordia.app.data.local.ConversationSourceType
import com.ordia.app.data.local.ConsentEventEntity
import com.ordia.app.data.local.ConsentEventType
import com.ordia.app.data.local.ObservedSourceEntity
import com.ordia.app.data.local.FocusSessionEntity
import com.ordia.app.data.local.HabitEntity
import com.ordia.app.data.local.HabitLogEntity
import com.ordia.app.data.local.NoteEntity
import com.ordia.app.data.local.ProjectEntity
import com.ordia.app.data.local.RoutineEntity
import com.ordia.app.data.local.RoutineStepEntity
import com.ordia.app.data.local.TagEntity
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskPriority
import com.ordia.app.data.local.TaskStatus
import com.ordia.app.data.local.TaskTagCrossRef
import com.ordia.app.data.preferences.GuardianMode
import com.ordia.app.data.preferences.InterfaceMode
import com.ordia.app.data.preferences.PreferencesRepository
import com.ordia.app.data.preferences.ThemeMode
import com.ordia.app.data.preferences.UserPreferences
import com.ordia.app.data.repository.AttachmentRepository
import com.ordia.app.data.repository.AttachmentStorage
import com.ordia.app.data.repository.AutomationLogRepository
import com.ordia.app.data.repository.AutomationRuleRepository
import com.ordia.app.data.repository.CaptureRepository
import com.ordia.app.data.repository.ConversationRepository
import com.ordia.app.data.repository.FocusRepository
import com.ordia.app.data.repository.HabitRepository
import com.ordia.app.data.repository.NoteRepository
import com.ordia.app.data.repository.ObservationRepository
import com.ordia.app.data.repository.ProjectRepository
import com.ordia.app.data.repository.RoutineRepository
import com.ordia.app.data.repository.TagRepository
import com.ordia.app.data.repository.TaskRepository
import com.ordia.app.domain.DateRules
import com.ordia.app.domain.DayPlanner
import com.ordia.app.domain.GuardianCoach
import com.ordia.app.domain.HabitRules
import com.ordia.app.domain.LearningEngine
import com.ordia.app.domain.LearningProfile
import com.ordia.app.domain.NoteBlock
import com.ordia.app.domain.NoteBlockCodec
import com.ordia.app.domain.NoteBlockType
import com.ordia.app.domain.NaturalTaskParser
import com.ordia.app.domain.OnboardingCompleter
import com.ordia.app.domain.ParsedTaskInput
import com.ordia.app.domain.RecurrenceEngine
import com.ordia.app.domain.ReminderSync
import com.ordia.app.domain.RoutineRules
import com.ordia.app.domain.SubtaskRules
import com.ordia.app.domain.TaskRules
import com.ordia.app.domain.TaskSnapshotCodec
import com.ordia.app.domain.TaskMutationGate
import com.ordia.app.domain.UniversalCaptureEngine
import com.ordia.app.conversations.ChatImportParser
import com.ordia.app.conversations.CommitmentEngine
import com.ordia.app.conversations.ConversationPreview
import com.ordia.app.conversations.ConversationSummaryEngine
import com.ordia.app.context.ContextualSettingsStore
import com.ordia.app.reminders.ReminderScheduler
import com.ordia.app.widget.OrdiaWidgetUpdater
import com.ordia.app.BuildConfig
import com.ordia.app.automation.AutomationEngine
import com.ordia.app.automation.AutomationUndoRules
import com.ordia.app.automation.AutomationParseResult
import com.ordia.app.automation.AutomationRuleCatalog
import com.ordia.app.automation.AutomationScheduler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    data class TaskSaved(val id: Long) : UiEvent
    data class NoteSaved(val id: Long) : UiEvent
    /** Automatización aplicada; la UI ofrece deshacer con [AutomationApplied.logId]. */
    data class AutomationApplied(val logId: Long, val message: String) : UiEvent
    data class Archived(val kind: String, val id: Long, val message: String) : UiEvent
}

/**
 * Estado observable del flujo de restauración.
 *
 * La UI lo usa para mostrar progreso real, deshabilitar acciones durante el
 * proceso y mostrar éxito únicamente tras la verificación de persistencia.
 */
sealed interface BackupRestoreState {
    data object Idle : BackupRestoreState
    data class FileSelected(val fileName: String?) : BackupRestoreState
    data object Validating : BackupRestoreState
    data object CreatingSafetyBackup : BackupRestoreState
    data object Restoring : BackupRestoreState
    data object Verifying : BackupRestoreState
    data class Success(val message: String) : BackupRestoreState
    data class Error(val message: String) : BackupRestoreState

    /** Estados en los que un restore está en curso (la UI debe bloquear acciones). */
    val inProgress: Boolean
        get() = when (this) {
            is FileSelected, is Validating, is CreatingSafetyBackup, is Restoring, is Verifying -> true
            else -> false
        }
}

data class OrdiaUiState(
    val tasks: List<TaskEntity> = emptyList(),
    val projects: List<ProjectEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val habits: List<HabitEntity> = emptyList(),
    val habitLogs: List<HabitLogEntity> = emptyList(),
    val focusSessions: List<FocusSessionEntity> = emptyList(),
    val routines: List<RoutineEntity> = emptyList(),
    val routineSteps: List<RoutineStepEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val taskTags: List<TaskTagCrossRef> = emptyList(),
    val archivedTasks: List<TaskEntity> = emptyList(),
    val archivedProjects: List<ProjectEntity> = emptyList(),
    val archivedNotes: List<NoteEntity> = emptyList(),
    val archivedHabits: List<HabitEntity> = emptyList(),
    val archivedRoutines: List<RoutineEntity> = emptyList(),
    val attachments: List<AttachmentEntity> = emptyList(),
    val preferences: UserPreferences = UserPreferences()
) {
    val guardianInsight: GuardianCoach.Insight get() = GuardianCoach.insight(tasks, habits, habitLogs)
    val nextTask: TaskEntity? get() = guardianInsight.taskId?.let(::task) ?: TaskRules.nextBestTask(tasks)
    val rootTasks: List<TaskEntity> get() = tasks.filter { it.parentTaskId == null }
    val pendingTasks: List<TaskEntity> get() = rootTasks.filter { !it.completed && !it.archived && it.status != TaskStatus.CANCELLED }
    // La Bandeja es una cola de revisión por estado. Una fecha extraída con
    // baja confianza no debe sacar silenciosamente la captura de esa cola.
    val inboxTasks: List<TaskEntity> get() = pendingTasks.filter { it.status == TaskStatus.INBOX }
    val overdueTasks: List<TaskEntity> get() = pendingTasks.filter { TaskRules.isOverdue(it) }
    val todayTasks: List<TaskEntity> get() = pendingTasks.filter { TaskRules.isDueToday(it) && !TaskRules.isOverdue(it) }
    val completedCount: Int get() = TaskRules.completedRootCount(tasks)
    val pendingCount: Int get() = pendingTasks.size
    val completionRate: Int get() = TaskRules.completionRate(rootTasks)
    val archivedCount: Int get() = archivedTasks.size + archivedProjects.size + archivedNotes.size + archivedHabits.size + archivedRoutines.size
    val focusMinutesThisWeek: Int get() {
        val start = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        return focusSessions.filter { it.startedAt >= start && it.completed }.sumOf { it.actualMinutes }
    }

    fun task(id: Long): TaskEntity? = tasks.firstOrNull { it.id == id }
    fun project(id: Long?): ProjectEntity? = id?.let { value -> projects.firstOrNull { it.id == value } }
    fun note(id: Long): NoteEntity? = notes.firstOrNull { it.id == id }
    fun habit(id: Long): HabitEntity? = habits.firstOrNull { it.id == id }
    fun subtasks(parentId: Long): List<TaskEntity> = tasks.filter { it.parentTaskId == parentId && !it.archived && it.status != TaskStatus.CANCELLED }.sortedBy { it.sortOrder }
    fun routineSteps(routineId: Long): List<RoutineStepEntity> = routineSteps.filter { it.routineId == routineId }.sortedBy { it.position }
    fun attachmentsFor(type: AttachmentOwnerType, ownerId: Long): List<AttachmentEntity> =
        attachments.filter { it.ownerType == type && it.ownerId == ownerId }
    fun tagsForTask(taskId: Long): List<TagEntity> {
        val ids = taskTags.filter { it.taskId == taskId }.map { it.tagId }.toSet()
        return tags.filter { it.id in ids }
    }
    fun projectProgress(projectId: Long): Float {
        val related = rootTasks.filter { it.projectId == projectId && !it.archived && it.status != TaskStatus.CANCELLED }
        if (related.isEmpty()) return 0f
        return related.count { it.completed }.toFloat() / related.size
    }
    fun habitCount(habitId: Long, date: LocalDate = LocalDate.now()): Int = HabitRules.countFor(habitLogs, habitId, date)
    fun habitStreak(habit: HabitEntity): Int = HabitRules.currentStreak(habit, habitLogs)
}

private data class CoreState(
    val tasks: List<TaskEntity>,
    val projects: List<ProjectEntity>,
    val notes: List<NoteEntity>,
    val habits: List<HabitEntity>
)

private data class SecondaryWithAttachments(
    val state: SecondaryState,
    val attachments: List<AttachmentEntity>
)

private data class ArchiveState(
    val tasks: List<TaskEntity>,
    val projects: List<ProjectEntity>,
    val notes: List<NoteEntity>,
    val habits: List<HabitEntity>,
    val routines: List<RoutineEntity>
)

private data class SecondaryState(
    val focus: List<FocusSessionEntity>,
    val routines: List<RoutineEntity>,
    val steps: List<RoutineStepEntity>,
    val tags: List<TagEntity>,
    val links: List<TaskTagCrossRef>
)

data class CaptureDraftState(
    val loaded: Boolean = false,
    val draft: CaptureDraftEntity? = null
)

data class ObservationRuntimeState(
    val enabled: Boolean = false,
    val pausedUntil: Long = 0L
) {
    val paused: Boolean get() = pausedUntil > System.currentTimeMillis()
}

class OrdiaViewModel(
    private val appContext: Context,
    private val taskRepository: TaskRepository,
    private val projectRepository: ProjectRepository,
    private val noteRepository: NoteRepository,
    private val habitRepository: HabitRepository,
    private val focusRepository: FocusRepository,
    private val routineRepository: RoutineRepository,
    private val tagRepository: TagRepository,
    private val attachmentRepository: AttachmentRepository,
    private val attachmentStorage: AttachmentStorage,
    private val automationLogRepository: AutomationLogRepository,
    private val automationRuleRepository: AutomationRuleRepository,
    private val automationEngine: AutomationEngine,
    private val captureRepository: CaptureRepository,
    private val conversationRepository: ConversationRepository,
    private val observationRepository: ObservationRepository,
    private val contextualSettingsStore: ContextualSettingsStore,
    private val preferencesRepository: PreferencesRepository,
    private val reminderScheduler: ReminderScheduler,
    private val backupManager: BackupManager
) : ViewModel() {
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    private val _backupState = MutableStateFlow<BackupRestoreState>(BackupRestoreState.Idle)
    val backupState: kotlinx.coroutines.flow.StateFlow<BackupRestoreState> = _backupState.asStateFlow()

    /** Candado adicional contra dos restores simultáneos a nivel de ViewModel. */
    private val restoreMutex = kotlinx.coroutines.sync.Mutex()

    val recentCaptures: StateFlow<List<CaptureEntity>> = captureRepository.recent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val captureDraftState: StateFlow<CaptureDraftState> = captureRepository.draft
        .map { CaptureDraftState(loaded = true, draft = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CaptureDraftState())
    val automationRules: StateFlow<List<AutomationRuleEntity>> = automationRuleRepository.rules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val automationHistory: StateFlow<List<AutomationLogEntity>> = automationRuleRepository.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val conversations: StateFlow<List<ConversationEntity>> = conversationRepository.conversations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val commitments: StateFlow<List<CommitmentEntity>> = conversationRepository.commitments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val pendingCommitments: StateFlow<List<CommitmentEntity>> = conversationRepository.pendingCommitments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val observedSources: StateFlow<List<ObservedSourceEntity>> = observationRepository.sources
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val consentHistory: StateFlow<List<ConsentEventEntity>> = observationRepository.consentHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _observationRuntime = MutableStateFlow(
        ObservationRuntimeState(contextualSettingsStore.enabled, contextualSettingsStore.pausedUntil)
    )
    val observationRuntime: StateFlow<ObservationRuntimeState> = _observationRuntime.asStateFlow()
    private val _sharedConversationPreview = MutableStateFlow<ConversationPreview?>(null)
    val sharedConversationPreview: StateFlow<ConversationPreview?> = _sharedConversationPreview.asStateFlow()

    init {
        viewModelScope.launch {
            val legacyPackages = contextualSettingsStore.allowedPackages()
            legacyPackages.forEach { packageName ->
                if (observationRepository.getSource(packageName) == null) {
                    runCatching {
                        observationRepository.configureSource(
                            packageName = packageName,
                            displayName = packageName,
                            enabled = true,
                            onlyCommitments = true
                        )
                    }
                }
            }
            if (legacyPackages.isNotEmpty()) contextualSettingsStore.clearAllowedPackages()
        }
    }

    private val core = combine(
        taskRepository.tasks,
        projectRepository.projects,
        noteRepository.notes,
        habitRepository.habits
    ) { tasks, projects, notes, habits -> CoreState(tasks, projects, notes, habits) }

    private val secondary = combine(
        focusRepository.recent,
        routineRepository.routines,
        routineRepository.steps,
        tagRepository.tags,
        tagRepository.links
    ) { focus, routines, steps, tags, links -> SecondaryState(focus, routines, steps, tags, links) }

    private val secondaryData = combine(secondary, attachmentRepository.all) { state, attachments ->
        SecondaryWithAttachments(state, attachments)
    }

    private val archive = combine(
        taskRepository.archived,
        projectRepository.archived,
        noteRepository.archived,
        habitRepository.archived,
        routineRepository.archived
    ) { tasks, projects, notes, habits, routines -> ArchiveState(tasks, projects, notes, habits, routines) }

    private val logs = habitRepository.logs(
        LocalDate.now().minusDays(370).toEpochDay(),
        LocalDate.now().plusDays(31).toEpochDay()
    )

    val uiState = combine(core, secondaryData, archive, logs, preferencesRepository.preferences) { first, secondData, archived, habitLogs, prefs ->
        val second = secondData.state
        OrdiaUiState(
            tasks = first.tasks,
            projects = first.projects,
            notes = first.notes,
            habits = first.habits,
            habitLogs = habitLogs,
            focusSessions = second.focus,
            routines = second.routines,
            routineSteps = second.steps,
            tags = second.tags,
            taskTags = second.links,
            archivedTasks = archived.tasks,
            archivedProjects = archived.projects,
            archivedNotes = archived.notes,
            archivedHabits = archived.habits,
            archivedRoutines = archived.routines,
            attachments = secondData.attachments,
            preferences = prefs
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrdiaUiState())

    fun saveTask(task: TaskEntity, tagIds: Set<Long> = emptySet(), preserveInbox: Boolean = false) {
        val clean = task.title.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val normalized = task.copy(
                title = clean,
                details = task.details.trim(),
                // Invariante startAt <= dueAt (BackupManager lo exige al restaurar):
                // el editor expone dueAt pero no startAt, así que editar el vencimiento
                // a un instante anterior al startAt heredado de la planificación dejaría
                // startAt > dueAt (backup irrestaurable). Se descarta el startAt incoherente.
                startAt = TaskRules.coerceStartAt(task.startAt, task.dueAt),
                status = when {
                    task.completed -> TaskStatus.COMPLETED
                    preserveInbox -> task.status
                    task.status == TaskStatus.INBOX && task.dueAt != null -> TaskStatus.PLANNED
                    else -> task.status
                },
                updatedAt = now
            )
            val id = if (normalized.id == 0L) taskRepository.add(normalized.copy(createdAt = now)) else {
                taskRepository.update(normalized)
                normalized.id
            }
            if (normalized.status != TaskStatus.CANCELLED && !normalized.completed && (normalized.reminderAt != null || normalized.dueAt != null)) reminderScheduler.schedule(normalized.copy(id = id)) else reminderScheduler.cancel(id)
            uiState.value.tags.forEach { tag ->
                val currentlyLinked = uiState.value.taskTags.any { it.taskId == id && it.tagId == tag.id }
                when {
                    tag.id in tagIds && !currentlyLinked -> tagRepository.link(id, tag.id)
                    tag.id !in tagIds && currentlyLinked -> tagRepository.unlink(id, tag.id)
                }
            }
            updateWidget()
            _events.emit(UiEvent.TaskSaved(id))
        }
    }

    fun addSmartTask(input: String) {
        addParsedTask(NaturalTaskParser.parse(input))
    }

    /**
     * Crea una tarea a partir de la interpretación del analizador local.
     *
     * Si la confianza es baja o no hay fecha, la captura aterriza en la Bandeja
     * (INBOX) para revisión sin perder texto; las señales extraídas (recordatorio,
     * duración, repetición) se conservan.
     */
    fun addParsedTask(parsed: ParsedTaskInput) {
        val reminderAt = parsed.reminderOffsetMinutes
            ?.takeIf { parsed.dueAt != null }
            ?.let { offset -> parsed.dueAt!! - offset * 60_000L }
        val status = when {
            parsed.confidence < 0.5f -> TaskStatus.INBOX
            parsed.dueAt == null -> TaskStatus.INBOX
            else -> TaskStatus.PLANNED
        }
        saveTask(
            TaskEntity(
                title = parsed.title,
                dueAt = parsed.dueAt,
                reminderAt = reminderAt,
                durationMinutes = parsed.durationMinutes ?: 25,
                priority = parsed.priority,
                recurrence = parsed.recurrence,
                recurrenceInterval = parsed.recurrenceInterval,
                recurrenceDays = parsed.recurrenceDays,
                status = status
            ),
            preserveInbox = status == TaskStatus.INBOX
        )
    }

    fun addTask(
        title: String,
        details: String = "",
        dueAt: Long? = null,
        priority: TaskPriority = TaskPriority.NORMAL,
        projectId: Long? = null,
        parentTaskId: Long? = null
    ) = saveTask(
        TaskEntity(
            title = title,
            details = details,
            dueAt = dueAt,
            priority = priority,
            projectId = projectId,
            parentTaskId = parentTaskId,
            status = if (dueAt == null) TaskStatus.INBOX else TaskStatus.PLANNED
        )
    )

    /**
     * Añade una subtarea al padre respetando la profundidad máxima del árbol.
     * Si el padre ya está en la profundidad máxima, muestra un mensaje y no crea nada.
     */
    fun addSubtask(parent: TaskEntity, title: String) {
        val clean = title.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            val tasksById = uiState.value.tasks.associateBy { it.id }
            if (!SubtaskRules.canAddSubtask(parent, tasksById)) {
                _events.emit(UiEvent.Message(appContext.getString(R.string.subtask_max_depth)))
                return@launch
            }
            saveTask(
                TaskEntity(
                    title = clean,
                    status = TaskStatus.INBOX,
                    parentTaskId = parent.id
                )
            )
        }
    }

    fun toggleTask(task: TaskEntity) {
        viewModelScope.launch {
            TaskMutationGate.mutex.withLock {
                val current = taskRepository.get(task.id) ?: return@withLock
                val now = System.currentTimeMillis()
                val completing = !current.completed
                val updated = current.copy(
                    completed = completing,
                    status = if (completing) TaskStatus.COMPLETED else if (current.dueAt == null) TaskStatus.INBOX else TaskStatus.PLANNED,
                    completedAt = if (completing) now else null,
                    updatedAt = now
                )
                taskRepository.update(updated)
                if (completing) {
                    reminderScheduler.cancel(current.id)
                    RecurrenceEngine.nextOccurrence(current, now)?.let { next ->
                        val nextId = taskRepository.add(next)
                        reminderScheduler.schedule(next.copy(id = nextId))
                        // El desglose del padre recurrente renace en la próxima
                        // ocurrencia (c.223): sin esto, el checklist se perdía en
                        // cada ciclo. Se clonan las subtareas no archivadas del
                        // padre recién completado, abiertas y sin planificación
                        // heredada (ver SubtaskRules.cloneForNextOccurrence).
                        val subs = taskRepository.subtasks(current.id)
                        if (subs.isNotEmpty()) {
                            taskRepository.addAll(SubtaskRules.cloneForNextOccurrence(subs, nextId, now))
                        }
                    }
                } else if (updated.reminderAt != null || updated.dueAt != null) reminderScheduler.schedule(updated)

                // Subtareas: el padre se completa automáticamente al cerrar la
                // última, y se reabre al reactivar una subtarea.
                current.parentTaskId?.let { parentId ->
                    val parent = taskRepository.get(parentId) ?: return@let
                    val siblings = taskRepository.subtasks(parentId)
                    if (completing && SubtaskRules.shouldAutoCompleteParent(parent, siblings)) {
                        completeParentAutomatically(parent, now)
                    } else if (!completing && SubtaskRules.shouldAutoReopenParent(parent, siblings)) {
                        val reopened = parent.copy(
                            completed = false,
                            status = if (parent.dueAt == null) TaskStatus.INBOX else TaskStatus.PLANNED,
                            completedAt = null,
                            updatedAt = now
                        )
                        taskRepository.update(reopened)
                        if (reopened.reminderAt != null || reopened.dueAt != null) reminderScheduler.schedule(reopened)
                    }
                }
            }
            updateWidget()
        }
    }

    /**
     * Pospone una tarea a "mañana a la misma hora" sin abrir el editor: acción
     * directa detrás de la sugerencia de posposición cuando el día está
     * saturado. Reusa [TaskRules.deferToNextDay] (regla pura que preserva el
     * offset del recordatorio y la distancia inicio→vencimiento) y [saveTask]
     * (que reagenda el recordatorio en el nuevo vencimiento).
     */
    fun deferTaskToTomorrow(taskId: Long) {
        viewModelScope.launch {
            val current = taskRepository.get(taskId) ?: return@launch
            val deferred = TaskRules.deferToNextDay(current, System.currentTimeMillis()) ?: return@launch
            saveTask(deferred)
        }
    }

    /**
     * Completa automáticamente una tarea padre al cerrar su última subtarea,
     * con los mismos efectos laterales que un toggle normal y registro de
     * automatización para poder deshacerlo (restaura el padre sin tocar las
     * subtareas, que completó el propio usuario).
     */
    private suspend fun completeParentAutomatically(parent: TaskEntity, now: Long) {
        val before = taskRepository.get(parent.id) ?: return
        val updated = before.copy(
            completed = true,
            status = TaskStatus.COMPLETED,
            completedAt = now,
            updatedAt = now
        )
        taskRepository.update(updated)
        reminderScheduler.cancel(before.id)
        RecurrenceEngine.nextOccurrence(before, now)?.let { next ->
            val nextId = taskRepository.add(next)
            reminderScheduler.schedule(next.copy(id = nextId))
            // Mismo rescate del checklist que en toggleTask (c.223): al
            // autocompletar el padre por cerrar su última subtarea, el
            // desglose renace abierto en la próxima ocurrencia.
            val subs = taskRepository.subtasks(before.id)
            if (subs.isNotEmpty()) {
                taskRepository.addAll(SubtaskRules.cloneForNextOccurrence(subs, nextId, now))
            }
        }
        val logId = automationLogRepository.insert(
            AutomationLogEntity(
                type = "subtask_auto",
                description = appContext.getString(R.string.automation_desc_subtask_auto, before.title),
                affectedTaskIdsJson = TaskSnapshotCodec.encodeIds(listOf(before.id)),
                undoPayloadJson = TaskSnapshotCodec.encodeMap(mapOf(before.id to before))
            )
        )
        _events.emit(
            UiEvent.AutomationApplied(
                logId,
                appContext.getString(R.string.subtask_parent_auto_completed, before.title)
            )
        )
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            reminderScheduler.cancel(task.id)
            taskRepository.archive(task.id)
            updateWidget()
            _events.emit(UiEvent.Archived("task", task.id, appContext.getString(R.string.task_archived)))
        }
    }

    fun duplicateTask(task: TaskEntity) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val copy = task.copy(
                id = 0,
                title = "${task.title} (copia)",
                completed = false,
                completedAt = null,
                status = if (task.dueAt == null) TaskStatus.INBOX else TaskStatus.PLANNED,
                createdAt = now,
                updatedAt = now
            )
            val id = taskRepository.add(copy)
            uiState.value.tagsForTask(task.id).forEach { tagRepository.link(id, it.id) }
            if (copy.reminderAt != null || copy.dueAt != null) reminderScheduler.schedule(copy.copy(id = id))
            updateWidget()
        }
    }

    fun saveProject(project: ProjectEntity) {
        val clean = project.name.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (project.id == 0L) projectRepository.add(project.copy(name = clean, description = project.description.trim(), createdAt = now, updatedAt = now))
            else projectRepository.update(project.copy(name = clean, description = project.description.trim(), updatedAt = now))
        }
    }

    fun deleteProject(project: ProjectEntity) = viewModelScope.launch {
        projectRepository.archive(project.id)
        _events.emit(UiEvent.Archived("project", project.id, appContext.getString(R.string.project_archived)))
    }

    fun saveNote(note: NoteEntity, blocks: List<NoteBlock>? = null, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val normalizedBlocks = blocks ?: NoteBlockCodec.decode(note.blocksData, note.body)
            val normalized = note.copy(
                title = note.title.trim().ifBlank { "Nota sin título" },
                body = NoteBlockCodec.toPlainText(normalizedBlocks),
                blocksData = NoteBlockCodec.encode(normalizedBlocks),
                updatedAt = now
            )
            val id = if (normalized.id == 0L) noteRepository.add(normalized.copy(createdAt = now)) else {
                noteRepository.update(normalized)
                normalized.id
            }
            onSaved(id)
            _events.emit(UiEvent.NoteSaved(id))
        }
    }

    fun addNote(title: String, body: String = "") = saveNote(
        NoteEntity(title = title, body = body),
        listOf(NoteBlock(text = body))
    )

    fun deleteNote(note: NoteEntity) = viewModelScope.launch {
        noteRepository.archive(note.id)
        _events.emit(UiEvent.Archived("note", note.id, appContext.getString(R.string.note_archived)))
    }

    fun togglePin(note: NoteEntity) = viewModelScope.launch {
        noteRepository.update(note.copy(pinned = !note.pinned, updatedAt = System.currentTimeMillis()))
    }

    fun addAttachment(
        ownerType: AttachmentOwnerType,
        ownerId: Long,
        sourceUri: String,
        displayName: String,
        mimeType: String,
        sizeBytes: Long
    ) = viewModelScope.launch {
        val resolvedMime = mimeType.ifBlank { "application/octet-stream" }
        val storedUri = attachmentStorage.import(
            sourceUri = sourceUri,
            ownerType = ownerType,
            ownerId = ownerId,
            displayName = displayName
        ) ?: sourceUri // respaldo: si la copia falla, conservar el URI original
        attachmentRepository.add(
            AttachmentEntity(
                ownerType = ownerType,
                ownerId = ownerId,
                uri = storedUri,
                displayName = displayName,
                mimeType = resolvedMime,
                sizeBytes = sizeBytes
            )
        )
        _events.emit(UiEvent.Message(appContext.getString(R.string.attachment_added)))
    }

    fun deleteAttachment(attachment: AttachmentEntity) = viewModelScope.launch {
        attachmentStorage.deleteStored(attachment.uri)
        attachmentRepository.delete(attachment)
        _events.emit(UiEvent.Message(appContext.getString(R.string.attachment_removed)))
    }

    /**
     * Resuelve el URI de un adjunto para abrirlo con `ACTION_VIEW`. Si el adjunto
     * vive en almacenamiento interno, lo expone vía FileProvider; si es un URI
     * externo legacy (capturas anteriores a este cambio), lo devuelve tal cual.
     * Devuelve `null` cuando el contenido ya no existe.
     */
    fun resolveAttachmentUri(storedUri: String): android.net.Uri? =
        attachmentStorage.resolveForOpening(storedUri)

    fun saveHabit(habit: HabitEntity) {
        val clean = habit.title.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (habit.id == 0L) habitRepository.add(habit.copy(title = clean, createdAt = now, updatedAt = now))
            else habitRepository.update(habit.copy(title = clean, updatedAt = now))
        }
    }

    fun toggleHabit(habit: HabitEntity, date: LocalDate = LocalDate.now()) {
        viewModelScope.launch {
            val current = uiState.value.habitCount(habit.id, date)
            if (current >= habit.targetPerPeriod) habitRepository.removeLog(habit.id, date.toEpochDay())
            else habitRepository.log(HabitLogEntity(habit.id, date.toEpochDay(), current + 1))
        }
    }

    fun deleteHabit(habit: HabitEntity) = viewModelScope.launch {
        habitRepository.archive(habit.id)
        _events.emit(UiEvent.Archived("habit", habit.id, appContext.getString(R.string.habit_archived)))
    }

    fun saveRoutine(routine: RoutineEntity, stepTitles: List<String>) {
        val clean = routine.name.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = if (routine.id == 0L) routineRepository.add(routine.copy(name = clean, createdAt = now, updatedAt = now)) else {
                val old = routineRepository.get(routine.id)
                routineRepository.update(routine.copy(name = clean, updatedAt = now))
                // Tras renombrar la rutina, las tareas que generó hoy todavía
                // llevan el nombre viejo en `details`; si no se reetiquetan,
                // wasRunToday deja de verlas y un re-disparo duplica los pasos.
                val oldName = old?.name?.trim().orEmpty()
                if (oldName.isNotBlank() && oldName != clean) {
                    RoutineRules.relinkAfterRename(uiState.value.tasks, oldName, clean, java.time.LocalDate.now())
                        .forEach { relink ->
                            uiState.value.tasks.firstOrNull { it.id == relink.taskId }
                                ?.let { taskRepository.update(it.copy(details = relink.newDetails)) }
                        }
                }
                routine.id
            }
            val steps = stepTitles.map { it.trim() }.filter { it.isNotBlank() }
                .mapIndexed { index, title -> RoutineStepEntity(routineId = id, title = title, position = index) }
            routineRepository.replaceSteps(id, steps)
        }
    }

    /**
     * Ejecuta una rutina: añade sus pasos a la bandeja como tareas, evitando
     * duplicados si ya se ejecutó hoy, y registra la automatización para poder
     * deshacerla (elimina las tareas creadas si siguen intactas).
     */
    fun runRoutine(routine: RoutineEntity) {
        viewModelScope.launch {
            val steps = routineRepository.stepsFor(routine.id)
            if (steps.isEmpty()) {
                _events.emit(UiEvent.Message(appContext.getString(R.string.routine_empty)))
                return@launch
            }
            if (RoutineRules.wasRunToday(uiState.value.tasks, routine.name, java.time.LocalDate.now())) {
                _events.emit(UiEvent.Message(appContext.getString(R.string.routine_already_in_inbox)))
                return@launch
            }
            val now = System.currentTimeMillis()
            val createdIds = steps.mapIndexedNotNull { index, step ->
                taskRepository.add(
                    TaskEntity(
                        title = step.title,
                        details = RoutineRules.routineDetail(routine.name),
                        durationMinutes = step.durationMinutes,
                        status = TaskStatus.INBOX,
                        sortOrder = index,
                        createdAt = now + index,
                        updatedAt = now + index
                    )
                ).takeIf { it > 0L }
            }
            if (createdIds.isEmpty()) {
                _events.emit(UiEvent.Message(appContext.getString(R.string.routine_empty)))
                return@launch
            }
            updateWidget()
            val logId = automationLogRepository.insert(
                AutomationLogEntity(
                    type = "routine",
                    description = appContext.getString(R.string.automation_desc_routine, routine.name, createdIds.size),
                    affectedTaskIdsJson = TaskSnapshotCodec.encodeIds(createdIds),
                    undoPayloadJson = "{}"
                )
            )
            _events.emit(
                UiEvent.AutomationApplied(
                    logId,
                    appContext.getString(R.string.routine_added_to_inbox, routine.name)
                )
            )
        }
    }

    fun archiveRoutine(routine: RoutineEntity) = viewModelScope.launch {
        routineRepository.archive(routine.id)
        _events.emit(UiEvent.Archived("routine", routine.id, appContext.getString(R.string.routine_archived)))
    }

    fun restoreArchived(kind: String, id: Long) = viewModelScope.launch {
        when (kind) {
            "task" -> {
                taskRepository.restore(id)
                // Al archivar se canceló el recordatorio (WorkManager). Al restaurar,
                // re-encolarlo si la tarea sigue activa con un disparo futuro: sin
                // esto, una tarea restaurada "olvida" avisar aunque conserve su fecha.
                val restored = taskRepository.get(id)
                if (restored != null &&
                    !restored.completed &&
                    restored.status != TaskStatus.CANCELLED &&
                    (restored.reminderAt != null || restored.dueAt != null)
                ) {
                    reminderScheduler.schedule(restored)
                }
            }
            "project" -> projectRepository.restore(id)
            "note" -> noteRepository.restore(id)
            "habit" -> habitRepository.restore(id)
            "routine" -> routineRepository.restore(id)
        }
        updateWidget()
        _events.emit(UiEvent.Message(appContext.getString(R.string.item_restored)))
    }

    fun deleteArchivedPermanently(kind: String, id: Long) = viewModelScope.launch {
        when (kind) {
            "task" -> { reminderScheduler.cancel(id); taskRepository.deletePermanently(id) }
            "project" -> projectRepository.deletePermanently(id)
            "note" -> noteRepository.deletePermanently(id)
            "habit" -> habitRepository.deletePermanently(id)
            "routine" -> routineRepository.deletePermanently(id)
        }
        updateWidget()
        _events.emit(UiEvent.Message(appContext.getString(R.string.item_deleted_permanently)))
    }

    fun addTag(name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        viewModelScope.launch { tagRepository.add(TagEntity(name = clean)) }
    }

    /**
     * Aplica un plan del día a las tareas seleccionadas (o a todas si
     * [blockIds] es null) y registra la automatización para poder deshacerla.
     */
    fun applyDayPlan(plan: DayPlanner.Plan, blockIds: Set<Long>? = null) = viewModelScope.launch {
        val selected = plan.blocks.filter { blockIds == null || blockIds.contains(it.taskId) }
        if (selected.isEmpty()) {
            _events.emit(UiEvent.Message(appContext.getString(R.string.planner_none_selected)))
            return@launch
        }
        val message = applyBlocks(plan, selected, "day_plan")
        _events.emit(UiEvent.AutomationApplied(message.first, message.second))
    }

    /**
     * Replanifica el día: recalcula el plan incluyendo las tareas que ya tenían
     * hora prevista ese día y reubica los bloques que no caben. Registra la
     * automatización con tipo "replan" para poder deshacerla.
     */
    fun replanDay(date: java.time.LocalDate) = viewModelScope.launch {
        val profile = plannerProfile()
        val plan = DayPlanner.build(
            uiState.value.tasks,
            date,
            dayStartMinute = profile?.dayStartMinute ?: 9 * 60,
            dayEndMinute = profile?.dayEndMinute ?: 18 * 60,
            includeScheduledOnDate = true
        )
        if (plan.blocks.isEmpty()) {
            _events.emit(UiEvent.Message(appContext.getString(R.string.planner_replan_none)))
            return@launch
        }
        val message = applyBlocks(plan, plan.blocks, "replan")
        _events.emit(UiEvent.AutomationApplied(message.first, message.second))
    }

    /**
     * Perfil de horarios aprendido localmente (opt-in). Devuelve null si el
     * usuario no activó el aprendizaje; entonces se usan los valores fijos.
     */
    private fun plannerProfile(): LearningProfile? {
        val prefs = uiState.value.preferences
        return if (prefs.learningEnabled) {
            LearningEngine.learn(uiState.value.tasks, System.currentTimeMillis())
        } else null
    }

    /** Comparte la lógica de aplicar bloques: snapshot previo, update y log. */
    private suspend fun applyBlocks(
        plan: DayPlanner.Plan,
        blocks: List<DayPlanner.Block>,
        type: String
    ): Pair<Long, String> {
        val now = System.currentTimeMillis()
        val before = mutableMapOf<Long, TaskEntity>()
        var updated = 0
        blocks.forEach { block ->
            val task = taskRepository.get(block.taskId) ?: return@forEach
            before[task.id] = task
            val start = DateRules.toEpochMillis(plan.date, block.startMinute)
            val end = DateRules.toEpochMillis(plan.date, block.endMinute)
            val normalized = task.copy(
                startAt = start,
                // Vencimiento coherente con el slot: si este empieza después del due
                // original (tarea vencida/temprana en un bloque posterior), el due
                // sigue al fin del slot. Evita `startAt > dueAt`, estado que
                // [BackupManager] rechaza al restaurar (backup irrestaurable). Para
                // tareas sin due se mantiene el fin del slot (comportamiento previo).
                dueAt = TaskRules.dueAtForPlannedSlot(task.dueAt, start, end) ?: end,
                status = if (task.completed) TaskStatus.COMPLETED else TaskStatus.PLANNED,
                updatedAt = now
            )
            taskRepository.update(normalized)
            normalized.reminderAt?.let { reminderScheduler.schedule(normalized) }
            updated++
        }
        updateWidget()
        val logId = automationLogRepository.insert(
            AutomationLogEntity(
                type = type,
                description = when (type) {
                    "replan" -> appContext.getString(R.string.automation_desc_replan, plan.date.toString(), updated)
                    else -> appContext.getString(R.string.automation_desc_day_plan, plan.date.toString(), updated)
                },
                affectedTaskIdsJson = TaskSnapshotCodec.encodeIds(blocks.map { it.taskId }),
                undoPayloadJson = TaskSnapshotCodec.encodeMap(before)
            )
        )
        val message = when {
            type == "replan" && updated == 1 -> appContext.getString(R.string.planner_replanned_one)
            type == "replan" -> appContext.getString(R.string.planner_replanned_many, updated)
            updated == 1 -> appContext.getString(R.string.planner_applied_one)
            else -> appContext.getString(R.string.planner_applied_many, updated)
        }
        return logId to message
    }

    /**
     * Restaura el estado previo de la última automatización no deshecha
     * (plan del día, replanificación, "qué hago ahora", rutina).
     */
    fun undoLastAutomation() = viewModelScope.launch {
        val log = automationLogRepository.latestNotUndone() ?: run {
            _events.emit(UiEvent.Message(appContext.getString(R.string.automation_nothing_to_undo)))
            return@launch
        }
        val before = TaskSnapshotCodec.decodeMap(log.undoPayloadJson)
        val affectedIds = TaskSnapshotCodec.decodeIds(log.affectedTaskIdsJson)
        val createdIds = AutomationUndoRules.createdTaskIds(affectedIds, before.keys)
        if (before.isEmpty() && affectedIds.isEmpty()) {
            _events.emit(UiEvent.Message(appContext.getString(R.string.automation_nothing_to_undo)))
            return@launch
        }
        val now = System.currentTimeMillis()
        before.forEach { (id, snapshot) ->
            val current = taskRepository.get(id) ?: return@forEach
            if (current != snapshot) {
                taskRepository.update(snapshot.copy(updatedAt = now))
                if (snapshot.completed || snapshot.status == TaskStatus.CANCELLED || (snapshot.reminderAt == null && snapshot.dueAt == null)) {
                    reminderScheduler.cancel(id)
                } else {
                    reminderScheduler.schedule(snapshot.copy(id = id))
                }
            }
        }
        // affectedTaskIdsJson también contiene tareas preexistentes modificadas.
        // Solo los IDs sin snapshot previo fueron creados por la automatización.
        // Aun así se eliminan únicamente si siguen intactos en la bandeja.
        val removedCreated = createdIds.mapNotNull { taskRepository.get(it) }
            .filter { !it.completed && !it.archived && it.status == TaskStatus.INBOX }
            .map { it.id }
            .also { ids -> ids.forEach { taskRepository.deletePermanently(it) } }
            .size
        automationLogRepository.markUndone(log.id)
        updateWidget()
        val message = if (removedCreated > 0) appContext.getString(R.string.automation_undone_created)
        else appContext.getString(R.string.automation_undone)
        _events.emit(UiEvent.Message(message))
    }

    fun saveFocusSession(taskId: Long?, startedAt: Long, endedAt: Long, plannedMinutes: Int, completed: Boolean, notes: String = "") {
        viewModelScope.launch {
            val actual = ((endedAt - startedAt) / 60_000L).toInt().coerceAtLeast(0)
            focusRepository.add(FocusSessionEntity(taskId = taskId, startedAt = startedAt, endedAt = endedAt, plannedMinutes = plannedMinutes, actualMinutes = actual, completed = completed, notes = notes))
            _events.emit(UiEvent.Message(appContext.getString(if (completed) R.string.focus_session_completed else R.string.focus_session_saved)))
        }
    }

    fun captureSharedText(text: String) = submitCapture(
        content = text,
        requestedTarget = CaptureTarget.AUTO,
        source = CaptureSource.SHARE
    )

    /**
     * Persiste el borrador mientras se escribe. La captura puede recuperarse
     * después de cerrar la pantalla o reiniciar el proceso de la aplicación.
     */
    fun saveCaptureDraft(
        content: String,
        target: CaptureTarget,
        attachmentUri: String = "",
        mimeType: String = ""
    ) = viewModelScope.launch {
        if (content.isBlank() && attachmentUri.isBlank() && target == CaptureTarget.AUTO) {
            captureRepository.clearDraft()
        } else {
            captureRepository.saveDraft(
                CaptureDraftEntity(
                    content = content.take(UniversalCaptureEngine.MAX_CONTENT_CHARS),
                    target = target,
                    attachmentUri = attachmentUri,
                    mimeType = mimeType,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun clearCaptureDraft() = viewModelScope.launch { captureRepository.clearDraft() }

    /**
     * Punto de entrada único de la captura. Primero escribe el original en el
     * historial y solo después intenta convertirlo en tarea, nota o bandeja.
     */
    fun submitCapture(
        content: String,
        requestedTarget: CaptureTarget = CaptureTarget.AUTO,
        source: CaptureSource = CaptureSource.COMPOSER,
        attachmentUri: String = "",
        mimeType: String = "",
        onSaved: (resultType: String, resultId: Long) -> Unit = { _, _ -> }
    ) {
        val original = content.take(UniversalCaptureEngine.MAX_CONTENT_CHARS)
        if (original.isBlank() && attachmentUri.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val fingerprint = UniversalCaptureEngine.fingerprint(original, attachmentUri)
            val duplicate = captureRepository.findRecentDuplicate(
                fingerprint,
                now - CAPTURE_DUPLICATE_WINDOW_MS
            )
            if (duplicate != null) {
                _events.emit(UiEvent.Message(appContext.getString(R.string.capture_duplicate_ignored)))
                return@launch
            }

            val interpretation = UniversalCaptureEngine.interpret(
                raw = original,
                requested = requestedTarget,
                hasAttachment = attachmentUri.isNotBlank()
            )
            var stored = CaptureEntity(
                content = original,
                source = source,
                requestedTarget = requestedTarget,
                resolvedTarget = interpretation.target,
                status = CaptureStatus.PENDING,
                attachmentUri = attachmentUri,
                mimeType = mimeType,
                fingerprint = fingerprint,
                createdAt = now,
                updatedAt = now
            )
            val captureId = captureRepository.insert(stored)
            stored = stored.copy(id = captureId)

            processStoredCapture(stored, interpretation, onSaved)
        }
    }

    fun retryCapture(capture: CaptureEntity) {
        if (capture.status != CaptureStatus.FAILED) return
        viewModelScope.launch {
            val interpretation = UniversalCaptureEngine.interpret(
                raw = capture.content,
                requested = capture.requestedTarget,
                hasAttachment = capture.attachmentUri.isNotBlank()
            )
            val pending = capture.copy(
                resolvedTarget = interpretation.target,
                status = CaptureStatus.PENDING,
                errorCode = "",
                updatedAt = System.currentTimeMillis()
            )
            captureRepository.update(pending)
            processStoredCapture(pending, interpretation)
        }
    }

    fun discardFailedCapture(capture: CaptureEntity) {
        if (capture.status != CaptureStatus.FAILED) return
        viewModelScope.launch {
            captureRepository.delete(capture.id)
            _events.emit(UiEvent.Message(appContext.getString(R.string.capture_failed_discarded)))
        }
    }

    private suspend fun processStoredCapture(
        stored: CaptureEntity,
        interpretation: com.ordia.app.domain.CaptureInterpretation,
        onSaved: (resultType: String, resultId: Long) -> Unit = { _, _ -> }
    ) {
        try {
            val result = when (interpretation.target) {
                CaptureTarget.NOTE -> "NOTE" to createNoteFromCapture(
                    title = interpretation.title,
                    body = interpretation.body,
                    checklist = interpretation.checklist,
                    attachmentUri = stored.attachmentUri,
                    mimeType = stored.mimeType
                )
                CaptureTarget.AUTO -> error("La captura AUTO no fue resuelta")
                CaptureTarget.INBOX,
                CaptureTarget.TASK,
                CaptureTarget.REMINDER -> "TASK" to createTaskFromCapture(
                    interpretation = interpretation,
                    original = stored.content,
                    attachmentUri = stored.attachmentUri,
                    mimeType = stored.mimeType
                )
            }
            captureRepository.update(
                stored.copy(
                    status = CaptureStatus.PROCESSED,
                    resultType = result.first,
                    resultId = result.second,
                    errorCode = "",
                    updatedAt = System.currentTimeMillis()
                )
            )
            captureRepository.clearDraft()
            updateWidget()
            onSaved(result.first, result.second)
            _events.emit(UiEvent.Message(appContext.getString(R.string.capture_saved_success)))
        } catch (error: Exception) {
            captureRepository.update(
                stored.copy(
                    status = CaptureStatus.FAILED,
                    errorCode = error.javaClass.simpleName.take(80),
                    updatedAt = System.currentTimeMillis()
                )
            )
            _events.emit(UiEvent.Message(appContext.getString(R.string.capture_saved_for_retry)))
        }
    }

    private suspend fun createNoteFromCapture(
        title: String,
        body: String,
        checklist: Boolean,
        attachmentUri: String,
        mimeType: String
    ): Long {
        val blocks = if (checklist) {
            body.lineSequence()
                .map { it.trim().replace(Regex("^(?:[-*•]|\\d+[.)]|\\[\\s?])\\s+"), "") }
                .filter(String::isNotBlank)
                .map { NoteBlock(type = NoteBlockType.CHECKLIST, text = it) }
                .toList()
        } else {
            listOf(NoteBlock(text = body))
        }
        val now = System.currentTimeMillis()
        val noteId = noteRepository.add(
            NoteEntity(
                title = title.trim().ifBlank { appContext.getString(R.string.capture_untitled_note) },
                body = NoteBlockCodec.toPlainText(blocks),
                blocksData = NoteBlockCodec.encode(blocks),
                createdAt = now,
                updatedAt = now
            )
        )
        attachCaptureIfPresent(AttachmentOwnerType.NOTE, noteId, attachmentUri, mimeType)
        return noteId
    }

    private suspend fun createTaskFromCapture(
        interpretation: com.ordia.app.domain.CaptureInterpretation,
        original: String,
        attachmentUri: String,
        mimeType: String
    ): Long {
        val parsed = interpretation.parsedTask ?: NaturalTaskParser.parse(interpretation.title)
        val reminderAt = parsed.reminderOffsetMinutes
            ?.takeIf { parsed.dueAt != null }
            ?.let { offset -> parsed.dueAt!! - offset * 60_000L }
            ?: parsed.dueAt.takeIf { interpretation.target == CaptureTarget.REMINDER }
        val status = when {
            interpretation.target == CaptureTarget.INBOX -> TaskStatus.INBOX
            parsed.confidence < 0.5f || parsed.dueAt == null -> TaskStatus.INBOX
            else -> TaskStatus.PLANNED
        }
        val now = System.currentTimeMillis()
        val task = TaskEntity(
            title = parsed.title.trim().ifBlank { appContext.getString(R.string.capture_untitled_task) },
            details = original,
            dueAt = parsed.dueAt,
            reminderAt = reminderAt,
            durationMinutes = parsed.durationMinutes ?: 25,
            priority = parsed.priority,
            status = status,
            recurrence = parsed.recurrence,
            recurrenceInterval = parsed.recurrenceInterval,
            recurrenceDays = parsed.recurrenceDays,
            createdAt = now,
            updatedAt = now
        )
        val taskId = taskRepository.add(task)
        if (task.reminderAt != null || task.dueAt != null) {
            reminderScheduler.schedule(task.copy(id = taskId))
        }
        attachCaptureIfPresent(AttachmentOwnerType.TASK, taskId, attachmentUri, mimeType)
        return taskId
    }

    private suspend fun attachCaptureIfPresent(
        ownerType: AttachmentOwnerType,
        ownerId: Long,
        attachmentUri: String,
        mimeType: String
    ) {
        if (attachmentUri.isBlank()) return
        val displayName = attachmentUri.substringAfterLast('/').ifBlank {
            appContext.getString(R.string.capture_attachment_name)
        }
        val resolvedMime = mimeType.ifBlank { "application/octet-stream" }
        val storedUri = attachmentStorage.import(
            sourceUri = attachmentUri,
            ownerType = ownerType,
            ownerId = ownerId,
            displayName = displayName
        ) ?: attachmentUri // respaldo: si la copia falla, conservar el URI original
        attachmentRepository.add(
            AttachmentEntity(
                ownerType = ownerType,
                ownerId = ownerId,
                uri = storedUri,
                displayName = displayName,
                mimeType = resolvedMime
            )
        )
    }

    fun prepareSharedConversation(raw: String, title: String = "Contenido compartido") {
        if (raw.isBlank()) return
        viewModelScope.launch {
            val preview = runCatching {
                withContext(Dispatchers.Default) { ChatImportParser.parse(raw, title) }
            }.getOrElse {
                _events.emit(UiEvent.Message(appContext.getString(R.string.conversation_parse_failed)))
                return@launch
            }
            _sharedConversationPreview.value = preview
        }
    }

    fun clearSharedConversationPreview() {
        _sharedConversationPreview.value = null
    }

    fun saveConversationPreview(
        preview: ConversationPreview,
        retainOriginal: Boolean,
        selfParticipant: String?,
        sourceType: ConversationSourceType = ConversationSourceType.IMPORTED,
        sourcePackage: String = "",
        onSaved: (Boolean) -> Unit = {}
    ) = viewModelScope.launch {
        val drafts = withContext(Dispatchers.Default) {
            CommitmentEngine.extract(preview.messages, selfParticipant, preview.contentHash)
        }
        val summary = withContext(Dispatchers.Default) {
            ConversationSummaryEngine.summarize(preview, drafts)
        }
        val now = System.currentTimeMillis()
        val conversation = ConversationEntity(
            sourceType = sourceType,
            sourcePackage = sourcePackage.take(180),
            title = preview.title.take(160),
            participants = ChatImportParser.encodeParticipants(preview.participants),
            summary = summary.take(4_000),
            rawContent = if (retainOriginal) preview.rawContent else "",
            retainsOriginal = retainOriginal,
            contentHash = preview.contentHash,
            messageCount = preview.messages.size,
            createdAt = now,
            updatedAt = now
        )
        val entities = drafts.map { draft ->
            CommitmentEntity(
                conversationId = 0,
                kind = draft.kind,
                owner = draft.owner,
                actor = draft.actor,
                action = draft.action,
                location = draft.location,
                dueAt = draft.dueAt,
                confidence = draft.confidence,
                suggestedReminderAt = draft.suggestedReminderAt,
                fingerprint = draft.fingerprint,
                createdAt = now,
                updatedAt = now
            )
        }
        val (id, created) = try {
            conversationRepository.saveGraph(conversation, entities)
        } catch (_: Exception) {
            _events.emit(UiEvent.Message(appContext.getString(R.string.conversation_save_failed)))
            onSaved(false)
            return@launch
        }
        if (id <= 0L) {
            _events.emit(UiEvent.Message(appContext.getString(R.string.conversation_save_failed)))
            onSaved(false)
            return@launch
        }
        _sharedConversationPreview.value = null
        _events.emit(
            UiEvent.Message(
                appContext.resources.getQuantityString(
                    if (created) R.plurals.conversation_saved else R.plurals.conversation_duplicate,
                    entities.size,
                    entities.size
                )
            )
        )
        onSaved(true)
    }

    fun convertCommitmentToTask(commitmentId: Long) = viewModelScope.launch {
        val commitment = conversationRepository.getCommitment(commitmentId) ?: return@launch
        if (commitment.reviewStatus != CommitmentReviewStatus.PENDING) return@launch
        val parsed = NaturalTaskParser.parse(commitment.action)
        val now = System.currentTimeMillis()
        val task = TaskEntity(
            title = parsed.title,
            details = buildString {
                append(commitment.action)
                if (commitment.actor.isNotBlank()) append("\n\nPersona: ${commitment.actor}")
                if (commitment.location.isNotBlank()) append("\nLugar: ${commitment.location}")
            },
            dueAt = commitment.dueAt,
            reminderAt = commitment.suggestedReminderAt,
            durationMinutes = parsed.durationMinutes ?: 25,
            priority = parsed.priority,
            status = if (commitment.dueAt == null) TaskStatus.INBOX else TaskStatus.PLANNED,
            createdAt = now,
            updatedAt = now
        )
        val taskId = taskRepository.add(task)
        if (task.dueAt != null || task.reminderAt != null) {
            reminderScheduler.schedule(task.copy(id = taskId))
        }
        conversationRepository.updateCommitment(
            commitment.copy(
                reviewStatus = CommitmentReviewStatus.CONVERTED,
                resultTaskId = taskId,
                updatedAt = System.currentTimeMillis()
            )
        )
        updateWidget()
        _events.emit(UiEvent.Message(appContext.getString(R.string.commitment_converted)))
    }

    fun dismissCommitment(commitmentId: Long) = viewModelScope.launch {
        val commitment = conversationRepository.getCommitment(commitmentId) ?: return@launch
        if (commitment.reviewStatus != CommitmentReviewStatus.PENDING) return@launch
        conversationRepository.updateCommitment(
            commitment.copy(
                reviewStatus = CommitmentReviewStatus.DISMISSED,
                updatedAt = System.currentTimeMillis()
            )
        )
        _events.emit(UiEvent.Message(appContext.getString(R.string.commitment_dismissed)))
    }

    fun deleteConversation(conversationId: Long) = viewModelScope.launch {
        conversationRepository.deleteConversation(conversationId)
        _events.emit(UiEvent.Message(appContext.getString(R.string.conversation_deleted)))
    }

    fun setObservationEnabled(enabled: Boolean) = viewModelScope.launch {
        contextualSettingsStore.enabled = enabled
        contextualSettingsStore.notificationSuggestions = enabled
        if (enabled) contextualSettingsStore.resume()
        observationRepository.recordConsent(
            if (enabled) ConsentEventType.OBSERVATION_ENABLED else ConsentEventType.OBSERVATION_DISABLED
        )
        refreshObservationRuntime()
        _events.emit(
            UiEvent.Message(
                appContext.getString(
                    if (enabled) R.string.observation_enabled_message else R.string.observation_disabled_message
                )
            )
        )
    }

    fun configureObservedSource(packageName: String, displayName: String, enabled: Boolean) = viewModelScope.launch {
        observationRepository.configureSource(packageName, displayName, enabled, onlyCommitments = true)
    }

    fun pauseObservationOneHour() = viewModelScope.launch {
        contextualSettingsStore.pauseOneHour()
        observationRepository.recordConsent(ConsentEventType.PAUSED)
        refreshObservationRuntime()
        _events.emit(UiEvent.Message(appContext.getString(R.string.observation_paused_message)))
    }

    fun resumeObservation() = viewModelScope.launch {
        contextualSettingsStore.resume()
        observationRepository.recordConsent(ConsentEventType.RESUMED)
        refreshObservationRuntime()
        _events.emit(UiEvent.Message(appContext.getString(R.string.observation_resumed_message)))
    }

    fun clearObservedConversationData() = viewModelScope.launch {
        conversationRepository.clearBySource(ConversationSourceType.NOTIFICATION)
        observationRepository.recordConsent(ConsentEventType.DATA_CLEARED)
        _events.emit(UiEvent.Message(appContext.getString(R.string.observation_data_cleared_message)))
    }

    fun recordNotificationPermissionReviewed() = viewModelScope.launch {
        observationRepository.recordConsent(ConsentEventType.PERMISSION_REVIEWED)
    }

    fun recordNotificationPermissionState(granted: Boolean) = viewModelScope.launch {
        observationRepository.recordConsent(
            if (granted) ConsentEventType.SYSTEM_PERMISSION_GRANTED else ConsentEventType.SYSTEM_PERMISSION_REVOKED
        )
    }

    fun revokeObservationInternally() = viewModelScope.launch {
        contextualSettingsStore.enabled = false
        contextualSettingsStore.notificationSuggestions = false
        contextualSettingsStore.resume()
        contextualSettingsStore.clearAllowedPackages()
        observationRepository.disableAllSources()
        observationRepository.recordConsent(ConsentEventType.INTERNAL_ACCESS_REVOKED)
        refreshObservationRuntime()
        _events.emit(UiEvent.Message(appContext.getString(R.string.observation_revoked_message)))
    }

    fun createAutomationFromText(instruction: String) = viewModelScope.launch {
        when (val parsed = AutomationRuleCatalog.parse(instruction)) {
            is AutomationParseResult.Unsupported -> _events.emit(UiEvent.Message(parsed.reason))
            is AutomationParseResult.Supported -> saveAutomation(parsed.template.toEntity())
        }
    }

    fun createAutomationTemplate(key: String) = viewModelScope.launch {
        val template = AutomationRuleCatalog.byKey(key) ?: return@launch
        saveAutomation(template.toEntity())
    }

    private suspend fun saveAutomation(rule: AutomationRuleEntity) {
        val (_, created) = automationRuleRepository.save(rule)
        _events.emit(UiEvent.Message(appContext.getString(if (created) R.string.automation_created else R.string.automation_already_exists)))
    }

    fun setAutomationEnabled(rule: AutomationRuleEntity, enabled: Boolean) = viewModelScope.launch {
        automationRuleRepository.update(rule.copy(enabled = enabled, updatedAt = System.currentTimeMillis()))
        AutomationScheduler.sync(appContext, automationRuleRepository)
        _events.emit(UiEvent.Message(appContext.getString(if (enabled) R.string.automation_enabled else R.string.automation_paused)))
    }

    fun deleteAutomation(rule: AutomationRuleEntity) = viewModelScope.launch {
        automationRuleRepository.delete(rule.id)
        AutomationScheduler.sync(appContext, automationRuleRepository)
        _events.emit(UiEvent.Message(appContext.getString(R.string.automation_deleted)))
    }

    fun testAutomation(rule: AutomationRuleEntity) = viewModelScope.launch {
        val outcome = automationEngine.runRule(rule, manual = true, test = true)
        _events.emit(UiEvent.Message(appContext.getString(R.string.automation_test_result, outcome.message)))
    }

    fun runAutomationNow(rule: AutomationRuleEntity) = viewModelScope.launch {
        val outcome = automationEngine.runRule(rule, manual = true)
        if (outcome.changed && outcome.logId > 0 && outcome.result == AutomationRuleResult.SUCCESS) {
            updateWidget()
            _events.emit(UiEvent.AutomationApplied(outcome.logId, outcome.message))
        } else {
            _events.emit(UiEvent.Message(outcome.message))
        }
    }

    private fun refreshObservationRuntime() {
        _observationRuntime.value = ObservationRuntimeState(
            enabled = contextualSettingsStore.enabled,
            pausedUntil = contextualSettingsStore.pausedUntil
        )
    }

    fun exportBackup(onReady: (String) -> Unit) = viewModelScope.launch {
        runCatching { backupManager.exportJson() }
            .onSuccess(onReady)
            .onFailure { _events.emit(UiEvent.Message(appContext.getString(R.string.backup_export_failed))) }
    }

    /**
     * Inicia el flujo de restauración con estados observables.
     *
     * La confirmación del usuario (diálogo "¿Restaurar esta copia?") ocurre en
     * la UI ANTES de llamar a este método; aquí se bloquean pulsaciones
     * duplicadas y restores concurrentes, y el éxito solo se publica después
     * de que [BackupManager] haya verificado la persistencia.
     */
    fun restoreBackup(raw: String, fileName: String? = null) {
        // Doble pulsación o proceso ya en curso.
        if (_backupState.value !is BackupRestoreState.Idle) return
        if (!restoreMutex.tryLock()) return
        _backupState.value = BackupRestoreState.FileSelected(fileName)
        viewModelScope.launch {
            try {
                val result = backupManager.importBackup(raw) { phase ->
                    _backupState.value = when (phase) {
                        RestorePhase.VALIDATING -> BackupRestoreState.Validating
                        RestorePhase.CREATING_SAFETY_BACKUP -> BackupRestoreState.CreatingSafetyBackup
                        RestorePhase.RESTORING -> BackupRestoreState.Restoring
                        RestorePhase.VERIFYING -> BackupRestoreState.Verifying
                    }
                }
                if (result.success) {
                    val restored = preferencesRepository.preferences.first()
                    if (BuildConfig.SELF_UPDATE_ENABLED) {
                        if (restored.autoUpdateEnabled) com.ordia.app.updates.OrdiaUpdateManager.schedule(appContext)
                        else com.ordia.app.updates.OrdiaUpdateManager.cancelSchedule(appContext)
                    }
                    if (BuildConfig.OVERLAY_ENABLED) {
                        appContext.stopService(android.content.Intent(appContext, com.ordia.app.overlay.GuardianOverlayService::class.java))
                    }
                    _backupState.value = BackupRestoreState.Success(result.message)
                    updateWidget()
                } else {
                    _backupState.value = BackupRestoreState.Error(result.message)
                }
            } catch (error: Exception) {
                // Red de seguridad: BackupManager no debería lanzar, pero si algo
                // inesperado ocurre, el usuario ve un error y la UI vuelve a Idle.
                _backupState.value = BackupRestoreState.Error(
                    "No se pudo restaurar la copia: ${error.message ?: "error inesperado"}"
                )
            } finally {
                restoreMutex.unlock()
            }
        }
    }

    /** Vuelve al estado inicial tras mostrar el resultado del restore. */
    fun dismissRestoreResult() {
        _backupState.value = BackupRestoreState.Idle
    }

    fun setThemeMode(value: ThemeMode) = viewModelScope.launch { preferencesRepository.setThemeMode(value) }
    fun setInterfaceMode(value: InterfaceMode) = viewModelScope.launch {
        try {
            preferencesRepository.setInterfaceMode(value)
        } catch (t: Throwable) {
            // Un fallo de escritura no debe dejar la selección sin explicación: se avisa y
            // el usuario puede volver a tocar el modo.
            _events.tryEmit(UiEvent.Message(appContext.getString(R.string.onboarding_save_error)))
        }
    }
    fun setGuardianEnabled(value: Boolean) = viewModelScope.launch { preferencesRepository.setGuardianEnabled(value) }
    fun setGuardianMode(value: GuardianMode) = viewModelScope.launch { preferencesRepository.setGuardianMode(value) }
    fun setQuietHours(start: Int, end: Int) = viewModelScope.launch { preferencesRepository.setQuietHours(start, end) }
    fun setOnboardingComplete(value: Boolean = true) = viewModelScope.launch { preferencesRepository.setOnboardingComplete(value) }
    fun setWeekStartsMonday(value: Boolean) = viewModelScope.launch { preferencesRepository.setWeekStartsMonday(value) }
    fun setDefaultFocusMinutes(value: Int) = viewModelScope.launch { preferencesRepository.setDefaultFocusMinutes(value) }
    fun setReduceMotion(value: Boolean) = viewModelScope.launch { preferencesRepository.setReduceMotion(value) }
    fun setCompactNavigation(value: Boolean) = viewModelScope.launch { preferencesRepository.setCompactNavigation(value) }
    fun setDarkMode(enabled: Boolean) = viewModelScope.launch { preferencesRepository.setDarkMode(enabled) }

    private val _onboardingBusy = MutableStateFlow(false)

    /** true mientras se persiste la finalización del onboarding (bloquea dobles toques en "Entrar a Ordia"). */
    val onboardingBusy: StateFlow<Boolean> = _onboardingBusy.asStateFlow()

    private val onboardingCompleter = OnboardingCompleter {
        preferencesRepository.setOnboardingComplete(true)
    }

    /**
     * Finaliza el onboarding de forma segura:
     * - Bloquea dobles disparos (un segundo toque mientras persiste no navega dos veces).
     * - Navega de forma reactiva cuando [UserPreferences.onboardingComplete] llega a true,
     *   es decir, cuando la persistencia ya terminó (no exige reiniciar la aplicación).
     * - Si la escritura falla, muestra un mensaje comprensible y deja reintentar: el
     *   usuario nunca queda atrapado en la pantalla de selección de modo.
     */
    fun finishOnboarding() {
        if (_onboardingBusy.value) return
        _onboardingBusy.value = true
        viewModelScope.launch {
            val ok = onboardingCompleter.run()
            if (!ok) {
                _events.tryEmit(UiEvent.Message(appContext.getString(R.string.onboarding_save_error)))
            }
            _onboardingBusy.value = false
        }
    }

    private fun updateWidget() = OrdiaWidgetUpdater.updateAll(appContext)

    class Factory(
        private val context: Context,
        private val taskRepository: TaskRepository,
        private val projectRepository: ProjectRepository,
        private val noteRepository: NoteRepository,
        private val habitRepository: HabitRepository,
        private val focusRepository: FocusRepository,
        private val routineRepository: RoutineRepository,
        private val tagRepository: TagRepository,
        private val attachmentRepository: AttachmentRepository,
        private val attachmentStorage: AttachmentStorage,
        private val automationLogRepository: AutomationLogRepository,
        private val automationRuleRepository: AutomationRuleRepository,
        private val automationEngine: AutomationEngine,
        private val captureRepository: CaptureRepository,
        private val conversationRepository: ConversationRepository,
        private val observationRepository: ObservationRepository,
        private val contextualSettingsStore: ContextualSettingsStore,
        private val preferencesRepository: PreferencesRepository,
        private val reminderScheduler: ReminderScheduler,
        private val backupManager: BackupManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = OrdiaViewModel(
            context.applicationContext,
            taskRepository,
            projectRepository,
            noteRepository,
            habitRepository,
            focusRepository,
            routineRepository,
            tagRepository,
            attachmentRepository,
            attachmentStorage,
            automationLogRepository,
            automationRuleRepository,
            automationEngine,
            captureRepository,
            conversationRepository,
            observationRepository,
            contextualSettingsStore,
            preferencesRepository,
            reminderScheduler,
            backupManager
        ) as T
    }

    private companion object {
        const val CAPTURE_DUPLICATE_WINDOW_MS = 5_000L
    }
}
