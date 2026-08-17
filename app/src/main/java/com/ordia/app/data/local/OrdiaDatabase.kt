package com.ordia.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TaskEntity::class,
        ProjectEntity::class,
        NoteEntity::class,
        NoteFolderEntity::class,
        NoteLabelEntity::class,
        NoteLabelCrossRef::class,
        NoteVersionEntity::class,
        HabitEntity::class,
        HabitLogEntity::class,
        FocusSessionEntity::class,
        RoutineEntity::class,
        RoutineStepEntity::class,
        TagEntity::class,
        TaskTagCrossRef::class,
        AttachmentEntity::class,
        AutomationLogEntity::class,
        CaptureEntity::class,
        CaptureDraftEntity::class,
        ConversationEntity::class,
        CommitmentEntity::class,
        ObservedSourceEntity::class,
        ConsentEventEntity::class,
        AutomationRuleEntity::class
    ],
    version = 9,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class OrdiaDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun projectDao(): ProjectDao
    abstract fun noteDao(): NoteDao
    abstract fun noteFolderDao(): NoteFolderDao
    abstract fun noteLabelDao(): NoteLabelDao
    abstract fun noteLabelCrossRefDao(): NoteLabelCrossRefDao
    abstract fun noteVersionDao(): NoteVersionDao
    abstract fun habitDao(): HabitDao
    abstract fun habitLogDao(): HabitLogDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun routineDao(): RoutineDao
    abstract fun routineStepDao(): RoutineStepDao
    abstract fun tagDao(): TagDao
    abstract fun taskTagDao(): TaskTagDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun automationLogDao(): AutomationLogDao
    abstract fun captureDao(): CaptureDao
    abstract fun conversationDao(): ConversationDao
    abstract fun observationDao(): ObservationDao
    abstract fun automationRuleDao(): AutomationRuleDao

    companion object {
        @Volatile private var instance: OrdiaDatabase? = null

        /**
         * Índices para consultas frecuentes:
         * - automation_log(type, createdAt): historial por tipo de automatización.
         * - captures(resultId): resolución del historial de captura hacia la entidad.
         * - notes(pinned, updatedAt): orden estable de Notas (fijadas primero, luego recientes).
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_automation_log_type_createdAt ON automation_log(type, createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_captures_resultId ON captures(resultId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_pinned_updatedAt ON notes(pinned, updatedAt)")
            }
        }

        /**
         * Migración 8 → 9: reconstrucción de ORDÍA como bloc de notas avanzado.
         *
         * NO toca ni pierde ningún dato preexistente: las notas, tareas y demás
         * colecciones se conservan íntegras. Solamente enriquece `notes` con
         * nuevas columnas (con defaults seguros) y crea las tablas auxiliares
         * del nuevo modelo (carpetas, etiquetas de nota, historial de versiones).
         *
         * Como `notes` gana una nueva FK hacia `note_folders`, se reconstruye la
         * tabla preservando filas y valores existentes (estrategia recomendada
         * por Room cuando se añaden FKs/índices sobre una tabla ya poblada).
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Tablas auxiliares del nuevo modelo de notas.
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS note_folders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        colorHex TEXT NOT NULL DEFAULT '',
                        parentFolderId INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(parentFolderId) REFERENCES note_folders(id) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_folders_parentFolderId ON note_folders(parentFolderId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_note_folders_name ON note_folders(name)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS note_labels (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        colorHex TEXT NOT NULL DEFAULT '#9A8F7F'
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_note_labels_name ON note_labels(name)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS note_label_cross_ref (
                        noteId INTEGER NOT NULL,
                        labelId INTEGER NOT NULL,
                        PRIMARY KEY(noteId, labelId),
                        FOREIGN KEY(noteId) REFERENCES notes(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(labelId) REFERENCES note_labels(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_label_cross_ref_noteId ON note_label_cross_ref(noteId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_label_cross_ref_labelId ON note_label_cross_ref(labelId)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS note_versions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        noteId INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        blocksData TEXT NOT NULL DEFAULT '',
                        body TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(noteId) REFERENCES notes(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_versions_noteId ON note_versions(noteId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_versions_createdAt ON note_versions(createdAt)")

                // Reconstruir `notes` con las nuevas columnas + FK hacia note_folders.
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS notes_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        body TEXT NOT NULL,
                        blocksData TEXT NOT NULL DEFAULT '',
                        projectId INTEGER,
                        folderId INTEGER,
                        pinned INTEGER NOT NULL DEFAULT 0,
                        favorite INTEGER NOT NULL DEFAULT 0,
                        locked INTEGER NOT NULL DEFAULT 0,
                        colorHex TEXT NOT NULL DEFAULT '',
                        archived INTEGER NOT NULL DEFAULT 0,
                        trashed INTEGER NOT NULL DEFAULT 0,
                        trashedAt INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(projectId) REFERENCES projects(id) ON UPDATE NO ACTION ON DELETE SET NULL,
                        FOREIGN KEY(folderId) REFERENCES note_folders(id) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO notes_new (id, title, body, blocksData, projectId, folderId, pinned, favorite, locked, colorHex, archived, trashed, trashedAt, createdAt, updatedAt)
                    SELECT id, title, body, blocksData, projectId, NULL, pinned, 0, 0, '', archived, 0, NULL, createdAt, updatedAt FROM notes
                """.trimIndent())
                // Índices de la nueva tabla, idénticos a los declarados en la entidad.
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_new_projectId ON notes_new(projectId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_new_folderId ON notes_new(folderId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_new_pinned ON notes_new(pinned)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_new_favorite ON notes_new(favorite)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_new_locked ON notes_new(locked)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_new_archived ON notes_new(archived)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_new_trashed ON notes_new(trashed)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_new_colorHex ON notes_new(colorHex)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_new_pinned_updatedAt ON notes_new(pinned, updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_new_trashed_updatedAt ON notes_new(trashed, updatedAt)")
                db.execSQL("DROP TABLE notes")
                db.execSQL("ALTER TABLE notes_new RENAME TO notes")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS automation_rules (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        instruction TEXT NOT NULL,
                        trigger TEXT NOT NULL,
                        condition TEXT NOT NULL,
                        action TEXT NOT NULL,
                        explanation TEXT NOT NULL,
                        enabled INTEGER NOT NULL DEFAULT 0,
                        frequencyMinutes INTEGER NOT NULL DEFAULT 60,
                        maxRunsPerDay INTEGER NOT NULL DEFAULT 3,
                        lastRunAt INTEGER,
                        lastResult TEXT NOT NULL DEFAULT 'NEVER',
                        lastError TEXT NOT NULL DEFAULT '',
                        definitionHash TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_automation_rules_enabled ON automation_rules(enabled)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_automation_rules_trigger ON automation_rules(trigger)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_automation_rules_definitionHash ON automation_rules(definitionHash)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS observed_sources (
                        packageName TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        enabled INTEGER NOT NULL DEFAULT 0,
                        onlyCommitments INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(packageName)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_observed_sources_enabled ON observed_sources(enabled)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_observed_sources_updatedAt ON observed_sources(updatedAt)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS consent_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        eventType TEXT NOT NULL,
                        sourcePackage TEXT NOT NULL DEFAULT '',
                        occurredAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_consent_events_occurredAt ON consent_events(occurredAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_consent_events_sourcePackage ON consent_events(sourcePackage)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS conversations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sourceType TEXT NOT NULL,
                        sourcePackage TEXT NOT NULL DEFAULT '',
                        title TEXT NOT NULL,
                        participants TEXT NOT NULL DEFAULT '',
                        summary TEXT NOT NULL,
                        rawContent TEXT NOT NULL DEFAULT '',
                        retainsOriginal INTEGER NOT NULL DEFAULT 0,
                        contentHash TEXT NOT NULL,
                        messageCount INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_conversations_contentHash ON conversations(contentHash)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_conversations_sourceType ON conversations(sourceType)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_conversations_createdAt ON conversations(createdAt)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS commitments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        conversationId INTEGER NOT NULL,
                        kind TEXT NOT NULL,
                        owner TEXT NOT NULL,
                        actor TEXT NOT NULL DEFAULT '',
                        action TEXT NOT NULL,
                        location TEXT NOT NULL DEFAULT '',
                        dueAt INTEGER,
                        confidence REAL NOT NULL,
                        suggestedReminderAt INTEGER,
                        reviewStatus TEXT NOT NULL,
                        fingerprint TEXT NOT NULL,
                        resultTaskId INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(conversationId) REFERENCES conversations(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(resultTaskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_commitments_conversationId ON commitments(conversationId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_commitments_reviewStatus ON commitments(reviewStatus)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_commitments_dueAt ON commitments(dueAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_commitments_resultTaskId ON commitments(resultTaskId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_commitments_fingerprint ON commitments(fingerprint)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS captures (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        content TEXT NOT NULL,
                        source TEXT NOT NULL,
                        requestedTarget TEXT NOT NULL,
                        resolvedTarget TEXT NOT NULL,
                        status TEXT NOT NULL,
                        attachmentUri TEXT NOT NULL DEFAULT '',
                        mimeType TEXT NOT NULL DEFAULT '',
                        fingerprint TEXT NOT NULL DEFAULT '',
                        resultType TEXT NOT NULL DEFAULT '',
                        resultId INTEGER,
                        errorCode TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_captures_createdAt ON captures(createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_captures_status ON captures(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_captures_fingerprint ON captures(fingerprint)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS capture_drafts (
                        slot TEXT NOT NULL,
                        content TEXT NOT NULL,
                        target TEXT NOT NULL,
                        attachmentUri TEXT NOT NULL DEFAULT '',
                        mimeType TEXT NOT NULL DEFAULT '',
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(slot)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS automation_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        type TEXT NOT NULL,
                        description TEXT NOT NULL,
                        affectedTaskIdsJson TEXT NOT NULL DEFAULT '[]',
                        undoPayloadJson TEXT NOT NULL DEFAULT '{}',
                        undone INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_automation_log_undone ON automation_log(undone)")
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN parentTaskId INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN startAt INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN reminderAt INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN status TEXT NOT NULL DEFAULT 'INBOX'")
                db.execSQL("ALTER TABLE tasks ADD COLUMN completedAt INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN recurrence TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE tasks ADD COLUMN recurrenceInterval INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE tasks ADD COLUMN recurrenceDays TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tasks ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN flagged INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE tasks SET status = CASE WHEN completed = 1 THEN 'COMPLETED' WHEN dueAt IS NULL THEN 'INBOX' ELSE 'PLANNED' END")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_parentTaskId ON tasks(parentTaskId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_status ON tasks(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_archived ON tasks(archived)")

                db.execSQL("ALTER TABLE projects ADD COLUMN icon TEXT NOT NULL DEFAULT 'folder'")
                db.execSQL("ALTER TABLE projects ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'")
                db.execSQL("ALTER TABLE projects ADD COLUMN targetDate INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_projects_archived ON projects(archived)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_projects_status ON projects(status)")

                db.execSQL("ALTER TABLE notes ADD COLUMN blocksData TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE notes ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_archived ON notes(archived)")

                db.execSQL("ALTER TABLE focus_sessions ADD COLUMN actualMinutes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE focus_sessions ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_focus_sessions_startedAt ON focus_sessions(startedAt)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS habits (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        details TEXT NOT NULL,
                        frequency TEXT NOT NULL DEFAULT 'DAILY',
                        activeDays TEXT NOT NULL DEFAULT '',
                        targetPerPeriod INTEGER NOT NULL DEFAULT 1,
                        reminderMinutes INTEGER,
                        colorHex TEXT NOT NULL DEFAULT '#8F9D78',
                        icon TEXT NOT NULL DEFAULT 'spark',
                        archived INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_habits_archived ON habits(archived)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS habit_logs (
                        habitId INTEGER NOT NULL,
                        epochDay INTEGER NOT NULL,
                        count INTEGER NOT NULL DEFAULT 1,
                        completedAt INTEGER NOT NULL,
                        PRIMARY KEY(habitId, epochDay),
                        FOREIGN KEY(habitId) REFERENCES habits(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_habit_logs_habitId ON habit_logs(habitId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_habit_logs_epochDay ON habit_logs(epochDay)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS routines (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL,
                        colorHex TEXT NOT NULL DEFAULT '#A995C3',
                        archived INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_routines_archived ON routines(archived)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS routine_steps (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        routineId INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        durationMinutes INTEGER NOT NULL DEFAULT 5,
                        position INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(routineId) REFERENCES routines(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_routine_steps_routineId ON routine_steps(routineId)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS tags (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        colorHex TEXT NOT NULL DEFAULT '#9A8F7F'
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tags_name ON tags(name)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS task_tag_cross_ref (
                        taskId INTEGER NOT NULL,
                        tagId INTEGER NOT NULL,
                        PRIMARY KEY(taskId, tagId),
                        FOREIGN KEY(taskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(tagId) REFERENCES tags(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_task_tag_cross_ref_taskId ON task_tag_cross_ref(taskId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_task_tag_cross_ref_tagId ON task_tag_cross_ref(tagId)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS attachments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        ownerType TEXT NOT NULL,
                        ownerId INTEGER NOT NULL,
                        uri TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        mimeType TEXT NOT NULL,
                        sizeBytes INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_attachments_ownerType_ownerId ON attachments(ownerType, ownerId)")
            }
        }

        fun getInstance(context: Context): OrdiaDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    OrdiaDatabase::class.java,
                    "ordia.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                    .build()
                    .also { instance = it }
            }
    }
}
