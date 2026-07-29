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
        HabitEntity::class,
        HabitLogEntity::class,
        FocusSessionEntity::class,
        RoutineEntity::class,
        RoutineStepEntity::class,
        TagEntity::class,
        TaskTagCrossRef::class,
        AttachmentEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class OrdiaDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun projectDao(): ProjectDao
    abstract fun noteDao(): NoteDao
    abstract fun habitDao(): HabitDao
    abstract fun habitLogDao(): HabitLogDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun routineDao(): RoutineDao
    abstract fun routineStepDao(): RoutineStepDao
    abstract fun tagDao(): TagDao
    abstract fun taskTagDao(): TaskTagDao
    abstract fun attachmentDao(): AttachmentDao

    companion object {
        @Volatile private var instance: OrdiaDatabase? = null

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
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
