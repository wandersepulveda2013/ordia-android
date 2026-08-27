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
class NoteDaoTest {

    private lateinit var db: NoteDatabase
    private lateinit var dao: NoteDao

    private fun note(
        title: String,
        content: String = "",
        pinned: Boolean = false,
        updatedAt: Long = 1000L,
        createdAt: Long = 1000L,
    ) = NoteEntity(title = title, content = content, createdAt = createdAt, updatedAt = updatedAt, pinned = pinned)

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.noteDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun insertAndObserveAll_returnsNote() = runTest {
        dao.insert(note("Lista de compras", "Leche, pan"))
        val all = dao.observeAll().first()
        assertEquals(1, all.size)
        assertEquals("Lista de compras", all.first().title)
    }

    @Test
    fun pinnedNotesAppearFirst() = runTest {
        dao.insert(note("Sin fijar A"))
        dao.insert(note("Fijada", pinned = true))
        dao.insert(note("Sin fijar B"))
        val all = dao.observeAll().first()
        assertTrue(all.first().pinned)
    }

    @Test
    fun getById_returnsNullForMissing() = runTest {
        assertNull(dao.getById(999L))
    }

    @Test
    fun update_changesTitleAndUpdatedAt() = runTest {
        val id = dao.insert(note("Original"))
        val created = dao.getById(id)!!
        dao.update(created.copy(title = "Editado", updatedAt = 2000L))
        val updated = dao.getById(id)!!
        assertEquals("Editado", updated.title)
        assertEquals(2000L, updated.updatedAt)
        assertEquals(1000L, updated.createdAt)
    }

    @Test
    fun delete_removesNote() = runTest {
        val id = dao.insert(note("Bórrame"))
        val n = dao.getById(id)!!
        dao.delete(n)
        assertNull(dao.getById(id))
    }

    @Test
    fun setPinned_togglesFlag() = runTest {
        val id = dao.insert(note("A fijar", pinned = false))
        dao.setPinned(id, true)
        assertTrue(dao.getById(id)!!.pinned)
        dao.setPinned(id, false)
        assertFalse(dao.getById(id)!!.pinned)
    }

    @Test
    fun clear_removesAll() = runTest {
        dao.insert(note("Uno"))
        dao.insert(note("Dos"))
        dao.clear()
        assertTrue(dao.observeAll().first().isEmpty())
    }

    @Test
    fun ordering_byUpdatedAtDescendingWhenUnpinned() = runTest {
        dao.insert(note("Vieja", updatedAt = 1000L))
        dao.insert(note("Nueva", updatedAt = 5000L))
        val all = dao.observeAll().first()
        assertEquals("Nueva", all.first().title)
    }

    @Test
    fun observeSearch_matchesTitleAndContentIgnoringCase() = runTest {
        dao.insert(note("Receta de paella", "Azafrán y arroz"))
        dao.insert(note("Lista de la compra", "Leche, paella congelada"))
        dao.insert(note("Ideas", "Otra cosa"))

        val byTitle = dao.observeSearch("paella").first()
        assertEquals(2, byTitle.size)

        val byContent = dao.observeSearch("leche").first()
        assertEquals(1, byContent.size)
        assertEquals("Lista de la compra", byContent.first().title)

        val caseInsensitive = dao.observeSearch("PAELLA").first()
        assertEquals(2, caseInsensitive.size)

        val noMatch = dao.observeSearch("inexistente").first()
        assertTrue(noMatch.isEmpty())
    }

    @Test
    fun observeSearch_blankQueryReturnsAll() = runTest {
        dao.insert(note("Uno"))
        dao.insert(note("Dos"))
        assertEquals(2, dao.observeSearch("").first().size)
    }
}
