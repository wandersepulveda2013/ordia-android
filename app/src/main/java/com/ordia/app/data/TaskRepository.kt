package com.ordia.app.data

import kotlinx.coroutines.flow.Flow

class TaskRepository(private val dao: TaskDao) {
    fun observeAll(): Flow<List<TaskEntity>> = dao.observeAll()

    suspend fun get(id: Long): TaskEntity? = dao.get(id)

    suspend fun save(task: TaskEntity): Long = dao.insert(task)

    suspend fun update(task: TaskEntity) = dao.update(task)

    suspend fun delete(task: TaskEntity) = dao.delete(task)

    suspend fun toggleCompleted(id: Long, isCompleted: Boolean) = dao.toggleCompleted(id, isCompleted)
}
