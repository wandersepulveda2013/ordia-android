package com.ordia.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ordia.app.ui.NotepadApp
import com.ordia.app.ui.NotepadViewModel
import com.ordia.app.ui.NotepadViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as OrdiaApplication
        setContent {
            val viewModel: NotepadViewModel = viewModel(factory = NotepadViewModelFactory(app.repository))
            NotepadApp(viewModel)
        }
    }
}
