package com.ordia.app.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class OrdiaViewModel : ViewModel() {

    private val _taskState = MutableStateFlow(false)
    val taskState: StateFlow<Boolean> = _taskState

    fun addSmartTask() {
        _taskState.value = true
    }

    fun saveTask() {
        _taskState.value = true
    }

    fun saveNote() {}

    fun toggleHabit() {}

    fun saveFocusSession() {}

    fun exportBackup() {}

    fun restoreArchived() {}

    fun applyDayPlan() {}
}
