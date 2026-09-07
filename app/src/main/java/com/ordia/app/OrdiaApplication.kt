package com.ordia.app

import android.app.Application
import com.ordia.app.data.OrdiaDatabase
import com.ordia.app.data.NoteRepository
import com.ordia.app.data.TaskRepository

class OrdiaApplication : Application() {
    val database: OrdiaDatabase by lazy { OrdiaDatabase.get(this) }

    val repository: NoteRepository by lazy {
        NoteRepository(database.noteDao())
    }

    val taskRepository: TaskRepository by lazy {
        TaskRepository(database.taskDao())
    }
}
