package com.ordia.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("""
        SELECT * FROM tasks
        WHERE archived = 0
        ORDER BY completed ASC,
        CASE priority WHEN 'URGENT' THEN 0 WHEN 'HIGH' THEN 1 WHEN 'NORMAL' THEN 2 ELSE 3 END,
        dueAt IS NULL, dueAt ASC, sortOrder ASC, createdAt DESC
    """)
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE archived = 1 ORDER BY updatedAt DESC")
    fun observeArchived(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY updatedAt DESC")
    suspend fun getAllNow(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE parentTaskId = :parentId AND archived = 0 ORDER BY sortOrder, createdAt")
    fun observeSubtasks(parentId: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE parentTaskId = :parentId AND archived = 0 ORDER BY sortOrder, createdAt")
    suspend fun getSubtasks(parentId: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE projectId = :projectId AND archived = 0 ORDER BY completed, dueAt IS NULL, dueAt")
    fun observeByProject(projectId: Long): Flow<List<TaskEntity>>

    // Sin búsqueda SQL: LIKE de SQLite pliega caso SOLO en ASCII (residuo familia
    // c.1096). La búsqueda real es SearchEngine.foldForSearch() en memoria
    // (pliega caso+tildes); cualquier reuso de esta vía reintroduciría el gap.

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>): List<Long>

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("UPDATE tasks SET archived = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun archive(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE tasks SET archived = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restore(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT id FROM tasks WHERE parentTaskId = :parentId")
    suspend fun getChildIds(parentId: Long): List<Long>

    @Query("DELETE FROM tasks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE tasks SET archived = 1, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun archiveByIds(ids: List<Long>, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE tasks SET archived = 0, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun restoreByIds(ids: List<Long>, updatedAt: Long = System.currentTimeMillis())

    /**
     * Borra la tarea y todo su subárbol en una única transacción (ORD-025).
     * Sin esto, `parentTaskId` quedaba apuntando a una fila inexistente (huérfanas).
     */
    @Transaction
    suspend fun deleteSubtreeAndSelf(id: Long) {
        deleteByIds(TaskTree.collectIds(id) { getChildIds(it) })
    }

    /**
     * Archiva la tarea y todo su subárbol (c.225). Sin esto, archivar (borrar
     * suavemente) un padre dejaba las subtareas activas: invisibles en la lista
     * (no son raíces y su padre no se renderiza) pero con su recordatorio aún
     * armado → avisos de una tarea que el usuario creyó eliminada. Coherente con
     * [deleteSubtreeAndSelf] (el borrado permanente ya mueve el subárbol entero).
     */
    @Transaction
    suspend fun archiveSubtree(id: Long) {
        archiveByIds(TaskTree.collectIds(id) { getChildIds(it) })
    }

    /**
     * Restaura la tarea y todo su subárbol (c.225), espejo de [archiveSubtree]:
     * restaura las subtareas archivadas junto al padre para que el desglose
     * reaparezca completo y sus recordatorios se rearmen.
     */
    @Transaction
    suspend fun restoreSubtree(id: Long) {
        restoreByIds(TaskTree.collectIds(id) { getChildIds(it) })
    }

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE archived = 0 ORDER BY CASE status WHEN 'ACTIVE' THEN 0 WHEN 'PAUSED' THEN 1 ELSE 2 END, updatedAt DESC")
    fun observeActive(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE archived = 1 ORDER BY updatedAt DESC")
    fun observeArchived(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    suspend fun getAllNow(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: ProjectEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(projects: List<ProjectEntity>): List<Long>

    @Update
    suspend fun update(project: ProjectEntity)

    @Delete
    suspend fun delete(project: ProjectEntity)

    @Query("UPDATE projects SET archived = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun archive(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE projects SET archived = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restore(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM projects")
    suspend fun deleteAll()
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE archived = 0 ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE archived = 1 ORDER BY updatedAt DESC")
    fun observeArchived(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    suspend fun getAllNow(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE projectId = :projectId AND archived = 0 ORDER BY pinned DESC, updatedAt DESC")
    fun observeByProject(projectId: Long): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<NoteEntity>): List<Long>

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("UPDATE notes SET archived = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun archive(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET archived = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restore(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM notes")
    suspend fun deleteAll()
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY updatedAt DESC")
    fun observeActive(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE archived = 1 ORDER BY updatedAt DESC")
    fun observeArchived(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits ORDER BY updatedAt DESC")
    suspend fun getAllNow(): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): HabitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(habit: HabitEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(habits: List<HabitEntity>): List<Long>

    @Update
    suspend fun update(habit: HabitEntity)

    @Delete
    suspend fun delete(habit: HabitEntity)

    @Query("UPDATE habits SET archived = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun archive(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE habits SET archived = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restore(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM habits")
    suspend fun deleteAll()
}

@Dao
interface HabitLogDao {
    @Query("SELECT * FROM habit_logs WHERE epochDay BETWEEN :startEpochDay AND :endEpochDay ORDER BY epochDay DESC")
    fun observeRange(startEpochDay: Long, endEpochDay: Long): Flow<List<HabitLogEntity>>

    @Query("SELECT * FROM habit_logs ORDER BY epochDay DESC")
    suspend fun getAllNow(): List<HabitLogEntity>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId ORDER BY epochDay DESC")
    suspend fun getForHabit(habitId: Long): List<HabitLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: HabitLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<HabitLogEntity>)

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId AND epochDay = :epochDay")
    suspend fun delete(habitId: Long, epochDay: Long)

    @Query("DELETE FROM habit_logs")
    suspend fun deleteAll()
}

@Dao
interface FocusSessionDao {
    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC")
    suspend fun getAllNow(): List<FocusSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: FocusSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<FocusSessionEntity>): List<Long>

    @Update
    suspend fun update(session: FocusSessionEntity)

    @Query("DELETE FROM focus_sessions")
    suspend fun deleteAll()
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines WHERE archived = 0 ORDER BY updatedAt DESC")
    fun observeActive(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines WHERE archived = 1 ORDER BY updatedAt DESC")
    fun observeArchived(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines ORDER BY updatedAt DESC")
    suspend fun getAllNow(): List<RoutineEntity>

    @Query("SELECT * FROM routines WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): RoutineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(routine: RoutineEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(routines: List<RoutineEntity>): List<Long>

    @Update
    suspend fun update(routine: RoutineEntity)

    @Delete
    suspend fun delete(routine: RoutineEntity)

    @Query("UPDATE routines SET archived = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun archive(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE routines SET archived = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restore(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM routines")
    suspend fun deleteAll()
}

@Dao
interface RoutineStepDao {
    @Query("SELECT * FROM routine_steps ORDER BY routineId, position, id")
    fun observeAll(): Flow<List<RoutineStepEntity>>

    @Query("SELECT * FROM routine_steps ORDER BY routineId, position, id")
    suspend fun getAllNow(): List<RoutineStepEntity>

    @Query("SELECT * FROM routine_steps WHERE routineId = :routineId ORDER BY position, id")
    suspend fun getByRoutine(routineId: Long): List<RoutineStepEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(step: RoutineStepEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(steps: List<RoutineStepEntity>): List<Long>

    @Update
    suspend fun update(step: RoutineStepEntity)

    @Delete
    suspend fun delete(step: RoutineStepEntity)

    @Query("DELETE FROM routine_steps WHERE routineId = :routineId")
    suspend fun deleteByRoutine(routineId: Long)

    @Query("DELETE FROM routine_steps")
    suspend fun deleteAll()

    /**
     * Reemplaza atómicamente todos los pasos de una rutina. Sin transacción, un proceso
     * que muera entre el borrado y las inserciones dejaría la rutina con pasos parciales
     * o sin pasos, perdiendo el trabajo del usuario.
     */
    @Transaction
    suspend fun replaceSteps(routineId: Long, steps: List<RoutineStepEntity>) {
        deleteByRoutine(routineId)
        steps.forEach { insert(it) }
    }
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY name")
    suspend fun getAllNow(): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tags: List<TagEntity>): List<Long>

    @Delete
    suspend fun delete(tag: TagEntity)

    @Query("DELETE FROM tags")
    suspend fun deleteAll()
}

@Dao
interface TaskTagDao {
    @Query("SELECT * FROM task_tag_cross_ref")
    fun observeAll(): Flow<List<TaskTagCrossRef>>

    @Query("SELECT * FROM task_tag_cross_ref")
    suspend fun getAllNow(): List<TaskTagCrossRef>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(ref: TaskTagCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(refs: List<TaskTagCrossRef>)

    @Query("DELETE FROM task_tag_cross_ref WHERE taskId = :taskId AND tagId = :tagId")
    suspend fun remove(taskId: Long, tagId: Long)

    @Query("DELETE FROM task_tag_cross_ref")
    suspend fun deleteAll()
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE ownerType = :ownerType AND ownerId = :ownerId ORDER BY createdAt DESC")
    fun observeForOwner(ownerType: AttachmentOwnerType, ownerId: Long): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments ORDER BY createdAt DESC")
    suspend fun getAllNow(): List<AttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attachment: AttachmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(attachments: List<AttachmentEntity>): List<Long>

    @Delete
    suspend fun delete(attachment: AttachmentEntity)

    @Query("DELETE FROM attachments")
    suspend fun deleteAll()
}

@Dao
interface AutomationLogDao {
    @Query("SELECT * FROM automation_log ORDER BY id DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<AutomationLogEntity>>

    @Query("SELECT * FROM automation_log WHERE undone = 0 ORDER BY id DESC LIMIT 1")
    suspend fun latestNotUndone(): AutomationLogEntity?

    @Query("SELECT * FROM automation_log WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): AutomationLogEntity?

    @Query("SELECT * FROM automation_log ORDER BY id")
    suspend fun getAllNow(): List<AutomationLogEntity>

    @Query("SELECT COUNT(*) FROM automation_log WHERE type = :type AND createdAt >= :since AND undone = 0")
    suspend fun countSince(type: String, since: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: AutomationLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<AutomationLogEntity>)

    @Query("UPDATE automation_log SET undone = 1 WHERE id = :id")
    suspend fun markUndone(id: Long)

    @Query("DELETE FROM automation_log")
    suspend fun deleteAll()
}

@Dao
interface AutomationRuleDao {
    @Query("SELECT * FROM automation_rules ORDER BY enabled DESC, updatedAt DESC")
    fun observeAll(): Flow<List<AutomationRuleEntity>>

    @Query("SELECT * FROM automation_rules ORDER BY id")
    suspend fun getAllNow(): List<AutomationRuleEntity>

    @Query("SELECT * FROM automation_rules WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): AutomationRuleEntity?

    @Query("SELECT * FROM automation_rules WHERE trigger = :trigger AND enabled = 1 ORDER BY id")
    suspend fun enabledFor(trigger: AutomationTrigger): List<AutomationRuleEntity>

    @Query("SELECT * FROM automation_rules WHERE definitionHash = :hash LIMIT 1")
    suspend fun findByDefinition(hash: String): AutomationRuleEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(rule: AutomationRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<AutomationRuleEntity>)

    @Update
    suspend fun update(rule: AutomationRuleEntity)

    @Query("DELETE FROM automation_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM automation_rules")
    suspend fun deleteAll()
}

@Dao
interface CaptureDao {
    @Query("SELECT * FROM captures ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<CaptureEntity>>

    @Query("SELECT * FROM captures ORDER BY createdAt DESC")
    suspend fun getAllNow(): List<CaptureEntity>

    @Query("SELECT * FROM capture_drafts ORDER BY slot")
    suspend fun getDraftsNow(): List<CaptureDraftEntity>

    @Query("SELECT * FROM capture_drafts WHERE slot = :slot LIMIT 1")
    fun observeDraft(slot: String = CaptureDraftEntity.PRIMARY_SLOT): Flow<CaptureDraftEntity?>

    @Query("SELECT * FROM captures WHERE fingerprint = :fingerprint AND createdAt >= :since ORDER BY createdAt DESC LIMIT 1")
    suspend fun findRecentDuplicate(fingerprint: String, since: Long): CaptureEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(capture: CaptureEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(captures: List<CaptureEntity>): List<Long>

    @Update
    suspend fun update(capture: CaptureEntity)

    @Query("DELETE FROM captures WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDraft(draft: CaptureDraftEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDrafts(drafts: List<CaptureDraftEntity>)

    @Query("DELETE FROM capture_drafts WHERE slot = :slot")
    suspend fun deleteDraft(slot: String = CaptureDraftEntity.PRIMARY_SLOT)

    @Query("DELETE FROM captures")
    suspend fun deleteAll()

    @Query("DELETE FROM capture_drafts")
    suspend fun deleteAllDrafts()
}

@Dao
abstract class ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY createdAt DESC")
    abstract fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM commitments ORDER BY CASE reviewStatus WHEN 'PENDING' THEN 0 WHEN 'CONVERTED' THEN 1 ELSE 2 END, createdAt DESC")
    abstract fun observeCommitments(): Flow<List<CommitmentEntity>>

    @Query("SELECT * FROM commitments WHERE reviewStatus = 'PENDING' ORDER BY dueAt IS NULL, dueAt ASC, createdAt DESC")
    abstract fun observePendingCommitments(): Flow<List<CommitmentEntity>>

    @Query("SELECT * FROM conversations ORDER BY createdAt DESC")
    abstract suspend fun getConversationsNow(): List<ConversationEntity>

    @Query("SELECT * FROM commitments ORDER BY createdAt DESC")
    abstract suspend fun getCommitmentsNow(): List<CommitmentEntity>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    abstract suspend fun getConversation(id: Long): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE contentHash = :contentHash LIMIT 1")
    abstract suspend fun findConversationByHash(contentHash: String): ConversationEntity?

    @Query("SELECT * FROM commitments WHERE id = :id LIMIT 1")
    abstract suspend fun getCommitment(id: Long): CommitmentEntity?

    @Query("SELECT * FROM commitments WHERE conversationId = :conversationId ORDER BY id ASC")
    abstract suspend fun getCommitmentsByConversation(conversationId: Long): List<CommitmentEntity>

    @Query("SELECT COUNT(*) FROM conversations WHERE sourceType = :sourceType AND createdAt >= :since")
    abstract suspend fun countConversationsSince(sourceType: ConversationSourceType, since: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertConversation(conversation: ConversationEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertCommitments(commitments: List<CommitmentEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertConversations(conversations: List<ConversationEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun restoreCommitments(commitments: List<CommitmentEntity>): List<Long>

    @Update
    abstract suspend fun updateCommitment(commitment: CommitmentEntity)

    @Transaction
    open suspend fun insertGraph(
        conversation: ConversationEntity,
        commitments: List<CommitmentEntity>
    ): Long {
        val insertedId = insertConversation(conversation)
        if (insertedId <= 0L) {
            val existingId = findConversationByHash(conversation.contentHash)?.id ?: return 0L
            return -existingId
        }
        if (commitments.isNotEmpty()) {
            insertCommitments(commitments.map { it.copy(conversationId = insertedId) })
        }
        return insertedId
    }

    @Query("DELETE FROM conversations WHERE id = :id")
    abstract suspend fun deleteConversation(id: Long)

    @Query("DELETE FROM conversations WHERE sourceType = :sourceType")
    abstract suspend fun deleteConversationsBySource(sourceType: ConversationSourceType)

    @Query("DELETE FROM commitments")
    abstract suspend fun deleteAllCommitments()

    @Query("DELETE FROM conversations")
    abstract suspend fun deleteAllConversations()
}

@Dao
abstract class ObservationDao {
    @Query("SELECT * FROM observed_sources ORDER BY enabled DESC, displayName COLLATE NOCASE")
    abstract fun observeSources(): Flow<List<ObservedSourceEntity>>

    @Query("SELECT * FROM consent_events ORDER BY occurredAt DESC, id DESC LIMIT 100")
    abstract fun observeConsentHistory(): Flow<List<ConsentEventEntity>>

    @Query("SELECT * FROM observed_sources WHERE packageName = :packageName LIMIT 1")
    abstract suspend fun getSource(packageName: String): ObservedSourceEntity?

    @Query("SELECT * FROM observed_sources ORDER BY displayName COLLATE NOCASE")
    abstract suspend fun getSourcesNow(): List<ObservedSourceEntity>

    @Query("SELECT * FROM consent_events ORDER BY occurredAt DESC, id DESC")
    abstract suspend fun getConsentEventsNow(): List<ConsentEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertSource(source: ObservedSourceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun restoreSources(sources: List<ObservedSourceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun restoreConsentEvents(events: List<ConsentEventEntity>)

    @Insert
    protected abstract suspend fun insertConsentEvent(event: ConsentEventEntity): Long

    @Query("UPDATE observed_sources SET enabled = 0, updatedAt = :now WHERE enabled = 1")
    abstract suspend fun disableAllSources(now: Long)

    @Query("DELETE FROM consent_events WHERE id NOT IN (SELECT id FROM consent_events ORDER BY occurredAt DESC, id DESC LIMIT :keep)")
    protected abstract suspend fun pruneConsentEvents(keep: Int)

    @Transaction
    open suspend fun configureSource(
        packageName: String,
        displayName: String,
        enabled: Boolean,
        onlyCommitments: Boolean,
        now: Long
    ) {
        val existing = getSource(packageName)
        if (existing?.enabled == enabled &&
            existing.onlyCommitments == onlyCommitments &&
            existing.displayName == displayName
        ) return
        upsertSource(
            ObservedSourceEntity(
                packageName = packageName,
                displayName = displayName,
                enabled = enabled,
                onlyCommitments = onlyCommitments,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        )
        insertConsentEvent(
            ConsentEventEntity(
                eventType = if (enabled) ConsentEventType.SOURCE_ENABLED else ConsentEventType.SOURCE_DISABLED,
                sourcePackage = packageName,
                occurredAt = now
            )
        )
        pruneConsentEvents(200)
    }

    @Transaction
    open suspend fun recordConsent(
        eventType: ConsentEventType,
        sourcePackage: String = "",
        now: Long = System.currentTimeMillis()
    ) {
        insertConsentEvent(ConsentEventEntity(eventType = eventType, sourcePackage = sourcePackage, occurredAt = now))
        pruneConsentEvents(200)
    }

    @Query("DELETE FROM consent_events")
    abstract suspend fun deleteAllConsentEvents()

    @Query("DELETE FROM observed_sources")
    abstract suspend fun deleteAllSources()
}
