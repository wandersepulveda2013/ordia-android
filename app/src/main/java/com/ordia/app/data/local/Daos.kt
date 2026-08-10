package com.ordia.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

    @Query("SELECT * FROM tasks WHERE archived = 0 AND (title LIKE '%' || :query || '%' OR details LIKE '%' || :query || '%') ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun search(query: String, limit: Int = 50): List<TaskEntity>

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

    @Query("SELECT * FROM projects WHERE archived = 0 AND (name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%') ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun search(query: String, limit: Int = 30): List<ProjectEntity>

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

    @Query("SELECT * FROM notes WHERE archived = 0 AND (title LIKE '%' || :query || '%' OR body LIKE '%' || :query || '%') ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun search(query: String, limit: Int = 50): List<NoteEntity>

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

    @Query("DELETE FROM routine_steps")
    suspend fun deleteAll()
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

    @Query("DELETE FROM task_tag_cross_ref WHERE taskId = :taskId AND tagId IN (:tagIds)")
    suspend fun removeList(taskId: Long, tagIds: List<Long>)

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
