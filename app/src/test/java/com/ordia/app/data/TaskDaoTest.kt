package com.ordia.app.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider

import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TaskDaoTest {
    private lateinit var database: NoteDatabase
    private lateinit var dao: TaskDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NoteDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.taskDao()
    }

    @Test
    fun insertAndRetrieveTask() = runTest {
        val task = TaskEntity(
            title = "Test Task",
            description = "Description",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val id = dao.insert(task)
        val retrieved = dao.get(id)

        assertEquals(task.title, retrieved?.title)
        assertEquals(task.description, retrieved?.description)
    }

    @Test
    fun updateTask() = runTest {
        val task = TaskEntity(
            title = "Original Title",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val id = dao.insert(task)

        val updated = task.copy(id = id, title = "New Title", status = "COMPLETED")
        dao.update(updated)

        val retrieved = dao.get(id)
        assertEquals("New Title", retrieved?.title)
        assertEquals("COMPLETED", retrieved?.status)
    }

    @Test
    fun deleteTask() = runTest {
        val task = TaskEntity(
            title = "Task to delete",
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val id = dao.insert(task)
        val inserted = dao.get(id)!!

        dao.delete(inserted)

        val retrieved = dao.get(id)
        assertNull(retrieved)
    }

    @Test
    fun observeAllTasks() = runTest {
        val task1 = TaskEntity(title = "Task 1", createdAt = 1000L, updatedAt = 1000L)
        val task2 = TaskEntity(title = "Task 2", createdAt = 2000L, updatedAt = 2000L)

        dao.insert(task1)
        dao.insert(task2)

        val tasks = dao.observeAll().first()
        assertEquals(2, tasks.size)
    }
}
