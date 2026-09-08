package com.ordia.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ordia.app.data.NoteEntity
import com.ordia.app.data.NoteRepository
import com.ordia.app.data.TaskDao
import com.ordia.app.data.TaskEntity
import com.ordia.app.intelligence.NaturalTaskParser
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotepadViewModel(private val repo: NoteRepository, private val taskDao: TaskDao) : ViewModel() {
    val notes: StateFlow<List<NoteEntity>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tasks: StateFlow<List<TaskEntity>> =
        taskDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(title: String, content: String, existingId: Long? = null) {
        viewModelScope.launch {
            if (title.isBlank() && content.isBlank()) {
                if (existingId != null) {
                    val current = repo.get(existingId)
                    if (current != null) {
                        repo.delete(current)
                    }
                }
                return@launch
            }
            if (existingId != null) {
                val current = repo.get(existingId)
                val now = System.currentTimeMillis()
                if (current != null) {
                    repo.update(current.copy(title = title, content = content, updatedAt = now))
                }
            } else {
                repo.save(NoteEntity(title = title, content = content, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
            }
        }
    }

    fun delete(note: NoteEntity) {
        viewModelScope.launch { repo.delete(note) }
    }

    fun togglePinned(note: NoteEntity) {
        viewModelScope.launch { repo.togglePinned(note.id, !note.pinned) }
    }

    fun capture(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val result = NaturalTaskParser.parse(text, now)
            if (result.isTask) {
                taskDao.insert(
                    TaskEntity(
                        title = result.title,
                        dueDate = result.dueDate,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            } else {
                repo.save(
                    NoteEntity(
                        title = result.title,
                        content = "",
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
        }
    }

    fun toggleTaskCompletion(task: TaskEntity) {
        viewModelScope.launch {
            taskDao.updateCompletion(task.id, !task.isCompleted, System.currentTimeMillis())
        }
    }
}
