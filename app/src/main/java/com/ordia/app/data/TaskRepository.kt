package com.ordia.app.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class TaskRepository(
    private val taskDao: TaskDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    fun observeAll(): Flow<List<TaskEntity>> = taskDao.observeAll()

    suspend fun get(id: Long): TaskEntity? = withContext(dispatcher) {
        taskDao.get(id)
    }

    suspend fun save(task: TaskEntity): Long = withContext(dispatcher) {
        val now = System.currentTimeMillis()
        val toSave = if (task.id == 0L) {
            task.copy(createdAt = now, updatedAt = now)
        } else {
            task.copy(updatedAt = now)
        }
        taskDao.insert(toSave)
    }

    suspend fun update(task: TaskEntity) = withContext(dispatcher) {
        taskDao.update(task.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(task: TaskEntity) = withContext(dispatcher) {
        taskDao.delete(task)
    }
}
