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

enum class CaptureSource { COMPOSER, SHARE, PROCESS_TEXT, VOICE, CLIPBOARD, ATTACHMENT, KEYBOARD, WIDGET }

enum class CaptureTarget { AUTO, INBOX, TASK, NOTE, REMINDER, EVENT }

enum class CaptureStatus { PENDING, PROCESSED, FAILED }

enum class ConversationSourceType { SHARED, IMPORTED, NOTIFICATION }

enum class CommitmentKind {
    SELF_COMMITMENT, OTHER_COMMITMENT, REQUEST, MEETING, PURCHASE, REMINDER, INFORMATION
}

enum class CommitmentOwner { SELF, OTHER, UNKNOWN }

enum class CommitmentReviewStatus { PENDING, CONVERTED, DISMISSED }

enum class ConsentEventType {
    OBSERVATION_ENABLED,
    OBSERVATION_DISABLED,
    SOURCE_ENABLED,
    SOURCE_DISABLED,
    PAUSED,
    RESUMED,
    DATA_CLEARED,
    PERMISSION_REVIEWED,
    SYSTEM_PERMISSION_GRANTED,
    SYSTEM_PERMISSION_REVOKED,
    INTERNAL_ACCESS_REVOKED
}

enum class AutomationTrigger { MANUAL, APP_OPEN, DAILY_MORNING, DAILY_EVENING }

enum class AutomationCondition { ALWAYS, HAS_INBOX_TASKS, HAS_OVERDUE_TASKS, HAS_QUICK_TASKS, HAS_PENDING_COMMITMENTS }

enum class AutomationAction { PLAN_DAY, RESCHEDULE_OVERDUE, BATCH_QUICK_TASKS, REVIEW_COMMITMENTS }

enum class AutomationRuleResult { NEVER, SUCCESS, SKIPPED, FAILED, TESTED }

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
        Index("archived")
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
    val updatedAt: Long = System.currentTimeMillis()
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
    indices = [Index("projectId"), Index("pinned"), Index("archived"), Index(value = ["pinned", "updatedAt"])]
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

/**
 * Historial de automatizaciones del asistente (plan del día, replanificación,
 * "qué hago ahora", rutinas). Guarda el estado previo de las tareas afectadas
 * en JSON para poder deshacer los cambios.
 */
@Entity(
    tableName = "automation_rules",
    indices = [
        Index("enabled"),
        Index("trigger"),
        Index(value = ["definitionHash"], unique = true)
    ]
)
data class AutomationRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val instruction: String,
    val trigger: AutomationTrigger,
    val condition: AutomationCondition,
    val action: AutomationAction,
    val explanation: String,
    @ColumnInfo(defaultValue = "0") val enabled: Boolean = false,
    @ColumnInfo(defaultValue = "60") val frequencyMinutes: Int = 60,
    @ColumnInfo(defaultValue = "3") val maxRunsPerDay: Int = 3,
    val lastRunAt: Long? = null,
    @ColumnInfo(defaultValue = "'NEVER'") val lastResult: AutomationRuleResult = AutomationRuleResult.NEVER,
    @ColumnInfo(defaultValue = "''") val lastError: String = "",
    val definitionHash: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "automation_log", indices = [Index(value = ["type", "createdAt"])])
data class AutomationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Tipo de automatización: "day_plan", "reschedule", "what_now", "routine". */
    val type: String,
    val description: String = "",
    /** Lista JSON de IDs de tareas afectadas. */
    @ColumnInfo(defaultValue = "[]") val affectedTaskIdsJson: String = "[]",
    /** Mapa JSON {taskId: snapshot previo} para deshacer. */
    @ColumnInfo(defaultValue = "{}") val undoPayloadJson: String = "{}",
    @ColumnInfo(defaultValue = "0") val undone: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Historial duradero de la captura universal. El contenido se inserta antes
 * de intentar transformarlo; por eso ni un fallo del analizador ni un cierre
 * durante la conversión puede hacer desaparecer la entrada original.
 */
@Entity(
    tableName = "captures",
    indices = [Index("createdAt"), Index("status"), Index("fingerprint"), Index("resultId")]
)
data class CaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val source: CaptureSource = CaptureSource.COMPOSER,
    val requestedTarget: CaptureTarget = CaptureTarget.AUTO,
    val resolvedTarget: CaptureTarget = CaptureTarget.INBOX,
    val status: CaptureStatus = CaptureStatus.PENDING,
    @ColumnInfo(defaultValue = "''") val attachmentUri: String = "",
    @ColumnInfo(defaultValue = "''") val mimeType: String = "",
    @ColumnInfo(defaultValue = "''") val fingerprint: String = "",
    @ColumnInfo(defaultValue = "''") val resultType: String = "",
    val resultId: Long? = null,
    @ColumnInfo(defaultValue = "''") val errorCode: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** Un único borrador principal recuperable, extensible a más slots en el futuro. */
@Entity(tableName = "capture_drafts")
data class CaptureDraftEntity(
    @PrimaryKey val slot: String = PRIMARY_SLOT,
    val content: String = "",
    val target: CaptureTarget = CaptureTarget.AUTO,
    @ColumnInfo(defaultValue = "''") val attachmentUri: String = "",
    @ColumnInfo(defaultValue = "''") val mimeType: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object { const val PRIMARY_SLOT = "main" }
}

/**
 * Conversación compartida o importada con retención explícita. En modo
 * resumen solamente [rawContent] queda vacío: se guardan el resumen y los
 * compromisos seleccionados, nunca el chat completo por defecto.
 */
@Entity(
    tableName = "conversations",
    indices = [
        Index(value = ["contentHash"], unique = true),
        Index("sourceType"),
        Index("createdAt")
    ]
)
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceType: ConversationSourceType,
    @ColumnInfo(defaultValue = "''") val sourcePackage: String = "",
    val title: String,
    @ColumnInfo(defaultValue = "''") val participants: String = "",
    val summary: String,
    @ColumnInfo(defaultValue = "''") val rawContent: String = "",
    @ColumnInfo(defaultValue = "0") val retainsOriginal: Boolean = false,
    val contentHash: String,
    @ColumnInfo(defaultValue = "0") val messageCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "commitments",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["resultTaskId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("conversationId"),
        Index("reviewStatus"),
        Index("dueAt"),
        Index("resultTaskId"),
        Index(value = ["fingerprint"], unique = true)
    ]
)
data class CommitmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val kind: CommitmentKind,
    val owner: CommitmentOwner = CommitmentOwner.UNKNOWN,
    @ColumnInfo(defaultValue = "''") val actor: String = "",
    val action: String,
    @ColumnInfo(defaultValue = "''") val location: String = "",
    val dueAt: Long? = null,
    val confidence: Float,
    val suggestedReminderAt: Long? = null,
    val reviewStatus: CommitmentReviewStatus = CommitmentReviewStatus.PENDING,
    val fingerprint: String,
    val resultTaskId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** Fuente de notificaciones autorizada explícitamente por paquete. */
@Entity(
    tableName = "observed_sources",
    indices = [Index("enabled"), Index("updatedAt")]
)
data class ObservedSourceEntity(
    @PrimaryKey val packageName: String,
    val displayName: String,
    @ColumnInfo(defaultValue = "0") val enabled: Boolean = false,
    @ColumnInfo(defaultValue = "1") val onlyCommitments: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** Auditoría de consentimiento sin texto de mensajes ni datos derivados. */
@Entity(
    tableName = "consent_events",
    indices = [Index("occurredAt"), Index("sourcePackage")]
)
data class ConsentEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventType: ConsentEventType,
    @ColumnInfo(defaultValue = "''") val sourcePackage: String = "",
    val occurredAt: Long = System.currentTimeMillis()
)
