package com.ordia.app.data

import kotlinx.coroutines.flow.Flow

/** Single source of truth for notes. Thin wrapper over [NoteDao]. */
open class NoteRepository(private val dao: NoteDao) {
    open fun observeAll(): Flow<List<NoteEntity>> = dao.observeAll()

    open suspend fun get(id: Long): NoteEntity? = dao.getById(id)

    open suspend fun save(note: NoteEntity): Long = dao.insert(note)

    open suspend fun update(note: NoteEntity) = dao.update(note)

    open suspend fun delete(note: NoteEntity) = dao.delete(note)

    open suspend fun togglePinned(id: Long, pinned: Boolean) = dao.setPinned(id, pinned)

    open suspend fun create(title: String, content: String): Long {
        val now = System.currentTimeMillis()
        return dao.insert(NoteEntity(title = title, content = content, createdAt = now, updatedAt = now))
    }
}
