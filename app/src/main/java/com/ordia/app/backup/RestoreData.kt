package com.ordia.app.backup

import com.ordia.app.data.local.AttachmentEntity
import com.ordia.app.data.local.CaptureDraftEntity
import com.ordia.app.data.local.CaptureEntity
import com.ordia.app.data.local.CommitmentEntity
import com.ordia.app.data.local.ConversationEntity
import com.ordia.app.data.local.ConsentEventEntity
import com.ordia.app.data.local.ObservedSourceEntity
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
import com.ordia.app.data.local.AutomationRuleEntity
import com.ordia.app.data.local.AutomationLogEntity

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
    val captureDrafts: List<CaptureDraftEntity> = emptyList(),
    val conversations: List<ConversationEntity> = emptyList(),
    val commitments: List<CommitmentEntity> = emptyList(),
    val observedSources: List<ObservedSourceEntity> = emptyList(),
    val consentEvents: List<ConsentEventEntity> = emptyList(),
    val automationRules: List<AutomationRuleEntity> = emptyList(),
    val automationLogs: List<AutomationLogEntity> = emptyList()
) {
    val totalCount: Int
        get() = projects.size + tasks.size + notes.size + habits.size + habitLogs.size +
            focusSessions.size + routines.size + routineSteps.size + tags.size +
            taskTags.size + attachments.size + captures.size + captureDrafts.size +
            conversations.size + commitments.size + observedSources.size + consentEvents.size +
            automationRules.size + automationLogs.size

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
            captureDrafts.size == other.captureDrafts.size &&
            conversations.size == other.conversations.size &&
            commitments.size == other.commitments.size &&
            observedSources.size == other.observedSources.size &&
            consentEvents.size == other.consentEvents.size &&
            automationRules.size == other.automationRules.size &&
            automationLogs.size == other.automationLogs.size

    /** Compara cada valor persistido ignorando únicamente el orden SQL. */
    fun contentMatches(other: RestoreData): Boolean =
        projects.toSet() == other.projects.toSet() &&
            tasks.toSet() == other.tasks.toSet() &&
            notes.toSet() == other.notes.toSet() &&
            habits.toSet() == other.habits.toSet() &&
            habitLogs.toSet() == other.habitLogs.toSet() &&
            focusSessions.toSet() == other.focusSessions.toSet() &&
            routines.toSet() == other.routines.toSet() &&
            routineSteps.toSet() == other.routineSteps.toSet() &&
            tags.toSet() == other.tags.toSet() &&
            taskTags.toSet() == other.taskTags.toSet() &&
            attachments.toSet() == other.attachments.toSet() &&
            captures.toSet() == other.captures.toSet() &&
            captureDrafts.toSet() == other.captureDrafts.toSet() &&
            conversations.toSet() == other.conversations.toSet() &&
            commitments.toSet() == other.commitments.toSet() &&
            observedSources.toSet() == other.observedSources.toSet() &&
            consentEvents.toSet() == other.consentEvents.toSet() &&
            automationRules.toSet() == other.automationRules.toSet() &&
            automationLogs.toSet() == other.automationLogs.toSet()
}
