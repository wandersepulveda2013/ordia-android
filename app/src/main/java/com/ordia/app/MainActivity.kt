package com.ordia.app

import android.os.Bundle
import androidx.activity.ComponentActivity

import androidx.activity.compose.setContent
import com.ordia.app.ui.NotepadApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NotepadApp()
        }
    }
}
