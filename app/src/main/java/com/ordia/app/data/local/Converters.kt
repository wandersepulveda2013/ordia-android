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

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String, fallback: T): T =
        runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)
}
