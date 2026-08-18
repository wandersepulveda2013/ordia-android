package com.ordia.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ordia.app.data.NoteRepository

class NotepadViewModelFactory(
    private val repository: NoteRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == NotepadViewModel::class.java) { "Unknown ViewModel $modelClass" }
        return NotepadViewModel(repository) as T
    }
}
