package com.ordia.app.di

import android.content.Context
import com.ordia.app.backup.BackupManager
import com.ordia.app.data.local.OrdiaDatabase
import com.ordia.app.data.preferences.PreferencesRepository
import com.ordia.app.data.repository.AttachmentRepository
import com.ordia.app.data.repository.FocusRepository
import com.ordia.app.data.repository.HabitRepository
import com.ordia.app.data.repository.NoteRepository
import com.ordia.app.data.repository.ProjectRepository
import com.ordia.app.data.repository.RoutineRepository
import com.ordia.app.data.repository.TagRepository
import com.ordia.app.data.repository.TaskRepository
import com.ordia.app.reminders.ReminderScheduler

class AppContainer(context: Context) {
    val database: OrdiaDatabase = OrdiaDatabase.getInstance(context)

    val preferencesRepository = PreferencesRepository(context)
    val taskRepository = TaskRepository(database.taskDao(), database, database.attachmentDao())
    val projectRepository = ProjectRepository(database.projectDao())
    val noteRepository = NoteRepository(database.noteDao())
    val habitRepository = HabitRepository(database.habitDao(), database.habitLogDao())
    val focusRepository = FocusRepository(database.focusSessionDao())
    val routineRepository = RoutineRepository(database.routineDao(), database.routineStepDao())
    val tagRepository = TagRepository(database.tagDao(), database.taskTagDao())
    val attachmentRepository = AttachmentRepository(database.attachmentDao())
    val reminderScheduler = ReminderScheduler(context)
    val backupManager = BackupManager(database)
}
