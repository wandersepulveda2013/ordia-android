package com.ordia.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ordia.app.backup.BackupManager
import com.ordia.app.data.local.AttachmentEntity
import com.ordia.app.data.local.AttachmentOwnerType
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
import com.ordia.app.data.repository.FocusRepository
import com.ordia.app.data.repository.HabitRepository
import com.ordia.app.data.repository.NoteRepository
import com.ordia.app.data.repository.ProjectRepository
import com.ordia.app.data.repository.RoutineRepository
import com.ordia.app.data.repository.TagRepository
import com.ordia.app.data.repository.TaskRepository
import com.ordia.app.domain.DateRules
import com.ordia.app.domain.DayPlanner
import com.ordia.app.domain.GuardianCoach
import com.ordia.app.domain.HabitRules
import com.ordia.app.domain.NoteBlock
import com.ordia.app.domain.NoteBlockCodec
import com.ordia.app.domain.NaturalTaskParser
import com.ordia.app.domain.RecurrenceEngine
import com.ordia.app.domain.TaskRules
import com.ordia.app.reminders.ReminderScheduler
import com.ordia.app.widget.OrdiaWidgetUpdater
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    data class TaskSaved(val id: Long) : UiEvent
    data class NoteSaved(val id: Long) : UiEvent
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
    val pendingTasks: List<TaskEntity> get() = rootTasks.filter { !it.completed && !it.archived }
    val inboxTasks: List<TaskEntity> get() = pendingTasks.filter { it.status == TaskStatus.INBOX && it.dueAt == null }
    val overdueTasks: List<TaskEntity> get() = pendingTasks.filter { TaskRules.isOverdue(it) }
    val todayTasks: List<TaskEntity> get() = pendingTasks.filter { TaskRules.isDueToday(it) }
    val completedCount: Int get() = rootTasks.count { it.completed }
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
    fun subtasks(parentId: Long): List<TaskEntity> = tasks.filter { it.parentTaskId == parentId && !it.archived }.sortedBy { it.sortOrder }
    fun routineSteps(routineId: Long): List<RoutineStepEntity> = routineSteps.filter { it.routineId == routineId }.sortedBy { it.position }
    fun attachmentsFor(type: AttachmentOwnerType, ownerId: Long): List<AttachmentEntity> =
        attachments.filter { it.ownerType == type && it.ownerId == ownerId }
    fun tagsForTask(taskId: Long): List<TagEntity> {
        val ids = taskTags.filter { it.taskId == taskId }.map { it.tagId }.toSet()
        return tags.filter { it.id in ids }
    }
    fun projectProgress(projectId: Long): Float {
        val related = rootTasks.filter { it.projectId == projectId && !it.archived }
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
    private val preferencesRepository: PreferencesRepository,
    private val reminderScheduler: ReminderScheduler,
    private val backupManager: BackupManager
) : ViewModel() {
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

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

    fun saveTask(task: TaskEntity, tagIds: Set<Long> = emptySet()) {
        val clean = task.title.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val normalized = task.copy(
                title = clean,
                details = task.details.trim(),
                status = when {
                    task.completed -> TaskStatus.COMPLETED
                    task.status == TaskStatus.INBOX && task.dueAt != null -> TaskStatus.PLANNED
                    else -> task.status
                },
                updatedAt = now
            )
            val id = if (normalized.id == 0L) taskRepository.add(normalized.copy(createdAt = now)) else {
                taskRepository.update(normalized)
                normalized.id
            }
            if (normalized.reminderAt != null || normalized.dueAt != null) reminderScheduler.schedule(normalized.copy(id = id)) else reminderScheduler.cancel(id)
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
        val parsed = NaturalTaskParser.parse(input)
        addTask(parsed.title, dueAt = parsed.dueAt, priority = parsed.priority)
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

    fun toggleTask(task: TaskEntity) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val completing = !task.completed
            taskRepository.update(
                task.copy(
                    completed = completing,
                    status = if (completing) TaskStatus.COMPLETED else if (task.dueAt == null) TaskStatus.INBOX else TaskStatus.PLANNED,
                    completedAt = if (completing) now else null,
                    updatedAt = now
                )
            )
            if (completing) {
                reminderScheduler.cancel(task.id)
                RecurrenceEngine.nextOccurrence(task, now)?.let { next ->
                    val nextId = taskRepository.add(next)
                    reminderScheduler.schedule(next.copy(id = nextId))
                }
            } else {
                reminderScheduler.schedule(task)
            }
            updateWidget()
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            reminderScheduler.cancel(task.id)
            taskRepository.archive(task.id)
            updateWidget()
            _events.emit(UiEvent.Message("Tarea movida al archivo."))
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
            tagRepository.linkAll(id, uiState.value.tagsForTask(task.id).map { it.id })
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
        _events.emit(UiEvent.Message("Proyecto archivado."))
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
        _events.emit(UiEvent.Message("Nota archivada."))
    }

    fun togglePin(note: NoteEntity) = viewModelScope.launch {
        noteRepository.update(note.copy(pinned = !note.pinned, updatedAt = System.currentTimeMillis()))
    }

    fun addAttachment(attachment: AttachmentEntity) = viewModelScope.launch {
        attachmentRepository.add(attachment)
        _events.emit(UiEvent.Message("Archivo adjuntado."))
    }

    fun deleteAttachment(attachment: AttachmentEntity) = viewModelScope.launch {
        attachmentRepository.delete(attachment)
        _events.emit(UiEvent.Message("Adjunto eliminado de la nota."))
    }

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
        _events.emit(UiEvent.Message("Hábito movido al archivo."))
    }

    fun saveRoutine(routine: RoutineEntity, stepTitles: List<String>) {
        val clean = routine.name.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = if (routine.id == 0L) routineRepository.add(routine.copy(name = clean, createdAt = now, updatedAt = now)) else {
                routineRepository.update(routine.copy(name = clean, updatedAt = now))
                routine.id
            }
            val existing = uiState.value.routineSteps(id)
            existing.forEach { routineRepository.deleteStep(it) }
            stepTitles.map { it.trim() }.filter { it.isNotBlank() }.forEachIndexed { index, title ->
                routineRepository.addStep(RoutineStepEntity(routineId = id, title = title, position = index))
            }
        }
    }

    fun runRoutine(routine: RoutineEntity) {
        viewModelScope.launch {
            val steps = routineRepository.stepsFor(routine.id)
            val now = System.currentTimeMillis()
            steps.forEachIndexed { index, step ->
                taskRepository.add(
                    TaskEntity(
                        title = step.title,
                        details = "Rutina: ${routine.name}",
                        durationMinutes = step.durationMinutes,
                        status = TaskStatus.INBOX,
                        sortOrder = index,
                        createdAt = now + index,
                        updatedAt = now + index
                    )
                )
            }
            updateWidget()
            _events.emit(UiEvent.Message("La rutina se añadió a tu bandeja."))
        }
    }

    fun archiveRoutine(routine: RoutineEntity) = viewModelScope.launch {
        routineRepository.archive(routine.id)
        _events.emit(UiEvent.Message("Rutina movida al archivo."))
    }

    fun restoreArchived(kind: String, id: Long) = viewModelScope.launch {
        when (kind) {
            "task" -> taskRepository.restore(id)
            "project" -> projectRepository.restore(id)
            "note" -> noteRepository.restore(id)
            "habit" -> habitRepository.restore(id)
            "routine" -> routineRepository.restore(id)
        }
        updateWidget()
        _events.emit(UiEvent.Message("Elemento restaurado."))
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
        _events.emit(UiEvent.Message("Elemento eliminado definitivamente."))
    }

    fun addTag(name: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        viewModelScope.launch { tagRepository.add(TagEntity(name = clean)) }
    }

    fun applyDayPlan(plan: DayPlanner.Plan) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        var updated = 0
        plan.blocks.forEach { block ->
            val task = taskRepository.get(block.taskId) ?: return@forEach
            val start = DateRules.toEpochMillis(plan.date, block.startMinute)
            val end = DateRules.toEpochMillis(plan.date, block.endMinute)
            val normalized = task.copy(
                startAt = start,
                dueAt = task.dueAt ?: end,
                status = if (task.completed) TaskStatus.COMPLETED else TaskStatus.PLANNED,
                updatedAt = now
            )
            taskRepository.update(normalized)
            normalized.reminderAt?.let { reminderScheduler.schedule(normalized) }
            updated++
        }
        updateWidget()
        _events.emit(
            UiEvent.Message(
                if (updated == 0) "No había tareas para planificar."
                else "Plan aplicado a $updated ${if (updated == 1) "tarea" else "tareas"}."
            )
        )
    }

    fun saveFocusSession(taskId: Long?, startedAt: Long, endedAt: Long, plannedMinutes: Int, completed: Boolean, notes: String = "") {
        viewModelScope.launch {
            val actual = ((endedAt - startedAt) / 60_000L).toInt().coerceAtLeast(0)
            focusRepository.add(FocusSessionEntity(taskId = taskId, startedAt = startedAt, endedAt = endedAt, plannedMinutes = plannedMinutes, actualMinutes = actual, completed = completed, notes = notes))
            _events.emit(UiEvent.Message(if (completed) "Sesión de enfoque completada." else "Sesión guardada."))
        }
    }

    fun captureSharedText(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        addNote(clean.lineSequence().firstOrNull()?.take(60).orEmpty().ifBlank { "Contenido compartido" }, clean)
    }

    fun exportBackup(onReady: (String) -> Unit) = viewModelScope.launch {
        runCatching { backupManager.exportJson() }
            .onSuccess(onReady)
            .onFailure { _events.emit(UiEvent.Message("No se pudo crear la copia: ${it.message}")) }
    }

    fun importBackup(raw: String) = viewModelScope.launch {
        val result = backupManager.importJson(raw)
        _events.emit(UiEvent.Message(result.message))
        updateWidget()
    }

    fun setThemeMode(value: ThemeMode) = viewModelScope.launch { preferencesRepository.setThemeMode(value) }
    fun setInterfaceMode(value: InterfaceMode) = viewModelScope.launch { preferencesRepository.setInterfaceMode(value) }
    fun setGuardianEnabled(value: Boolean) = viewModelScope.launch { preferencesRepository.setGuardianEnabled(value) }
    fun setGuardianMode(value: GuardianMode) = viewModelScope.launch { preferencesRepository.setGuardianMode(value) }
    fun setQuietHours(start: Int, end: Int) = viewModelScope.launch { preferencesRepository.setQuietHours(start, end) }
    fun setOnboardingComplete(value: Boolean = true) = viewModelScope.launch { preferencesRepository.setOnboardingComplete(value) }
    fun setWeekStartsMonday(value: Boolean) = viewModelScope.launch { preferencesRepository.setWeekStartsMonday(value) }
    fun setDefaultFocusMinutes(value: Int) = viewModelScope.launch { preferencesRepository.setDefaultFocusMinutes(value) }
    fun setReduceMotion(value: Boolean) = viewModelScope.launch { preferencesRepository.setReduceMotion(value) }
    fun setCompactNavigation(value: Boolean) = viewModelScope.launch { preferencesRepository.setCompactNavigation(value) }
    fun setDarkMode(enabled: Boolean) = viewModelScope.launch { preferencesRepository.setDarkMode(enabled) }

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
            preferencesRepository,
            reminderScheduler,
            backupManager
        ) as T
    }
}
