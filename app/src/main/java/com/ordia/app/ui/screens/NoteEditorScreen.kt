package com.ordia.app.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ordia.app.data.NoteEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    note: NoteEntity?,
    onBack: () -> Unit,
    onAutosave: (title: String, content: String) -> Unit,
    onCommit: (title: String, content: String) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(note?.title.orEmpty()) }
    var content by rememberSaveable { mutableStateOf(note?.content.orEmpty()) }

    BackHandler {
        onCommit(title, content)
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        onCommit(title, content)
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
                                onCommit(title, content)
                                onBack()
                            },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
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
            TextField(
                value = title,
                onValueChange = { title = it; onAutosave(title, content) },
                placeholder = { Text("Título", style = MaterialTheme.typography.titleLarge) },
                textStyle = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
                colors = bareFieldColors(),
            )
            TextField(
                value = content,
                onValueChange = { content = it; onAutosave(title, content) },
                placeholder = { Text("Escribe lo que piensas…", style = MaterialTheme.typography.bodyLarge) },
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
                colors = bareFieldColors(),
            )
        }
    }
}

@Composable
private fun bareFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
)
