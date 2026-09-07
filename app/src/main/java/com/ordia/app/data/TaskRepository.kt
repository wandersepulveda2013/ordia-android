package com.ordia.app.data

import kotlinx.coroutines.flow.Flow

class TaskRepository(private val dao: TaskDao) {
    fun observeAll(): Flow<List<TaskEntity>> = dao.observeAll()

    suspend fun get(id: Long): TaskEntity? = dao.getById(id)

    suspend fun save(task: TaskEntity): Long {
        return dao.insert(task)
    }

    suspend fun update(task: TaskEntity) {
        dao.update(task)
    }

    suspend fun delete(task: TaskEntity) {
        dao.delete(task)
    }

    suspend fun toggleCompleted(id: Long, completed: Boolean) {
        dao.setCompleted(id, completed)
    }

    suspend fun create(title: String): Long {
        val now = System.currentTimeMillis()
        return dao.insert(TaskEntity(title = title, createdAt = now, updatedAt = now))
    }
}
