package com.ordia.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.ordia.app.data.NoteRepository

class NotepadViewModelFactory(
    private val repository: NoteRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass == NotepadViewModel::class.java) { "Unknown ViewModel $modelClass" }
        val savedState: SavedStateHandle = extras.createSavedStateHandle()
        return NotepadViewModel(repository, savedState) as T
    }
}
