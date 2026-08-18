package com.ordia.app

import android.app.Application
import com.ordia.app.data.NoteDatabase
import com.ordia.app.data.NoteRepository

class OrdiaApplication : Application() {
    val repository: NoteRepository by lazy {
        NoteRepository(NoteDatabase.get(this).noteDao())
    }
}
