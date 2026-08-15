package com.ordia.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** User-facing urgency, independent from the task lifecycle. */
enum class TaskPriority { LOW, NORMAL, HIGH, URGENT }

enum class TaskStatus { INBOX, PLANNED, IN_PROGRESS, COMPLETED, CANCELLED }

enum class RecurrenceFrequency { NONE, DAILY, WEEKLY, MONTHLY, YEARLY }

enum class ProjectStatus { ACTIVE, PAUSED, COMPLETED }

enum class HabitFrequency { DAILY, WEEKLY, MONTHLY }

enum class AttachmentOwnerType { TASK, NOTE, PROJECT }

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("projectId"),
        Index("parentTaskId"),
        Index("dueAt"),
        Index("completed"),
        Index("status"),
        Index("archived"),
        Index("blockedBy")
    ]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val details: String = "",
    val projectId: Long? = null,
    val parentTaskId: Long? = null,
    val startAt: Long? = null,
    val dueAt: Long? = null,
    val reminderAt: Long? = null,
    val durationMinutes: Int = 25,
    val priority: TaskPriority = TaskPriority.NORMAL,
    @ColumnInfo(defaultValue = "'INBOX'") val status: TaskStatus = TaskStatus.INBOX,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    @ColumnInfo(defaultValue = "'NONE'") val recurrence: RecurrenceFrequency = RecurrenceFrequency.NONE,
    @ColumnInfo(defaultValue = "1") val recurrenceInterval: Int = 1,
    @ColumnInfo(defaultValue = "''") val recurrenceDays: String = "",
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
    @ColumnInfo(defaultValue = "0") val flagged: Boolean = false,
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val blockedBy: Long? = null
)

@Entity(tableName = "projects", indices = [Index("archived"), Index("status")])
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val colorHex: String = "#C9A86A",
    @ColumnInfo(defaultValue = "'folder'") val icon: String = "folder",
    @ColumnInfo(defaultValue = "'ACTIVE'") val status: ProjectStatus = ProjectStatus.ACTIVE,
    val targetDate: Long? = null,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("projectId"), Index("pinned"), Index("archived")]
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String = "",
    @ColumnInfo(defaultValue = "''") val blocksData: String = "",
    val projectId: Long? = null,
    val pinned: Boolean = false,
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "habits", indices = [Index("archived")])
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val details: String = "",
    @ColumnInfo(defaultValue = "'DAILY'") val frequency: HabitFrequency = HabitFrequency.DAILY,
    @ColumnInfo(defaultValue = "''") val activeDays: String = "",
    @ColumnInfo(defaultValue = "1") val targetPerPeriod: Int = 1,
    val reminderMinutes: Int? = null,
    @ColumnInfo(defaultValue = "'#8F9D78'") val colorHex: String = "#8F9D78",
    @ColumnInfo(defaultValue = "'spark'") val icon: String = "spark",
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "habit_logs",
    primaryKeys = ["habitId", "epochDay"],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("habitId"), Index("epochDay")]
)
data class HabitLogEntity(
    val habitId: Long,
    val epochDay: Long,
    @ColumnInfo(defaultValue = "1") val count: Int = 1,
    val completedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "focus_sessions", indices = [Index("taskId"), Index("startedAt")])
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long? = null,
    val startedAt: Long,
    val endedAt: Long? = null,
    val plannedMinutes: Int = 25,
    @ColumnInfo(defaultValue = "0") val actualMinutes: Int = 0,
    val completed: Boolean = false,
    @ColumnInfo(defaultValue = "''") val notes: String = ""
)

@Entity(tableName = "routines", indices = [Index("archived")])
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    @ColumnInfo(defaultValue = "'#A995C3'") val colorHex: String = "#A995C3",
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "routine_steps",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("routineId")]
)
data class RoutineStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val title: String,
    @ColumnInfo(defaultValue = "5") val durationMinutes: Int = 5,
    @ColumnInfo(defaultValue = "0") val position: Int = 0
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(defaultValue = "'#9A8F7F'") val colorHex: String = "#9A8F7F"
)

@Entity(
    tableName = "task_tag_cross_ref",
    primaryKeys = ["taskId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("taskId"), Index("tagId")]
)
data class TaskTagCrossRef(val taskId: Long, val tagId: Long)

@Entity(tableName = "attachments", indices = [Index("ownerType", "ownerId")])
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerType: AttachmentOwnerType,
    val ownerId: Long,
    val uri: String,
    val displayName: String,
    val mimeType: String = "application/octet-stream",
    @ColumnInfo(defaultValue = "0") val sizeBytes: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)
