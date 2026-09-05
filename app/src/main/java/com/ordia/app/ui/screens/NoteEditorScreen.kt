package com.ordia.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ordia.app.data.NoteEntity
import com.ordia.app.ui.components.OrdiaTextField
import com.ordia.app.ui.components.OrdiaTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    note: NoteEntity?,
    onBack: () -> Unit,
    onSave: (title: String, content: String, id: Long?) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(note?.title.orEmpty()) }
    var content by rememberSaveable { mutableStateOf(note?.content.orEmpty()) }

    LaunchedEffect(note?.id) {
        if (note != null) {
            title = note.title
            content = note.content
        }
    }

    Scaffold(
        topBar = {
            OrdiaTopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        onSave(title, content, note?.id)
                        onBack()
                    }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver") }
                },
                title = { Text("Editar", style = MaterialTheme.typography.titleMedium) },
                actions = {
                    Text(
                        "Hecho",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .clickable {
                                onSave(title, content, note?.id)
                                onBack()
                            },
                    )
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OrdiaTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("Título", style = MaterialTheme.typography.titleLarge) },
                textStyle = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OrdiaTextField(
                value = content,
                onValueChange = { content = it },
                placeholder = { Text("Escribe lo que piensas…", style = MaterialTheme.typography.bodyLarge) },
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
