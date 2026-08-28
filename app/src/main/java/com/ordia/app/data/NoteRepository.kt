package com.ordia.app.data

import kotlinx.coroutines.flow.Flow

/** Single source of truth for notes. Thin wrapper over [NoteDao]. */
class NoteRepository(private val dao: NoteDao) {
    fun observeAll(): Flow<List<NoteEntity>> = dao.observeAll()

    fun observeSearch(query: String): Flow<List<NoteEntity>> = dao.observeSearch(query)

    suspend fun get(id: Long): NoteEntity? = dao.getById(id)

    suspend fun save(note: NoteEntity): Long = dao.insert(note)

    suspend fun update(note: NoteEntity) = dao.update(note)

    suspend fun delete(note: NoteEntity) = dao.delete(note)

    suspend fun togglePinned(id: Long) = dao.togglePinned(id)

    suspend fun create(title: String, content: String): Long {
        val now = System.currentTimeMillis()
        return dao.insert(NoteEntity(title = title, content = content, createdAt = now, updatedAt = now))
    }
}
