package com.ordia.app.data

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteRepositoryTest {

    private val sample = listOf(
        NoteEntity(id = 1, title = "A", content = "x", createdAt = 1, updatedAt = 2, pinned = false),
        NoteEntity(id = 2, title = "B", content = "y", createdAt = 3, updatedAt = 4, pinned = true),
    )

    private class FakeDao(var notes: MutableList<NoteEntity> = mutableListOf()) : NoteDao {
        override fun observeAll() = flowOf(notes.toList())
        override suspend fun getById(id: Long) = notes.firstOrNull { it.id == id }
        override suspend fun insert(note: NoteEntity): Long {
            val nextId = (notes.maxOfOrNull { it.id } ?: 0L) + 1
            val withId = note.copy(id = nextId)
            notes.add(withId)
            return nextId
        }
        override suspend fun update(note: NoteEntity) {
            val idx = notes.indexOfFirst { it.id == note.id }
            if (idx >= 0) notes[idx] = note
        }
        override suspend fun delete(note: NoteEntity) { notes.removeAll { it.id == note.id } }
        override suspend fun setPinned(id: Long, pinned: Boolean) {
            val idx = notes.indexOfFirst { it.id == id }
            if (idx >= 0) notes[idx] = notes[idx].copy(pinned = pinned)
        }
        override suspend fun clear() { notes.clear() }
    }

    private val dao = FakeDao(sample.toMutableList())
    private val repo = NoteRepository(dao)

    @Test
    fun observeAll_emitsFromDao() = runTest {
        assertEquals(sample, repo.observeAll().first())
    }

    @Test
    fun get_returnsById() = runTest {
        assertEquals("A", repo.get(1)?.title)
        assertEquals(null, repo.get(99))
    }

    @Test
    fun save_insertsNewNote() = runTest {
        val id = repo.save(NoteEntity(title = "C", content = "z", createdAt = 10, updatedAt = 11))
        assertEquals("C", repo.get(id)?.title)
    }

    @Test
    fun update_mutatesExisting() = runTest {
        repo.update(sample[0].copy(title = "A2"))
        assertEquals("A2", repo.get(1)?.title)
    }

    @Test
    fun delete_removesNote() = runTest {
        repo.delete(sample[1])
        assertEquals(null, repo.get(2))
    }

    @Test
    fun togglePinned_flipsFlag() = runTest {
        repo.togglePinned(1, true)
        assertEquals(true, repo.get(1)?.pinned)
    }

    @Test
    fun create_setsTimestamps() = runTest {
        val before = System.currentTimeMillis()
        val id = repo.create("Nueva", "cuerpo")
        val n = repo.get(id)!!
        assertEquals("Nueva", n.title)
        assertTrue(n.createdAt >= before)
        assertEquals(n.createdAt, n.updatedAt)
    }
}
