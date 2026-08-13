package com.ordia.app.ui.screens

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ordia.app.data.local.AttachmentEntity
import com.ordia.app.data.local.AttachmentOwnerType
import com.ordia.app.data.local.NoteEntity
import com.ordia.app.domain.NoteBlock
import com.ordia.app.domain.NoteBlockCodec
import com.ordia.app.domain.NoteBlockType
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.OrdiaViewModel
import com.ordia.app.ui.components.ordiaWorkSurface
import kotlinx.coroutines.delay

@Composable
fun NoteEditorScreen(
    state: OrdiaUiState,
    vm: OrdiaViewModel,
    noteId: Long,
    contentPadding: PaddingValues,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var currentId by rememberSaveable(noteId) { mutableLongStateOf(noteId) }
    val existing = state.note(currentId)
    var title by remember(noteId) { mutableStateOf(state.note(noteId)?.title.orEmpty()) }
    val blocks = remember(noteId) {
        val initial = state.note(noteId)
        mutableStateListOf<NoteBlock>().apply { addAll(NoteBlockCodec.decode(initial?.blocksData.orEmpty(), initial?.body.orEmpty())) }
    }
    var addMenu by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }

    fun saveCurrent(onSaved: (Long) -> Unit = {}) {
        val base = existing ?: NoteEntity(id = currentId, title = title)
        val note = base.copy(title = title, body = NoteBlockCodec.toPlainText(blocks), blocksData = NoteBlockCodec.encode(blocks))
        vm.saveNote(note, blocks) { id ->
            currentId = id
            dirty = false
            onSaved(id)
        }
    }

    val pickAttachment = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && currentId > 0L) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            var displayName = uri.lastPathSegment ?: "Archivo"
            var sizeBytes = 0L
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0) displayName = cursor.getString(nameIndex) ?: displayName
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) sizeBytes = cursor.getLong(sizeIndex)
                }
            }
            vm.addAttachment(
                AttachmentEntity(
                    ownerType = AttachmentOwnerType.NOTE,
                    ownerId = currentId,
                    uri = uri.toString(),
                    displayName = displayName,
                    mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream",
                    sizeBytes = sizeBytes
                )
            )
        }
    }
    val attachments = if (currentId > 0L) state.attachmentsFor(AttachmentOwnerType.NOTE, currentId) else emptyList()

    fun attachFile() {
        if (currentId > 0L) pickAttachment.launch(arrayOf("*/*"))
        else saveCurrent { pickAttachment.launch(arrayOf("*/*")) }
    }

    fun hasMeaningfulContent(): Boolean = title.isNotBlank() || blocks.any { it.text.isNotBlank() || it.type == NoteBlockType.DIVIDER }

    fun saveAndBack() {
        if (currentId > 0L || hasMeaningfulContent()) saveCurrent()
        onBack()
    }

    androidx.compose.runtime.LaunchedEffect(title, blocks.toList(), dirty, currentId) {
        if (dirty && currentId > 0L) {
            delay(800)
            saveCurrent()
        }
    }
    BackHandler { saveAndBack() }

    Column(
        Modifier.fillMaxSize().padding(contentPadding).ordiaWorkSurface().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { saveAndBack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Guardar y volver") }
            Text(if (dirty) "Cambios sin guardar" else "Guardado local", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            IconButton(onClick = { attachFile() }) { Icon(Icons.Outlined.AttachFile, "Adjuntar archivo") }
            IconButton(onClick = {
                val plainText = buildString {
                    append(title)
                    append("\n\n")
                    blocks.forEach { block -> append(block.text).append("\n") }
                }
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    putExtra(Intent.EXTRA_TEXT, plainText)
                }
                context.startActivity(Intent.createChooser(sendIntent, "Compartir nota"))
            }) { Icon(Icons.Outlined.Share, "Compartir nota") }
            TextButton(onClick = { existing?.let { vm.deleteNote(it); onBack() } }, enabled = existing != null) { Text("Archivar") }
            Button(onClick = { saveAndBack() }) {
                Icon(Icons.Outlined.Save, null)
                Text("Guardar", Modifier.padding(start = 6.dp))
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; dirty = true },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Título de la nota") },
                    textStyle = MaterialTheme.typography.headlineLarge,
                    singleLine = true
                )
            }
            itemsIndexed(blocks, key = { _, block -> block.id }) { index, block ->
                NoteBlockEditor(
                    index = index,
                    block = block,
                    total = blocks.size,
                    onChange = { updated -> blocks[index] = updated; dirty = true },
                    onMoveUp = { if (index > 0) { val item = blocks.removeAt(index); blocks.add(index - 1, item); dirty = true } },
                    onMoveDown = { if (index < blocks.lastIndex) { val item = blocks.removeAt(index); blocks.add(index + 1, item); dirty = true } },
                    onDelete = { if (blocks.size > 1) blocks.removeAt(index) else blocks[0] = NoteBlock(); dirty = true }
                )
            }
            if (attachments.isNotEmpty()) {
                item {
                    Text("Archivos adjuntos", style = MaterialTheme.typography.titleMedium)
                }
                itemsIndexed(attachments, key = { _, attachment -> attachment.id }) { _, attachment ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.InsertDriveFile, null, modifier = Modifier.size(22.dp))
                        TextButton(
                            onClick = {
                                val uri = android.net.Uri.parse(attachment.uri)
                                val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, attachment.mimeType)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                runCatching { context.startActivity(intent) }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                                Text(attachment.displayName, maxLines = 1)
                                Text(formatBytes(attachment.sizeBytes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = { vm.deleteAttachment(attachment) }) {
                            Icon(Icons.Outlined.DeleteOutline, "Quitar ${attachment.displayName}")
                        }
                    }
                }
            }
            item {
                Column {
                    Button(onClick = { addMenu = true }) {
                        Icon(Icons.Outlined.Add, null)
                        Text("Añadir bloque", Modifier.padding(start = 6.dp))
                    }
                    DropdownMenu(addMenu, { addMenu = false }) {
                        NoteBlockType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.label()) },
                                leadingIcon = { Icon(type.icon(), null) },
                                onClick = { blocks.add(NoteBlock(type = type)); dirty = true; addMenu = false }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteBlockEditor(
    index: Int,
    block: NoteBlock,
    total: Int,
    onChange: (NoteBlock) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onMoveUp, enabled = index > 0) { Icon(Icons.Outlined.ArrowUpward, "Mover arriba") }
            Text((index + 1).toString(), style = MaterialTheme.typography.labelSmall)
            IconButton(onClick = onMoveDown, enabled = index < total - 1) { Icon(Icons.Outlined.ArrowDownward, "Mover abajo") }
        }
        if (block.type == NoteBlockType.CHECKLIST) {
            Checkbox(block.checked, { onChange(block.copy(checked = it)) })
        }
        if (block.type == NoteBlockType.DIVIDER) {
            Column(Modifier.weight(1f).padding(vertical = 18.dp)) {
                androidx.compose.material3.HorizontalDivider()
                Text("Separador", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            OutlinedTextField(
                value = block.text,
                onValueChange = { onChange(block.copy(text = it)) },
                modifier = Modifier.weight(1f),
                placeholder = { Text(block.type.label()) },
                minLines = if (block.type == NoteBlockType.PARAGRAPH || block.type == NoteBlockType.QUOTE) 2 else 1,
                textStyle = when (block.type) {
                    NoteBlockType.HEADING -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
                    NoteBlockType.QUOTE -> MaterialTheme.typography.bodyLarge.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    else -> MaterialTheme.typography.bodyLarge
                }
            )
        }
        IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, "Eliminar bloque") }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes <= 0L -> "Tamaño desconocido"
    bytes < 1_024L -> "$bytes B"
    bytes < 1_048_576L -> "${bytes / 1_024} KB"
    else -> "${"%.1f".format(bytes / 1_048_576.0)} MB"
}

private fun NoteBlockType.label() = when (this) {
    NoteBlockType.PARAGRAPH -> "Párrafo"
    NoteBlockType.HEADING -> "Encabezado"
    NoteBlockType.CHECKLIST -> "Lista de tareas"
    NoteBlockType.QUOTE -> "Cita"
    NoteBlockType.BULLET -> "Viñeta"
    NoteBlockType.NUMBERED -> "Lista numerada"
    NoteBlockType.DIVIDER -> "Separador"
}
private fun NoteBlockType.icon() = when (this) {
    NoteBlockType.PARAGRAPH -> Icons.Outlined.Add
    NoteBlockType.HEADING -> Icons.Outlined.Title
    NoteBlockType.CHECKLIST -> Icons.Outlined.CheckBox
    NoteBlockType.QUOTE -> Icons.Outlined.FormatQuote
    NoteBlockType.BULLET, NoteBlockType.NUMBERED -> Icons.AutoMirrored.Outlined.FormatListBulleted
    NoteBlockType.DIVIDER -> Icons.Outlined.HorizontalRule
}
