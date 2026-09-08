package com.ordia.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ordia.app.data.TaskEntity
import com.ordia.app.data.TaskRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskViewModel(private val repo: TaskRepository) : ViewModel() {
    val tasks: StateFlow<List<TaskEntity>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(title: String, description: String, existingId: Long? = null) {
        viewModelScope.launch {
            if (existingId != null) {
                val current = repo.get(existingId)
                if (current != null) {
                    repo.update(current.copy(title = title, description = description))
                }
            } else {
                repo.save(
                    TaskEntity(
                        title = title,
                        description = description,
                        createdAt = 0,
                        updatedAt = 0
                    )
                )
            }
        }
    }

    fun delete(task: TaskEntity) {
        viewModelScope.launch { repo.delete(task) }
    }

    fun toggleStatus(task: TaskEntity) {
        viewModelScope.launch {
            val newStatus = if (task.status == "PENDING") "COMPLETED" else "PENDING"
            repo.update(task.copy(status = newStatus))
        }
    }
}
