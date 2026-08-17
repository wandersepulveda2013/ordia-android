package com.ordia.app.ui.screens

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatClear
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatPaint
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ordia.app.R
import com.ordia.app.data.local.AttachmentEntity
import com.ordia.app.data.local.AttachmentOwnerType
import com.ordia.app.data.local.NoteEntity
import com.ordia.app.domain.NoteBlock
import com.ordia.app.domain.NoteBlockCodec
import com.ordia.app.domain.NoteBlockType
import com.ordia.app.domain.NoteSpan
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.OrdiaViewModel

/**
 * Editor de notas de ORDÍA.
 *
 * Interfaz mínima que desaparece: documento continuo, autosave desde el primer
 * carácter, undo/redo, barra inferior diminuta y hoja de inserción categorizada.
 * Los bloques son ricos internamente; el usuario percibe un único flujo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    state: OrdiaUiState,
    vm: OrdiaViewModel,
    noteId: Long,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onTask: (Long) -> Unit
) {
    val context = LocalContext.current
    val defaultAttachmentName = stringResource(R.string.note_editor_default_filename)
    var currentId by rememberSaveable(noteId) { mutableLongStateOf(noteId) }
    val existing = state.note(currentId)
    var title by remember(noteId) { mutableStateOf(state.note(noteId)?.title.orEmpty()) }
    val blocks = remember(noteId) {
        val initial = state.note(noteId)
        mutableStateListOf<NoteBlock>().apply {
            addAll(NoteBlockCodec.decode(initial?.blocksData.orEmpty(), initial?.body.orEmpty()))
        }
    }

    var dirty by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    var insertOpen by remember { mutableStateOf(false) }
    var infoOpen by remember { mutableStateOf(false) }
    var focusMode by remember { mutableStateOf(false) }

    val undoStack = remember { mutableStateListOf<Pair<String, List<NoteBlock>>>() }
    val redoStack = remember { mutableStateListOf<Pair<String, List<NoteBlock>>>() }
    var lastSnapshot by remember { mutableStateOf<Pair<String, List<NoteBlock>>?>(null) }

    fun snapshot(): Pair<String, List<NoteBlock>> = title to blocks.toList()
    fun pushUndo() {
        val current = snapshot()
        if (lastSnapshot != null && lastSnapshot != current) {
            undoStack.add(lastSnapshot!!)
            if (undoStack.size > 100) undoStack.removeAt(0)
            redoStack.clear()
        }
        lastSnapshot = current
    }
    fun undo() {
        if (undoStack.isEmpty()) return
        val prev = undoStack.removeAt(undoStack.lastIndex)
        redoStack.add(snapshot())
        title = prev.first
        blocks.clear(); blocks.addAll(prev.second)
        lastSnapshot = prev
        dirty = true
    }
    fun redo() {
        if (redoStack.isEmpty()) return
        val next = redoStack.removeAt(redoStack.lastIndex)
        undoStack.add(snapshot())
        title = next.first
        blocks.clear(); blocks.addAll(next.second)
        lastSnapshot = next
        dirty = true
    }

    fun saveCurrent(onSaved: (Long) -> Unit = {}) {
        val base = existing ?: NoteEntity(id = currentId, title = title)
        val note = base.copy(
            title = title.trim().ifBlank { "Nota sin título" },
            body = NoteBlockCodec.toPlainText(blocks),
            blocksData = NoteBlockCodec.encode(blocks)
        )
        saving = true
        vm.saveNote(note, blocks) { id ->
            currentId = id
            dirty = false
            saving = false
            onSaved(id)
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            var displayName = uri.lastPathSegment ?: "imagen"
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0) displayName = c.getString(i) ?: displayName
                }
            }
            blocks.add(
                NoteBlock(
                    type = NoteBlockType.IMAGE,
                    attachmentUri = uri.toString(),
                    attachmentName = displayName,
                    mimeType = context.contentResolver.getType(uri) ?: "image/*"
                )
            )
            dirty = true
        }
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && currentId > 0L) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            var displayName = uri.lastPathSegment ?: defaultAttachmentName
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

    fun hasMeaningfulContent(): Boolean =
        title.isNotBlank() || blocks.any { it.text.isNotBlank() || it.attachmentUri.isNotBlank() || it.type == NoteBlockType.DIVIDER }

    fun saveAndBack() {
        if (currentId > 0L || hasMeaningfulContent()) saveCurrent()
        onBack()
    }
    fun attachFile() {
        if (currentId > 0L) pickFile.launch(arrayOf("*/*"))
        else saveCurrent { pickFile.launch(arrayOf("*/*")) }
    }

    LaunchedEffect(title, blocks.toList(), dirty, currentId) {
        if (dirty && (currentId > 0L || hasMeaningfulContent())) {
            kotlinx.coroutines.delay(800)
            saveCurrent()
        }
    }
    BackHandler { saveAndBack() }

    fun updateBlock(index: Int, updated: NoteBlock) { pushUndo(); blocks[index] = updated; dirty = true }
    fun addBlock(type: NoteBlockType) { pushUndo(); blocks.add(NoteBlock(type = type)); dirty = true }
    fun deleteBlock(index: Int) {
        pushUndo()
        if (blocks.size > 1) blocks.removeAt(index) else blocks[0] = NoteBlock()
        dirty = true
    }
    fun convertBlock(index: Int, type: NoteBlockType) { pushUndo(); blocks[index] = blocks[index].copy(type = type); dirty = true }
    fun moveBlock(index: Int, delta: Int) {
        val target = index + delta
        if (target !in blocks.indices) return
        pushUndo()
        val item = blocks.removeAt(index); blocks.add(target, item); dirty = true
    }
    fun toggleSpanFormat(index: Int, transform: (NoteSpan) -> NoteSpan) {
        pushUndo()
        val block = blocks[index]
        val spans = block.spans ?: listOf(NoteSpan(text = block.text))
        val newSpans = if (spans.size == 1) listOf(transform(spans[0])) else spans.map(transform)
        blocks[index] = block.copy(spans = newSpans, text = newSpans.joinToString("") { it.text })
        dirty = true
    }
    fun clearFormat(index: Int) {
        pushUndo()
        val block = blocks[index]
        blocks[index] = block.copy(spans = null, text = block.plainText)
        dirty = true
    }

    val attachments = if (currentId > 0L) state.attachmentsFor(AttachmentOwnerType.NOTE, currentId) else emptyList()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0)
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            if (!focusMode) {
                TopAppBar(
                    title = {
                        Text(
                            if (saving) stringResource(R.string.notes_editor_save_saving)
                            else if (dirty) stringResource(R.string.note_editor_dirty)
                            else stringResource(R.string.notes_editor_save_saved),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { saveAndBack() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.note_editor_back_save))
                        }
                    },
                    actions = {
                        IconButton(onClick = { focusMode = true }) {
                            Icon(Icons.Outlined.Bolt, stringResource(R.string.notes_editor_focus_mode))
                        }
                        IconButton(
                            onClick = { existing?.let { vm.toggleFavorite(it) } },
                            enabled = existing != null
                        ) {
                            Icon(
                                Icons.Outlined.StarBorder,
                                stringResource(R.string.notes_favorite),
                                tint = if (existing?.favorite == true) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box {
                            IconButton(onClick = { overflowOpen = true }) {
                                Icon(Icons.Outlined.MoreVert, stringResource(R.string.notes_menu_overflow))
                            }
                            EditorOverflowMenu(
                                expanded = overflowOpen,
                                onDismiss = { overflowOpen = false },
                                note = existing,
                                vm = vm,
                                onInfo = { overflowOpen = false; infoOpen = true },
                                onDelete = { overflowOpen = false; existing?.let { vm.trashNote(it); onBack() } }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }

            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { pushUndo(); title = it; dirty = true },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.notes_editor_title_hint), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                        singleLine = true
                    )
                }
                itemsIndexed(blocks, key = { _, block -> block.id }) { index, block ->
                    BlockRow(
                        index = index,
                        block = block,
                        onChange = { updateBlock(index, it) },
                        onDelete = { deleteBlock(index) },
                        onMoveUp = { moveBlock(index, -1) },
                        onMoveDown = { moveBlock(index, 1) },
                        onToggleBold = { toggleSpanFormat(index) { s -> s.copy(bold = !s.bold) } },
                        onToggleItalic = { toggleSpanFormat(index) { s -> s.copy(italic = !s.italic) } },
                        onToggleUnderline = { toggleSpanFormat(index) { s -> s.copy(underline = !s.underline) } },
                        onToggleStrike = { toggleSpanFormat(index) { s -> s.copy(strikethrough = !s.strikethrough) } },
                        onToggleHighlight = { toggleSpanFormat(index) { s -> s.copy(highlight = !s.highlight) } },
                        onClearFormat = { clearFormat(index) }
                    )
                }
                if (attachments.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.note_editor_attachments), style = MaterialTheme.typography.titleSmall)
                    }
                    itemsIndexed(attachments, key = { _, a -> a.id }) { _, attachment ->
                        AttachmentRow(attachment = attachment, onDelete = { vm.deleteAttachment(attachment) }, context = context)
                    }
                }
                item { Spacer(Modifier.height(focusMode .let{ if (it) 120.dp else 72.dp})) }
            }

            if (!focusMode) {
                EditorBottomBar(
                    onInsert = { insertOpen = true },
                    onUndo = ::undo,
                    onRedo = ::redo,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = redoStack.isNotEmpty()
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { focusMode = false }) {
                        Text(stringResource(R.string.notes_editor_focus_mode))
                    }
                }
            }
        }
    }

    if (insertOpen) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { insertOpen = false }, sheetState = sheetState) {
            InsertSheetContent(
                onPick = { type ->
                    insertOpen = false
                    when (type) {
                        NoteBlockType.IMAGE -> {
                            if (currentId > 0L) pickImage.launch(arrayOf("image/*"))
                            else saveCurrent { pickImage.launch(arrayOf("image/*")) }
                        }
                        else -> addBlock(type)
                    }
                },
                onAttachFile = { insertOpen = false; attachFile() }
            )
        }
    }

    if (infoOpen && existing != null) {
        InfoDialog(note = existing, onDismiss = { infoOpen = false })
    }
}

@Composable
private fun EditorOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    note: NoteEntity?,
    vm: OrdiaViewModel,
    onInfo: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.notes_pin)) },
            leadingIcon = { Icon(Icons.Outlined.PushPin, null) },
            onClick = { note?.let { vm.togglePin(it) } }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.notes_favorite)) },
            leadingIcon = { Icon(Icons.Outlined.StarBorder, null) },
            onClick = { note?.let { vm.toggleFavorite(it) } }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.notes_lock)) },
            leadingIcon = { Icon(Icons.Outlined.Lock, null) },
            onClick = { note?.let { vm.setNoteLocked(it.id, !it.locked) } }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.notes_duplicate)) },
            leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
            onClick = { note?.let { vm.duplicateNote(it) } }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.notes_share)) },
            leadingIcon = { Icon(Icons.Outlined.Share, null) },
            onClick = {
                note?.let { n ->
                    val ctx = (vm as Any).let { android.app.Application() }
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, n.title + "\n\n" + n.body)
                    }
                    val intent = Intent.createChooser(send, null)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    runCatching { ctx.startActivity(intent) }
                }
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.notes_info)) },
            leadingIcon = { Icon(Icons.Outlined.Info, null) },
            onClick = onInfo
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.notes_delete)) },
            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) },
            onClick = onDelete
        )
    }
}

@Composable
private fun EditorBottomBar(
    onInsert: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean
) {
    Surface(
        tonalElevation = 0.dp,
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onInsert) { Icon(Icons.Outlined.Add, stringResource(R.string.notes_editor_insert)) }
            IconButton(onClick = onUndo, enabled = canUndo) { Icon(Icons.AutoMirrored.Outlined.Undo, "Undo") }
            IconButton(onClick = onRedo, enabled = canRedo) { Icon(Icons.AutoMirrored.Outlined.Redo, "Redo") }
            Spacer(Modifier.width(8.dp))
            Text(
                "Aa ☑ •••",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InsertSheetContent(
    onPick: (NoteBlockType) -> Unit,
    onAttachFile: () -> Unit
) {
    val unavailable = stringResource(R.string.notes_editor_feature_unavailable)
    LazyColumn(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { InsertSection("Texto") }
        item { InsertItem(stringResource(R.string.notes_editor_insert_text), Icons.Outlined.TextFields) { onPick(NoteBlockType.PARAGRAPH) } }
        item { InsertItem(stringResource(R.string.notes_editor_insert_heading), Icons.Outlined.Title) { onPick(NoteBlockType.HEADING) } }
        item { InsertItem(stringResource(R.string.notes_editor_insert_quote), Icons.Outlined.FormatQuote) { onPick(NoteBlockType.QUOTE) } }
        item { InsertItem(stringResource(R.string.notes_editor_insert_code), Icons.Outlined.TextFields) { onPick(NoteBlockType.CODE) } }
        item { InsertItem(stringResource(R.string.notes_editor_insert_divider), Icons.Outlined.HorizontalRule) { onPick(NoteBlockType.DIVIDER) } }
        item { InsertSection("Listas") }
        item { InsertItem(stringResource(R.string.notes_editor_insert_bullets), Icons.AutoMirrored.Outlined.FormatListBulleted) { onPick(NoteBlockType.BULLET) } }
        item { InsertItem(stringResource(R.string.notes_editor_insert_numbered), Icons.AutoMirrored.Outlined.FormatListBulleted) { onPick(NoteBlockType.NUMBERED) } }
        item { InsertItem(stringResource(R.string.notes_editor_insert_checklist), Icons.Outlined.CheckBox) { onPick(NoteBlockType.CHECKLIST) } }
        item { InsertSection("Contenido") }
        item { InsertItem(stringResource(R.string.notes_editor_insert_table), Icons.Outlined.Add) { onPick(NoteBlockType.TABLE) } }
        item { InsertItem(stringResource(R.string.notes_editor_insert_image), Icons.Outlined.Image) { onPick(NoteBlockType.IMAGE) } }
        item { InsertItem(stringResource(R.string.notes_editor_insert_file), Icons.Outlined.Add) { onAttachFile() } }
        item { InsertItem(stringResource(R.string.notes_editor_insert_link), Icons.Outlined.Link) { onPick(NoteBlockType.LINK) } }
        item { InsertSection("Avanzado (próximamente)") }
        item { InsertItem(stringResource(R.string.notes_editor_insert_camera), Icons.Outlined.Image) { /* honest: no implementado */ } }
        item { InsertItem(stringResource(R.string.notes_editor_insert_scanner), Icons.Outlined.Image) { /* honest */ } }
        item { InsertItem(stringResource(R.string.notes_editor_insert_audio), Icons.Outlined.Add) { /* honest */ } }
        item { InsertItem(stringResource(R.string.notes_editor_insert_drawing), Icons.Outlined.TextFields) { /* honest */ } }
        item { InsertItem(stringResource(R.string.notes_editor_insert_handwriting), Icons.Outlined.TextFields) { /* honest */ } }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun InsertSection(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun InsertItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp).clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(label)
        }
    }
}

@Composable
private fun BlockRow(
    index: Int,
    block: NoteBlock,
    onChange: (NoteBlock) -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleBold: () -> Unit,
    onToggleItalic: () -> Unit,
    onToggleUnderline: () -> Unit,
    onToggleStrike: () -> Unit,
    onToggleHighlight: () -> Unit,
    onClearFormat: () -> Unit
) {
    when (block.type) {
        NoteBlockType.DIVIDER -> {
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        NoteBlockType.IMAGE -> {
            Column(Modifier.fillMaxWidth()) {
                block.attachmentUri.takeIf { it.isNotBlank() }?.let { uriStr ->
                    val ctx = LocalContext.current
                    val bitmap = remember(uriStr) {
                        runCatching {
                            android.graphics.ImageDecoder.decodeBitmap(
                                android.graphics.ImageDecoder.createSource(ctx.contentResolver, android.net.Uri.parse(uriStr))
                            ) { decoder, _, _ -> decoder.setMutableRequired(false) }
                        }.getOrNull()
                    }
                    bitmap?.let {
                        androidx.compose.foundation.Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = block.attachmentName,
                            modifier = Modifier.fillMaxWidth().heightInMax(280.dp)
                        )
                    } ?: Text("🖼 ${block.attachmentName}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        NoteBlockType.TABLE -> {
            TableBlockView(block = block, onChange = onChange)
        }
        else -> {
            val isChecklist = block.type == NoteBlockType.CHECKLIST
            val isList = block.type == NoteBlockType.BULLET || block.type == NoteBlockType.NUMBERED
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                if (isChecklist) {
                    Checkbox(block.checked, { onChange(block.copy(checked = it)) }, modifier = Modifier.size(24.dp))
                } else if (block.type == NoteBlockType.BULLET) {
                    Text("•", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 6.dp, top = 8.dp))
                } else if (block.type == NoteBlockType.NUMBERED) {
                    Text("${index + 1}.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 6.dp, top = 8.dp))
                }
                OutlinedTextField(
                    value = block.text,
                    onValueChange = { onChange(block.copy(text = it)) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(placeholderFor(block.type), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    minLines = 1,
                    maxLines = Int.MAX_VALUE,
                    textStyle = textStyleFor(block.type)
                )
            }
            // Barra de formato discreta visible solo en bloques de texto.
            if (block.type in textLikeTypes) {
                FormatBar(
                    onBold = onToggleBold,
                    onItalic = onToggleItalic,
                    onUnderline = onToggleUnderline,
                    onStrike = onToggleStrike,
                    onHighlight = onToggleHighlight,
                    onClear = onClearFormat,
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown,
                    onDelete = onDelete,
                    canMoveUp = index > 0
                )
            }
        }
    }
}

@Composable
private fun FormatBar(
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onUnderline: () -> Unit,
    onStrike: () -> Unit,
    onHighlight: () -> Unit,
    onClear: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    canMoveUp: Boolean
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 28.dp, top = 2.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        IconButton(onClick = onBold, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.FormatBold, "B", modifier = Modifier.size(16.dp)) }
        IconButton(onClick = onItalic, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.FormatItalic, "I", modifier = Modifier.size(16.dp)) }
        IconButton(onClick = onUnderline, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.FormatUnderlined, "U", modifier = Modifier.size(16.dp)) }
        IconButton(onClick = onStrike, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.FormatStrikethrough, "S", modifier = Modifier.size(16.dp)) }
        IconButton(onClick = onHighlight, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.FormatPaint, "H", modifier = Modifier.size(16.dp)) }
        IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.FormatClear, "C", modifier = Modifier.size(16.dp)) }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Outlined.MoreVert, null, modifier = Modifier.size(14.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.note_editor_delete_block), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun TableBlockView(block: NoteBlock, onChange: (NoteBlock) -> Unit) {
    var rows = block.tableRows
    if (rows.isEmpty()) rows = listOf(List(2) { "" }, List(2) { "" })
    Column(Modifier.fillMaxWidth()) {
        rows.forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEachIndexed { c, cell ->
                    OutlinedTextField(
                        value = cell,
                        onValueChange = { newCell ->
                            val newRows: List<List<String>> = rows.mapIndexed { ri, row ->
                                if (ri == r) row.mapIndexed { ci, cell -> if (ci == c) newCell else cell }
                                else row
                            }
                            onChange(block.copy(tableRows = newRows))
                        },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        singleLine = true
                    )
                }
            }
        }
        Row {
            TextButton(onClick = {
                val cols = rows.firstOrNull()?.size ?: 2
                val newRow: List<String> = List(cols) { "" }
                val newRows: List<List<String>> = rows.toMutableList().apply { add(newRow) }.toList()
                onChange(block.copy(tableRows = newRows))
            }) { Text("+ fila") }
            TextButton(onClick = {
                val newRows: List<List<String>> = rows.map { it + "" }
                onChange(block.copy(tableRows = newRows))
            }) { Text("+ columna") }
        }
    }
}

@Composable
private fun AttachmentRow(
    attachment: AttachmentEntity,
    onDelete: () -> Unit,
    context: android.content.Context
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("📎 ", style = MaterialTheme.typography.bodyMedium)
        Column(Modifier.weight(1f)) {
            Text(attachment.displayName, style = MaterialTheme.typography.bodyMedium)
            Text(formatBytes(context, attachment.sizeBytes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = {
            val uri = android.net.Uri.parse(attachment.uri)
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, attachment.mimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runCatching { context.startActivity(intent) }
        }) { Text("Abrir") }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.note_editor_remove_attachment, attachment.displayName))
        }
    }
}

@Composable
private fun InfoDialog(note: NoteEntity, onDismiss: () -> Unit) {
    val created = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(note.createdAt))
    val modified = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(note.updatedAt))
    val words = note.body.split(Regex("\\s+")).filter { it.isNotBlank() }.size
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        title = { Text(stringResource(R.string.notes_info)) },
        text = {
            Column {
                Text(stringResource(R.string.notes_info_created, created))
                Text(stringResource(R.string.notes_info_modified, modified))
                Text(stringResource(R.string.notes_info_words, words))
                Text(stringResource(R.string.notes_info_chars, note.body.length))
            }
        }
    )
}

private val textLikeTypes = setOf(
    NoteBlockType.PARAGRAPH, NoteBlockType.HEADING, NoteBlockType.HEADING_2,
    NoteBlockType.HEADING_3, NoteBlockType.SUBTITLE, NoteBlockType.QUOTE,
    NoteBlockType.BULLET, NoteBlockType.NUMBERED, NoteBlockType.CHECKLIST, NoteBlockType.CODE
)

@Composable
private fun placeholderFor(type: NoteBlockType): String = when (type) {
    NoteBlockType.HEADING, NoteBlockType.HEADING_2, NoteBlockType.HEADING_3, NoteBlockType.SUBTITLE -> "Encabezado"
    NoteBlockType.QUOTE -> "Cita…"
    NoteBlockType.CODE -> "código…"
    NoteBlockType.CHECKLIST -> "Elemento…"
    NoteBlockType.BULLET, NoteBlockType.NUMBERED -> "Lista…"
    else -> "Escribe…"
}

@Composable
private fun textStyleFor(type: NoteBlockType): androidx.compose.ui.text.TextStyle = when (type) {
    NoteBlockType.HEADING -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
    NoteBlockType.HEADING_2 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
    NoteBlockType.HEADING_3, NoteBlockType.SUBTITLE -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
    NoteBlockType.QUOTE -> MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic)
    NoteBlockType.CODE -> MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    else -> MaterialTheme.typography.bodyLarge
}

private fun formatBytes(context: android.content.Context, bytes: Long): String = when {
    bytes <= 0L -> context.getString(R.string.note_editor_size_unknown)
    bytes < 1_024L -> context.getString(R.string.note_editor_size_b, bytes)
    bytes < 1_048_576L -> context.getString(R.string.note_editor_size_kb, bytes / 1_024)
    else -> context.getString(R.string.note_editor_size_mb, "%.1f".format(bytes / 1_048_576.0))
}

private fun Modifier.heightInMax(max: androidx.compose.ui.unit.Dp): Modifier =
    this.then(Modifier.heightIn(max = max))
