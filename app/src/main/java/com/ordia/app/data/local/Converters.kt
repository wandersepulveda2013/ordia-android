package com.ordia.app.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter fun priorityToString(value: TaskPriority): String = value.name
    @TypeConverter fun stringToPriority(value: String): TaskPriority = enumOrDefault(value, TaskPriority.NORMAL)

    @TypeConverter fun taskStatusToString(value: TaskStatus): String = value.name
    @TypeConverter fun stringToTaskStatus(value: String): TaskStatus = enumOrDefault(value, TaskStatus.INBOX)

    @TypeConverter fun recurrenceToString(value: RecurrenceFrequency): String = value.name
    @TypeConverter fun stringToRecurrence(value: String): RecurrenceFrequency = enumOrDefault(value, RecurrenceFrequency.NONE)

    @TypeConverter fun projectStatusToString(value: ProjectStatus): String = value.name
    @TypeConverter fun stringToProjectStatus(value: String): ProjectStatus = enumOrDefault(value, ProjectStatus.ACTIVE)

    @TypeConverter fun habitFrequencyToString(value: HabitFrequency): String = value.name
    @TypeConverter fun stringToHabitFrequency(value: String): HabitFrequency = enumOrDefault(value, HabitFrequency.DAILY)

    @TypeConverter fun attachmentOwnerToString(value: AttachmentOwnerType): String = value.name
    @TypeConverter fun stringToAttachmentOwner(value: String): AttachmentOwnerType = enumOrDefault(value, AttachmentOwnerType.NOTE)

    @TypeConverter fun captureSourceToString(value: CaptureSource): String = value.name
    @TypeConverter fun stringToCaptureSource(value: String): CaptureSource = enumOrDefault(value, CaptureSource.COMPOSER)

    @TypeConverter fun captureTargetToString(value: CaptureTarget): String = value.name
    @TypeConverter fun stringToCaptureTarget(value: String): CaptureTarget = enumOrDefault(value, CaptureTarget.AUTO)

    @TypeConverter fun captureStatusToString(value: CaptureStatus): String = value.name
    @TypeConverter fun stringToCaptureStatus(value: String): CaptureStatus = enumOrDefault(value, CaptureStatus.PENDING)

    @TypeConverter fun conversationSourceToString(value: ConversationSourceType): String = value.name
    @TypeConverter fun stringToConversationSource(value: String): ConversationSourceType = enumOrDefault(value, ConversationSourceType.SHARED)

    @TypeConverter fun commitmentKindToString(value: CommitmentKind): String = value.name
    @TypeConverter fun stringToCommitmentKind(value: String): CommitmentKind = enumOrDefault(value, CommitmentKind.INFORMATION)

    @TypeConverter fun commitmentOwnerToString(value: CommitmentOwner): String = value.name
    @TypeConverter fun stringToCommitmentOwner(value: String): CommitmentOwner = enumOrDefault(value, CommitmentOwner.UNKNOWN)

    @TypeConverter fun commitmentStatusToString(value: CommitmentReviewStatus): String = value.name
    @TypeConverter fun stringToCommitmentStatus(value: String): CommitmentReviewStatus = enumOrDefault(value, CommitmentReviewStatus.PENDING)

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String, fallback: T): T =
        runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)
}
