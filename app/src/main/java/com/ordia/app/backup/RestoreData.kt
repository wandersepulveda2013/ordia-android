package com.ordia.app.backup

import com.ordia.app.data.local.AttachmentEntity
import com.ordia.app.data.local.CaptureDraftEntity
import com.ordia.app.data.local.CaptureEntity
import com.ordia.app.data.local.FocusSessionEntity
import com.ordia.app.data.local.HabitEntity
import com.ordia.app.data.local.HabitLogEntity
import com.ordia.app.data.local.NoteEntity
import com.ordia.app.data.local.ProjectEntity
import com.ordia.app.data.local.RoutineEntity
import com.ordia.app.data.local.RoutineStepEntity
import com.ordia.app.data.local.TagEntity
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskTagCrossRef

/**
 * Conjunto completo de datos que una copia de seguridad puede contener.
 *
 * Se usa tanto para el contenido validado de una copia como para la lectura
 * de lo que hay persistido en la base (verificación posterior al restore).
 */
data class RestoreData(
    val projects: List<ProjectEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val habits: List<HabitEntity> = emptyList(),
    val habitLogs: List<HabitLogEntity> = emptyList(),
    val focusSessions: List<FocusSessionEntity> = emptyList(),
    val routines: List<RoutineEntity> = emptyList(),
    val routineSteps: List<RoutineStepEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val taskTags: List<TaskTagCrossRef> = emptyList(),
    val attachments: List<AttachmentEntity> = emptyList(),
    val captures: List<CaptureEntity> = emptyList(),
    val captureDrafts: List<CaptureDraftEntity> = emptyList()
) {
    val totalCount: Int
        get() = projects.size + tasks.size + notes.size + habits.size + habitLogs.size +
            focusSessions.size + routines.size + routineSteps.size + tags.size +
            taskTags.size + attachments.size + captures.size + captureDrafts.size

    /** Compara el número de registros de cada colección con otro estado. */
    fun countsMatch(other: RestoreData): Boolean =
        projects.size == other.projects.size &&
            tasks.size == other.tasks.size &&
            notes.size == other.notes.size &&
            habits.size == other.habits.size &&
            habitLogs.size == other.habitLogs.size &&
            focusSessions.size == other.focusSessions.size &&
            routines.size == other.routines.size &&
            routineSteps.size == other.routineSteps.size &&
            tags.size == other.tags.size &&
            taskTags.size == other.taskTags.size &&
            attachments.size == other.attachments.size &&
            captures.size == other.captures.size &&
            captureDrafts.size == other.captureDrafts.size
}
