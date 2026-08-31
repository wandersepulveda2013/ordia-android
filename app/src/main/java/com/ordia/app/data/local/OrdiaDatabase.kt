package com.ordia.app.data.local
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `focus_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `duration` INTEGER NOT NULL)")
    }
}

@Database(
    entities = [
        TaskEntity::class, ProjectEntity::class, RealNoteEntity::class, HabitEntity::class,
        HabitLogEntity::class, FocusSessionEntity::class, RoutineEntity::class,
        RoutineStepEntity::class, TagEntity::class, TaskTagCrossRef::class, AttachmentEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class OrdiaDatabase : RoomDatabase()
