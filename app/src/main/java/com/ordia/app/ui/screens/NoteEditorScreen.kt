package com.ordia.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ordia.app.data.NoteEntity
import com.ordia.app.ui.components.OrdiaInput
import com.ordia.app.ui.components.OrdiaTopBar
import com.ordia.app.ui.theme.LocalSpacing

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
            OrdiaTopBar(
                title = "Editar",
                navigationIcon = Icons.AutoMirrored.Outlined.ArrowBack,
                navigationContentDescription = "Volver",
                onNavigationClick = {
                    onSave(title, content, note?.id)
                    onBack()
                },
                actionText = "Hecho",
                onActionClick = {
                    onSave(title, content, note?.id)
                    onBack()
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val spacing = LocalSpacing.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.horizontalScreen, vertical = spacing.verticalScreen),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            OrdiaInput(
                value = title,
                onValueChange = { title = it },
                placeholder = "Título",
                textStyle = MaterialTheme.typography.titleLarge
            )
            OrdiaInput(
                value = content,
                onValueChange = { content = it },
                placeholder = "Escribe lo que piensas…",
                textStyle = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
