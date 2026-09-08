package com.ordia.app

import android.app.Application
import com.ordia.app.data.NoteDatabase
import com.ordia.app.data.NoteRepository

import com.ordia.app.data.TaskRepository

class OrdiaApplication : Application() {
    val repository: NoteRepository by lazy {
        NoteRepository(NoteDatabase.get(this).noteDao())
    }

    val taskRepository: TaskRepository by lazy {
        TaskRepository(NoteDatabase.get(this).taskDao())
    }
}
