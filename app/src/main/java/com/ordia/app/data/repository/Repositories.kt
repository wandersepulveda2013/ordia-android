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
    suspend fun deletePermanently(id: Long) = dao.deleteById(id)
    suspend fun search(query: String): List<TaskEntity> = dao.search(query)
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
    suspend fun deleteStepsForRoutine(routineId: Long) = stepDao.deleteByRoutine(routineId)
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
