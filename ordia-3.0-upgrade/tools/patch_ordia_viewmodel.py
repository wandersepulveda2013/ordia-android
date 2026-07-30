from pathlib import Path

path = Path("app/src/main/java/com/ordia/app/ui/OrdiaViewModel.kt")
text = path.read_text(encoding="utf-8")
original = text

# The first 2.0 draft incremented experience from UI events. That could be farmed by
# toggling the same record repeatedly. OrdiaRoot now synchronizes a monotonic value derived
# from real records, so remove the obsolete injection if an earlier package added it.
text = text.replace(
    '            if (completing) preferencesRepository.addGuardianExperience(12, "task_complete")\n',
    "",
)
text = text.replace(
    '            if (note.id == 0L) preferencesRepository.addGuardianExperience(2, "note_created")\n',
    "",
)
text = text.replace(
    '            if (completed) preferencesRepository.addGuardianExperience((5 + actual / 5).coerceAtMost(30), "focus_complete")\n',
    "",
)
text = text.replace(
    '''            if (current >= habit.targetPerPeriod) {
                habitRepository.removeLog(habit.id, date.toEpochDay())
            } else {
                habitRepository.log(HabitLogEntity(habit.id, date.toEpochDay(), current + 1))
                val completed = current + 1 >= habit.targetPerPeriod
                preferencesRepository.addGuardianExperience(if (completed) 10 else 3, if (completed) "habit_complete" else "habit_progress")
            }
''',
    '''            if (current >= habit.targetPerPeriod) habitRepository.removeLog(habit.id, date.toEpochDay())
            else habitRepository.log(HabitLogEntity(habit.id, date.toEpochDay(), current + 1))
''',
)


# A restored backup can change update preferences and always disables the floating overlay.
# Reconcile those runtime services only after BackupManager reports a successful restore.
if "import kotlinx.coroutines.flow.first" not in text:
    text = text.replace(
        "import kotlinx.coroutines.flow.combine\n",
        "import kotlinx.coroutines.flow.combine\nimport kotlinx.coroutines.flow.first\n",
    )

old_import = """    fun importBackup(raw: String) = viewModelScope.launch {
        val result = backupManager.importJson(raw)
        _events.emit(UiEvent.Message(result.message))
        updateWidget()
    }
"""
new_import = """    fun importBackup(raw: String) = viewModelScope.launch {
        val result = backupManager.importJson(raw)
        if (result.success) {
            val restored = preferencesRepository.preferences.first()
            if (restored.autoUpdateEnabled) com.ordia.app.updates.OrdiaUpdateManager.schedule(appContext)
            else com.ordia.app.updates.OrdiaUpdateManager.cancelSchedule(appContext)
            appContext.stopService(android.content.Intent(appContext, com.ordia.app.overlay.GuardianOverlayService::class.java))
        }
        _events.emit(UiEvent.Message(result.message))
        updateWidget()
    }
"""
if old_import in text:
    text = text.replace(old_import, new_import)
elif "com.ordia.app.updates.OrdiaUpdateManager.schedule(appContext)" not in text:
    raise SystemExit("Could not patch importBackup runtime reconciliation safely.")



# Ordia 3.0 task-state integrity improvements.
text = text.replace(
    "val pendingTasks: List<TaskEntity> get() = rootTasks.filter { !it.completed && !it.archived }",
    "val pendingTasks: List<TaskEntity> get() = rootTasks.filter { !it.completed && !it.archived && it.status != TaskStatus.CANCELLED }",
)
text = text.replace(
    "val todayTasks: List<TaskEntity> get() = pendingTasks.filter { TaskRules.isDueToday(it) }",
    "val todayTasks: List<TaskEntity> get() = pendingTasks.filter { TaskRules.isDueToday(it) && !TaskRules.isOverdue(it) }",
)
text = text.replace(
    "tasks.filter { it.parentTaskId == parentId && !it.archived }.sortedBy { it.sortOrder }",
    "tasks.filter { it.parentTaskId == parentId && !it.archived && it.status != TaskStatus.CANCELLED }.sortedBy { it.sortOrder }",
)
text = text.replace(
    "val related = rootTasks.filter { it.projectId == projectId && !it.archived }",
    "val related = rootTasks.filter { it.projectId == projectId && !it.archived && it.status != TaskStatus.CANCELLED }",
)
text = text.replace(
    "if (normalized.reminderAt != null || normalized.dueAt != null) reminderScheduler.schedule(normalized.copy(id = id)) else reminderScheduler.cancel(id)",
    "if (normalized.status != TaskStatus.CANCELLED && !normalized.completed && (normalized.reminderAt != null || normalized.dueAt != null)) reminderScheduler.schedule(normalized.copy(id = id)) else reminderScheduler.cancel(id)",
)
if "import com.ordia.app.domain.TaskMutationGate" not in text:
    text = text.replace("import com.ordia.app.domain.TaskRules\n", "import com.ordia.app.domain.TaskRules\nimport com.ordia.app.domain.TaskMutationGate\n")
if "import kotlinx.coroutines.sync.withLock" not in text:
    text = text.replace("import kotlinx.coroutines.launch\n", "import kotlinx.coroutines.launch\nimport kotlinx.coroutines.sync.withLock\n")
old_toggle = '''    fun toggleTask(task: TaskEntity) {
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
'''
new_toggle = '''    fun toggleTask(task: TaskEntity) {
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
                    }
                } else if (updated.reminderAt != null || updated.dueAt != null) reminderScheduler.schedule(updated)
            }
            updateWidget()
        }
    }
'''
if old_toggle in text:
    text = text.replace(old_toggle, new_toggle)
elif "TaskMutationGate.mutex.withLock" not in text:
    raise SystemExit("Could not patch toggleTask safely.")

if text != original:
    path.write_text(text, encoding="utf-8")
    print("Removed obsolete incremental guardian XP hooks from OrdiaViewModel.")
else:
    print("OrdiaViewModel requires no guardian XP cleanup.")
