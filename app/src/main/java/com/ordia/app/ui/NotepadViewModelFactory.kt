package com.ordia.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ordia.app.data.NoteRepository
import com.ordia.app.data.TaskDao

class NotepadViewModelFactory(
    private val repository: NoteRepository,
    private val taskDao: TaskDao,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == NotepadViewModel::class.java) { "Unknown ViewModel $modelClass" }
        return NotepadViewModel(repository, taskDao) as T
    }
}
