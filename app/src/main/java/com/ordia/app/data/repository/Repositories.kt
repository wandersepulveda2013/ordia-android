package com.ordia.app.data.repository

import com.ordia.app.data.local.AttachmentDao
import com.ordia.app.data.local.AttachmentEntity
import com.ordia.app.data.local.AttachmentOwnerType
import com.ordia.app.data.local.FocusSessionDao
import com.ordia.app.data.local.FocusSessionEntity
import com.ordia.app.data.local.HabitDao
import com.ordia.app.data.local.HabitEntity
import com.ordia.app.data.local.HabitLogDao
import com.ordia.app.data.local.HabitLogEntity
import com.ordia.app.data.local.NoteDao
import com.ordia.app.data.local.NoteEntity
import com.ordia.app.data.local.ProjectDao
import com.ordia.app.data.local.ProjectEntity
import com.ordia.app.data.local.RoutineDao
import com.ordia.app.data.local.RoutineEntity
import com.ordia.app.data.local.RoutineStepDao
import com.ordia.app.data.local.RoutineStepEntity
import com.ordia.app.data.local.TagDao
import com.ordia.app.data.local.TagEntity
import com.ordia.app.data.local.TaskDao
import com.ordia.app.data.local.TaskEntity
import com.ordia.app.data.local.TaskTagCrossRef
import com.ordia.app.data.local.TaskTagDao
import com.ordia.app.data.local.CaptureDao
import com.ordia.app.data.local.CaptureDraftEntity
import com.ordia.app.data.local.CaptureEntity
import com.ordia.app.data.local.CommitmentEntity
import com.ordia.app.data.local.ConversationDao
import com.ordia.app.data.local.ConversationEntity
import com.ordia.app.data.local.ConversationSourceType
import com.ordia.app.data.local.ConsentEventEntity
import com.ordia.app.data.local.ConsentEventType
import com.ordia.app.data.local.ObservationDao
import com.ordia.app.data.local.ObservedSourceEntity
import com.ordia.app.data.local.AutomationRuleDao
import com.ordia.app.data.local.AutomationRuleEntity
import com.ordia.app.data.local.AutomationTrigger
import kotlinx.coroutines.flow.Flow

class TaskRepository(private val dao: TaskDao) {
    val tasks: Flow<List<TaskEntity>> = dao.observeAll()
    val archived: Flow<List<TaskEntity>> = dao.observeArchived()
    suspend fun get(id: Long): TaskEntity? = dao.getById(id)
    suspend fun subtasks(parentId: Long): List<TaskEntity> = dao.getSubtasks(parentId)
    suspend fun add(task: TaskEntity): Long = dao.insert(task)
    suspend fun addAll(tasks: List<TaskEntity>): List<Long> = dao.insertAll(tasks)
    suspend fun update(task: TaskEntity) = dao.update(task)
    suspend fun delete(task: TaskEntity) = dao.delete(task)
    suspend fun archive(id: Long) = dao.archive(id)
    suspend fun restore(id: Long) = dao.restore(id)
    suspend fun deletePermanently(id: Long) = dao.deleteSubtreeAndSelf(id)
    suspend fun search(query: String): List<TaskEntity> = dao.search(query)
    suspend fun getAllNow(): List<TaskEntity> = dao.getAllNow()
}

class ProjectRepository(private val dao: ProjectDao) {
    val projects: Flow<List<ProjectEntity>> = dao.observeActive()
    val archived: Flow<List<ProjectEntity>> = dao.observeArchived()
    suspend fun get(id: Long): ProjectEntity? = dao.getById(id)
    suspend fun add(project: ProjectEntity): Long = dao.insert(project)
    suspend fun update(project: ProjectEntity) = dao.update(project)
    suspend fun delete(project: ProjectEntity) = dao.delete(project)
    suspend fun archive(id: Long) = dao.archive(id)
    suspend fun restore(id: Long) = dao.restore(id)
    suspend fun deletePermanently(id: Long) = dao.deleteById(id)
    suspend fun search(query: String): List<ProjectEntity> = dao.search(query)
}

class NoteRepository(private val dao: NoteDao) {
    val notes: Flow<List<NoteEntity>> = dao.observeAll()
    val archived: Flow<List<NoteEntity>> = dao.observeArchived()
    suspend fun get(id: Long): NoteEntity? = dao.getById(id)
    suspend fun add(note: NoteEntity): Long = dao.insert(note)
    suspend fun update(note: NoteEntity) = dao.update(note)
    suspend fun delete(note: NoteEntity) = dao.delete(note)
    suspend fun archive(id: Long) = dao.archive(id)
    suspend fun restore(id: Long) = dao.restore(id)
    suspend fun deletePermanently(id: Long) = dao.deleteById(id)
    suspend fun search(query: String): List<NoteEntity> = dao.search(query)
}

class HabitRepository(
    private val habitDao: HabitDao,
    private val logDao: HabitLogDao
) {
    val habits: Flow<List<HabitEntity>> = habitDao.observeActive()
    val archived: Flow<List<HabitEntity>> = habitDao.observeArchived()
    fun logs(startEpochDay: Long, endEpochDay: Long): Flow<List<HabitLogEntity>> =
        logDao.observeRange(startEpochDay, endEpochDay)

    suspend fun get(id: Long): HabitEntity? = habitDao.getById(id)
    suspend fun add(habit: HabitEntity): Long = habitDao.insert(habit)
    suspend fun update(habit: HabitEntity) = habitDao.update(habit)
    suspend fun delete(habit: HabitEntity) = habitDao.delete(habit)
    suspend fun archive(id: Long) = habitDao.archive(id)
    suspend fun restore(id: Long) = habitDao.restore(id)
    suspend fun deletePermanently(id: Long) = habitDao.deleteById(id)
    suspend fun log(entry: HabitLogEntity) = logDao.upsert(entry)
    suspend fun removeLog(habitId: Long, epochDay: Long) = logDao.delete(habitId, epochDay)
    suspend fun history(habitId: Long): List<HabitLogEntity> = logDao.getForHabit(habitId)
}

class FocusRepository(private val dao: FocusSessionDao) {
    val recent: Flow<List<FocusSessionEntity>> = dao.observeRecent()
    suspend fun add(session: FocusSessionEntity): Long = dao.insert(session)
    suspend fun update(session: FocusSessionEntity) = dao.update(session)
}

class RoutineRepository(
    private val routineDao: RoutineDao,
    private val stepDao: RoutineStepDao
) {
    val routines: Flow<List<RoutineEntity>> = routineDao.observeActive()
    val archived: Flow<List<RoutineEntity>> = routineDao.observeArchived()
    val steps: Flow<List<RoutineStepEntity>> = stepDao.observeAll()
    suspend fun add(routine: RoutineEntity): Long = routineDao.insert(routine)
    suspend fun update(routine: RoutineEntity) = routineDao.update(routine)
    suspend fun delete(routine: RoutineEntity) = routineDao.delete(routine)
    suspend fun archive(id: Long) = routineDao.archive(id)
    suspend fun restore(id: Long) = routineDao.restore(id)
    suspend fun deletePermanently(id: Long) = routineDao.deleteById(id)
    suspend fun addStep(step: RoutineStepEntity): Long = stepDao.insert(step)
    suspend fun updateStep(step: RoutineStepEntity) = stepDao.update(step)
    suspend fun deleteStep(step: RoutineStepEntity) = stepDao.delete(step)
    suspend fun stepsFor(routineId: Long): List<RoutineStepEntity> = stepDao.getByRoutine(routineId)
}

class TagRepository(
    private val tagDao: TagDao,
    private val taskTagDao: TaskTagDao
) {
    val tags: Flow<List<TagEntity>> = tagDao.observeAll()
    val links: Flow<List<TaskTagCrossRef>> = taskTagDao.observeAll()
    suspend fun add(tag: TagEntity): Long = tagDao.insert(tag)
    suspend fun delete(tag: TagEntity) = tagDao.delete(tag)
    suspend fun link(taskId: Long, tagId: Long) = taskTagDao.add(TaskTagCrossRef(taskId, tagId))
    suspend fun unlink(taskId: Long, tagId: Long) = taskTagDao.remove(taskId, tagId)
}

class AttachmentRepository(private val dao: AttachmentDao) {
    val all: Flow<List<AttachmentEntity>> = dao.observeAll()
    fun forOwner(type: AttachmentOwnerType, ownerId: Long): Flow<List<AttachmentEntity>> =
        dao.observeForOwner(type, ownerId)
    suspend fun add(attachment: AttachmentEntity): Long = dao.insert(attachment)
    suspend fun delete(attachment: AttachmentEntity) = dao.delete(attachment)
}

class CaptureRepository(private val dao: CaptureDao) {
    val recent: Flow<List<CaptureEntity>> = dao.observeRecent()
    val draft: Flow<CaptureDraftEntity?> = dao.observeDraft()

    suspend fun insert(capture: CaptureEntity): Long = dao.insert(capture)
    suspend fun update(capture: CaptureEntity) = dao.update(capture)
    suspend fun findRecentDuplicate(fingerprint: String, since: Long): CaptureEntity? =
        dao.findRecentDuplicate(fingerprint, since)
    suspend fun saveDraft(draft: CaptureDraftEntity) = dao.upsertDraft(draft)
    suspend fun clearDraft() = dao.deleteDraft()
}

class ConversationRepository(private val dao: ConversationDao) {
    val conversations: Flow<List<ConversationEntity>> = dao.observeConversations()
    val commitments: Flow<List<CommitmentEntity>> = dao.observeCommitments()
    val pendingCommitments: Flow<List<CommitmentEntity>> = dao.observePendingCommitments()

    suspend fun saveGraph(conversation: ConversationEntity, commitments: List<CommitmentEntity>): Pair<Long, Boolean> {
        val result = dao.insertGraph(conversation, commitments)
        return when {
            result > 0L -> result to true
            result < 0L -> -result to false
            else -> 0L to false
        }
    }

    suspend fun getConversation(id: Long): ConversationEntity? = dao.getConversation(id)
    suspend fun findByHash(contentHash: String): ConversationEntity? = dao.findConversationByHash(contentHash)
    suspend fun countSince(sourceType: ConversationSourceType, since: Long): Int =
        dao.countConversationsSince(sourceType, since)
    suspend fun getCommitment(id: Long): CommitmentEntity? = dao.getCommitment(id)
    suspend fun getCommitmentsNow(): List<CommitmentEntity> = dao.getCommitmentsNow()
    suspend fun updateCommitment(commitment: CommitmentEntity) = dao.updateCommitment(commitment)
    suspend fun deleteConversation(id: Long) = dao.deleteConversation(id)
    suspend fun clearBySource(sourceType: ConversationSourceType) = dao.deleteConversationsBySource(sourceType)
    suspend fun clearAll() {
        dao.deleteAllCommitments()
        dao.deleteAllConversations()
    }
}

class AutomationRuleRepository(
    private val ruleDao: AutomationRuleDao,
    private val logDao: com.ordia.app.data.local.AutomationLogDao
) {
    val rules: Flow<List<AutomationRuleEntity>> = ruleDao.observeAll()
    val history: Flow<List<com.ordia.app.data.local.AutomationLogEntity>> = logDao.observeRecent(100)

    suspend fun save(rule: AutomationRuleEntity): Pair<Long, Boolean> {
        val existing = ruleDao.findByDefinition(rule.definitionHash)
        if (existing != null) return existing.id to false
        val id = ruleDao.insert(rule)
        return id to (id > 0L)
    }

    suspend fun get(id: Long): AutomationRuleEntity? = ruleDao.getById(id)
    suspend fun enabledFor(trigger: AutomationTrigger): List<AutomationRuleEntity> = ruleDao.enabledFor(trigger)
    suspend fun allNow(): List<AutomationRuleEntity> = ruleDao.getAllNow()
    suspend fun update(rule: AutomationRuleEntity) = ruleDao.update(rule)
    suspend fun delete(id: Long) = ruleDao.deleteById(id)
    suspend fun countRuns(ruleId: Long, since: Long): Int = logDao.countSince("rule:$ruleId", since)
    suspend fun log(entry: com.ordia.app.data.local.AutomationLogEntity): Long = logDao.insert(entry)
}

class ObservationRepository(private val dao: ObservationDao) {
    val sources: Flow<List<ObservedSourceEntity>> = dao.observeSources()
    val consentHistory: Flow<List<ConsentEventEntity>> = dao.observeConsentHistory()

    suspend fun getSource(packageName: String): ObservedSourceEntity? = dao.getSource(packageName)

    suspend fun configureSource(
        packageName: String,
        displayName: String,
        enabled: Boolean,
        onlyCommitments: Boolean = true
    ) {
        require(PACKAGE_PATTERN.matches(packageName)) { "Paquete de origen inválido." }
        dao.configureSource(
            packageName = packageName,
            displayName = displayName.trim().take(100).ifBlank { packageName },
            enabled = enabled,
            onlyCommitments = onlyCommitments,
            now = System.currentTimeMillis()
        )
    }

    suspend fun recordConsent(type: ConsentEventType, sourcePackage: String = "") {
        require(sourcePackage.isBlank() || PACKAGE_PATTERN.matches(sourcePackage)) { "Paquete de origen inválido." }
        dao.recordConsent(type, sourcePackage)
    }

    suspend fun disableAllSources() = dao.disableAllSources(System.currentTimeMillis())

    private companion object {
        val PACKAGE_PATTERN = Regex("^[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+$")
    }
}
