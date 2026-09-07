package com.ordia.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class TaskDaoTest {

    private lateinit var db: OrdiaDatabase
    private lateinit var dao: TaskDao

    private fun task(
        title: String,
        isCompleted: Boolean = false,
        updatedAt: Long = 1000L,
        createdAt: Long = 1000L,
    ) = TaskEntity(title = title, isCompleted = isCompleted, createdAt = createdAt, updatedAt = updatedAt)

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, OrdiaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.taskDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun insertAndObserveAll_returnsTask() = runTest {
        dao.insert(task("Comprar pan"))
        val all = dao.observeAll().first()
        assertEquals(1, all.size)
        assertEquals("Comprar pan", all.first().title)
    }

    @Test
    fun getById_returnsNullForMissing() = runTest {
        assertNull(dao.getById(999L))
    }

    @Test
    fun update_changesTitleAndUpdatedAt() = runTest {
        val id = dao.insert(task("Original"))
        val created = dao.getById(id)!!
        dao.update(created.copy(title = "Editado", updatedAt = 2000L))
        val updated = dao.getById(id)!!
        assertEquals("Editado", updated.title)
        assertEquals(2000L, updated.updatedAt)
    }

    @Test
    fun delete_removesTask() = runTest {
        val id = dao.insert(task("Bórrame"))
        val t = dao.getById(id)!!
        dao.delete(t)
        assertNull(dao.getById(id))
    }

    @Test
    fun setCompleted_togglesFlag() = runTest {
        val id = dao.insert(task("A completar", isCompleted = false))
        dao.setCompleted(id, true)
        assertTrue(dao.getById(id)!!.isCompleted)
        dao.setCompleted(id, false)
        assertFalse(dao.getById(id)!!.isCompleted)
    }
}
