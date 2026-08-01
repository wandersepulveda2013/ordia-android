package com.ordia.app.di

import android.content.Context
import com.ordia.app.backup.BackupManager
import com.ordia.app.backup.RoomBackupStore
import com.ordia.app.context.ContextualSettingsStore
import com.ordia.app.context.ContextualSuggestionStore
import com.ordia.app.data.local.OrdiaDatabase
import com.ordia.app.data.preferences.PreferencesRepository
import com.ordia.app.data.repository.AttachmentRepository
import com.ordia.app.data.repository.AutomationLogRepository
import com.ordia.app.data.repository.AutomationRuleRepository
import com.ordia.app.data.repository.FocusRepository
import com.ordia.app.data.repository.CaptureRepository
import com.ordia.app.data.repository.ConversationRepository
import com.ordia.app.data.repository.HabitRepository
import com.ordia.app.data.repository.NoteRepository
import com.ordia.app.data.repository.ObservationRepository
import com.ordia.app.data.repository.ProjectRepository
import com.ordia.app.data.repository.RoutineRepository
import com.ordia.app.data.repository.TagRepository
import com.ordia.app.data.repository.TaskRepository
import com.ordia.app.reminders.ReminderScheduler
import com.ordia.app.automation.AutomationEngine

class AppContainer(context: Context) {
    val database: OrdiaDatabase = OrdiaDatabase.getInstance(context)
    val contextualSettingsStore = ContextualSettingsStore(context)
    val contextualSuggestionStore = ContextualSuggestionStore(context)
    val preferencesRepository = PreferencesRepository(context)
    val taskRepository = TaskRepository(database.taskDao())
    val projectRepository = ProjectRepository(database.projectDao())
    val noteRepository = NoteRepository(database.noteDao())
    val habitRepository = HabitRepository(database.habitDao(), database.habitLogDao())
    val focusRepository = FocusRepository(database.focusSessionDao())
    val routineRepository = RoutineRepository(database.routineDao(), database.routineStepDao())
    val tagRepository = TagRepository(database.tagDao(), database.taskTagDao())
    val attachmentRepository = AttachmentRepository(database.attachmentDao())
    val automationLogRepository = AutomationLogRepository(database.automationLogDao())
    val automationRuleRepository = AutomationRuleRepository(database.automationRuleDao(), database.automationLogDao())
    val captureRepository = CaptureRepository(database.captureDao())
    val conversationRepository = ConversationRepository(database.conversationDao())
    val observationRepository = ObservationRepository(database.observationDao())
    val reminderScheduler = ReminderScheduler(context)
    val automationEngine = AutomationEngine(
        automationRuleRepository,
        taskRepository,
        conversationRepository,
        reminderScheduler
    )
    val backupManager = BackupManager(
        backupStore = RoomBackupStore(database),
        preferences = preferencesRepository,
        reminderScheduler = reminderScheduler,
        preRestoreBackupFile = java.io.File(context.filesDir, com.ordia.app.backup.BackupManager.PRE_RESTORE_BACKUP_FILENAME)
    )
}
