package com.ordia.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
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
    exportSchema = false
)
abstract class OrdiaDatabase : RoomDatabase() {
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `routines` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `routine_steps` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `routineId` INTEGER NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `attachments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `path` TEXT NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `focus_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `duration` INTEGER NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `habit_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `habitId` INTEGER NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `projects` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `task_tag_cross_ref` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `taskId` INTEGER NOT NULL, `tagId` INTEGER NOT NULL)")
            }
        }
    }
}
