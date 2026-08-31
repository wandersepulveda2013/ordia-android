package com.ordia.app.data.local
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "tasks") data class TaskEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val title: String, val blockedBy: Long? = null)
@Entity(tableName = "projects") data class ProjectEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)
@Entity(tableName = "notes") data class RealNoteEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val title: String, val content: String)
@Entity(tableName = "habits") data class HabitEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)
@Entity(tableName = "habit_logs") data class HabitLogEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val habitId: Long)
@Entity(tableName = "focus_sessions") data class FocusSessionEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val duration: Long)
@Entity(tableName = "routines") data class RoutineEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)
@Entity(tableName = "routine_steps") data class RoutineStepEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val routineId: Long, val name: String)
@Entity(tableName = "tags") data class TagEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)
@Entity(tableName = "task_tag_cross_ref", primaryKeys = ["taskId", "tagId"]) data class TaskTagCrossRef(val taskId: Long, val tagId: Long)
@Entity(tableName = "attachments") data class AttachmentEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val uri: String)
