package com.ordia.app.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.flow.Flow

// A simple fake dao for testing TaskRepository without Mockito
class FakeTaskDao : TaskDao {
    var insertCalled = false
    var deleteCalled = false
    var insertedTask: TaskEntity? = null
    var deletedTask: TaskEntity? = null

    override fun observeAll(): Flow<List<TaskEntity>> {
        val tasks = listOf(TaskEntity(id = 1, title = "Task", createdAt = 0, updatedAt = 0))
        return flowOf(tasks)
    }

    override suspend fun get(id: Long): TaskEntity? {
        return null
    }

    override suspend fun insert(task: TaskEntity): Long {
        insertCalled = true
        insertedTask = task
        return 1L
    }

    override suspend fun update(task: TaskEntity) {
        // Not used in this test
    }

    override suspend fun delete(task: TaskEntity) {
        deleteCalled = true
        deletedTask = task
    }
}

class TaskRepositoryTest {
    private lateinit var dao: FakeTaskDao
    private lateinit var repository: TaskRepository
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        dao = FakeTaskDao()
        repository = TaskRepository(dao, dispatcher)
    }

    @Test
    fun observeAll_returnsFlowFromDao() = runTest(dispatcher) {
        val result = repository.observeAll().first()
        assertEquals(1, result.size)
        assertEquals("Task", result[0].title)
    }

    @Test
    fun save_insertsTaskViaDao() = runTest(dispatcher) {
        val task = TaskEntity(title = "New Task", createdAt = 0, updatedAt = 0)

        repository.save(task)

        assertEquals(true, dao.insertCalled)
        assertEquals("New Task", dao.insertedTask?.title)
    }

    @Test
    fun delete_deletesTaskViaDao() = runTest(dispatcher) {
        val task = TaskEntity(id = 1, title = "Task to delete", createdAt = 0, updatedAt = 0)

        repository.delete(task)

        assertEquals(true, dao.deleteCalled)
        assertEquals("Task to delete", dao.deletedTask?.title)
    }
}
