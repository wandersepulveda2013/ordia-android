package com.ordia.app.ui.screens

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.AlertDialog
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
import com.ordia.app.media.NoteImageLoader
import com.ordia.app.media.NoteMediaStore
import com.ordia.app.domain.NoteBlockType
import com.ordia.app.domain.NoteSpan
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.OrdiaViewModel
import kotlinx.coroutines.launch

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
    onTask: (Long) -> Unit,
    onOpenNote: (Long) -> Unit
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

    // Captura una versión de apertura (snapshot inicial) para el historial,
    // una sola vez por carga de nota y solo si la nota ya tiene contenido.
    androidx.compose.runtime.LaunchedEffect(noteId) {
        val initial = state.note(noteId)
        if (initial != null && (initial.title.isNotBlank() || initial.body.isNotBlank())) {
            // Evita duplicar snapshots consecutivos idénticos.
            val versions = vm.noteVersions(noteId)
            val latest = versions.maxByOrNull { it.createdAt }
            val same = latest != null &&
                latest.title == initial.title &&
                latest.blocksData == initial.blocksData &&
                latest.body == initial.body
            if (!same) {
                vm.captureNoteVersion(initial, NoteBlockCodec.decode(initial.blocksData, initial.body))
            }
        }
    }

    var dirty by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    var insertOpen by remember { mutableStateOf(false) }
    var infoOpen by remember { mutableStateOf(false) }
    var historyOpen by remember { mutableStateOf(false) }
    var focusMode by remember { mutableStateOf(false) }
    var findOpen by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var findIndex by remember { mutableStateOf(0) }
    var scannerOpen by remember { mutableStateOf(false) }
    var ocrOpen by remember { mutableStateOf(false) }
    var audioOpen by remember { mutableStateOf(false) }
    var drawingOpen by remember { mutableStateOf(false) }
    var linkNoteOpen by remember { mutableStateOf(false) }

    val findMatches = remember(title, blocks, findQuery) {
        if (findQuery.isBlank()) emptyList()
        else {
            val q = findQuery.lowercase()
            val out = ArrayList<Pair<Int, Int>>()
            if (title.lowercase().contains(q)) out.add(-1 to title.lowercase().indexOf(q))
            blocks.forEachIndexed { i, b ->
                val t = b.plainText.lowercase()
                var from = 0
                while (true) {
                    val pos = t.indexOf(q, from)
                    if (pos < 0) break
                    out.add(i to pos)
                    from = pos + q.length
                }
            }
            out
        }
    }

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
            // Importa a almacenamiento privado: la nota no depende de que el
            // documento original siga accesible (persiste tras cerrar/reabrir).
            val path = NoteMediaStore.importImage(context, uri)
            val displayName = NoteMediaStore.queryDisplayName(context.contentResolver, uri) ?: "imagen"
            if (path != null) {
                blocks.add(
                    NoteBlock(
                        type = NoteBlockType.IMAGE,
                        attachmentUri = path,
                        attachmentName = displayName,
                        mimeType = context.contentResolver.getType(uri) ?: "image/*"
                    )
                )
                dirty = true
            }
        }
    }

    // --- Galería multi-imagen (FASE 25) ---
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            val path = NoteMediaStore.importImage(context, uri)
            val displayName = NoteMediaStore.queryDisplayName(context.contentResolver, uri) ?: "imagen"
            if (path != null) {
                blocks.add(
                    NoteBlock(
                        type = NoteBlockType.IMAGE,
                        attachmentUri = path,
                        attachmentName = displayName,
                        mimeType = context.contentResolver.getType(uri) ?: "image/*"
                    )
                )
            }
        }
        if (uris.isNotEmpty()) dirty = true
    }

    // --- Cámara real ---
    // URI temporal (FileProvider) donde la app de cámara escribe la foto.
    var cameraOutUri by remember { mutableStateOf<android.net.Uri?>(null) }

    fun hasMeaningfulContent(): Boolean =
        title.isNotBlank() || blocks.any { it.text.isNotBlank() || it.attachmentUri.isNotBlank() || it.type == NoteBlockType.DIVIDER }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraOutUri
        if (saved && uri != null) {
            // Importa la captura a almacenamiento privado y normaliza orientación.
            val path = NoteMediaStore.importImage(context, uri)
            if (path != null) {
                blocks.add(
                    NoteBlock(
                        type = NoteBlockType.IMAGE,
                        attachmentUri = path,
                        attachmentName = "Foto ${java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())}",
                        mimeType = "image/jpeg"
                    )
                )
                dirty = true
            }
            // Limpia el archivo temporal de cámara si es distinto del importado.
            runCatching {
                val tmpFile = java.io.File(uri.path ?: "")
                if (tmpFile.exists() && tmpFile.absolutePath != path) tmpFile.delete()
            }
        }
        cameraOutUri = null
    }

    fun launchCameraInternal() {
        val dir = java.io.File(context.filesDir, "notes-media").apply { mkdirs() }
        val tmp = java.io.File(dir, "cam-${System.currentTimeMillis()}.jpg")
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.update-files",
            tmp
        )
        cameraOutUri = uri
        takePicture.launch(uri)
    }

    val requestCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCameraInternal()
    }

    fun launchCamera() {
        if (currentId <= 0L && !hasMeaningfulContent()) {
            saveCurrent { launchCameraInternal() }
        } else {
            launchCameraInternal()
        }
    }

    // --- Escáner y OCR: comparten un picker de imagen ---
    var scannerSourceUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var ocrSourceUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val pickScanImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scannerSourceUri = uri
            scannerOpen = true
        }
    }
    val pickOcrImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            ocrSourceUri = uri
            ocrOpen = true
        }
    }

    fun launchScanner() {
        if (currentId <= 0L && !hasMeaningfulContent()) saveCurrent { pickScanImage.launch(arrayOf("image/*")) }
        else pickScanImage.launch(arrayOf("image/*"))
    }
    fun launchOcr() {
        if (currentId <= 0L && !hasMeaningfulContent()) saveCurrent { pickOcrImage.launch(arrayOf("image/*")) }
        else pickOcrImage.launch(arrayOf("image/*"))
    }

    val requestAudio = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) audioOpen = true
    }
    fun launchAudio() {
        // RECORD_AUDIO solo está declarado en previewAdvanced/previewFull.
        requestAudio.launch(android.Manifest.permission.RECORD_AUDIO)
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && currentId > 0L) {
            val path = NoteMediaStore.importStream(context, uri)
            if (path != null) {
                var displayName = NoteMediaStore.queryDisplayName(context.contentResolver, uri) ?: defaultAttachmentName
                var sizeBytes = 0L
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex >= 0) cursor.getString(nameIndex)?.let { displayName = it }
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) sizeBytes = cursor.getLong(sizeIndex)
                    }
                }
                val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val finalSize = if (sizeBytes > 0) sizeBytes else java.io.File(path).length()
                vm.addAttachment(
                    AttachmentEntity(
                        ownerType = AttachmentOwnerType.NOTE,
                        ownerId = currentId,
                        uri = path,
                        displayName = displayName,
                        mimeType = mime,
                        sizeBytes = finalSize
                    )
                )
            }
        }
    }

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
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                    val ctrl = ev.isCtrlPressed
                    val k = ev.key
                    when {
                        ctrl && k == Key.B -> {
                            val idx = blocks.indexOfLast { it.type == NoteBlockType.PARAGRAPH || it.type == NoteBlockType.HEADING || it.type == NoteBlockType.HEADING_2 || it.type == NoteBlockType.HEADING_3 || it.type == NoteBlockType.SUBTITLE }
                            if (idx >= 0) toggleSpanFormat(idx) { s -> s.copy(bold = !s.bold) }
                            true
                        }
                        ctrl && k == Key.I -> {
                            val idx = blocks.indexOfLast { it.type == NoteBlockType.PARAGRAPH || it.type == NoteBlockType.HEADING || it.type == NoteBlockType.HEADING_2 || it.type == NoteBlockType.HEADING_3 || it.type == NoteBlockType.SUBTITLE }
                            if (idx >= 0) toggleSpanFormat(idx) { s -> s.copy(italic = !s.italic) }
                            true
                        }
                        ctrl && k == Key.U -> {
                            val idx = blocks.indexOfLast { it.type == NoteBlockType.PARAGRAPH || it.type == NoteBlockType.HEADING || it.type == NoteBlockType.HEADING_2 || it.type == NoteBlockType.HEADING_3 || it.type == NoteBlockType.SUBTITLE }
                            if (idx >= 0) toggleSpanFormat(idx) { s -> s.copy(underline = !s.underline) }
                            true
                        }
                        ctrl && k == Key.Z -> { undo(); true }
                        ctrl && ev.isShiftPressed && k == Key.Z -> { redo(); true }
                        ctrl && k == Key.Y -> { redo(); true }
                        ctrl && k == Key.F -> { findOpen = !findOpen; findQuery = ""; findIndex = 0; true }
                        else -> false
                    }
                }
        ) {
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
                        IconButton(onClick = { findOpen = !findOpen; findQuery = ""; findIndex = 0 }) {
                            Icon(Icons.Outlined.Search, stringResource(R.string.notes_search_hint))
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
                                blocks = blocks,
                                vm = vm,
                                context = context,
                                onInfo = { overflowOpen = false; infoOpen = true },
                                onHistory = { overflowOpen = false; historyOpen = true },
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

            if (findOpen) {
                FindBar(
                    query = findQuery,
                    onQuery = { findQuery = it; findIndex = 0 },
                    index = findIndex,
                    total = findMatches.size,
                    onPrev = { if (findMatches.isNotEmpty()) findIndex = (findIndex - 1).mod(findMatches.size) },
                    onNext = { if (findMatches.isNotEmpty()) findIndex = (findIndex + 1).mod(findMatches.size) },
                    onClose = { findOpen = false; findQuery = ""; findIndex = 0 }
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
                    if (block.type == NoteBlockType.CHECKLIST && (index == 0 || blocks[index - 1].type != NoteBlockType.CHECKLIST)) {
                        ChecklistGroupHeader(
                            blocks = blocks,
                            startIndex = index,
                            onMarkAll = { checked ->
                                val groupEnd = checklistGroupEnd(blocks, index)
                                for (i in index..groupEnd) {
                                    updateBlock(i, blocks[i].copy(checked = checked))
                                }
                            }
                        )
                    }
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
                        onClearFormat = { clearFormat(index) },
                        onOpenNote = onOpenNote,
                        onSlash = { insertOpen = true }
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
                onAttachFile = { insertOpen = false; attachFile() },
                onCamera = { insertOpen = false; launchCamera() },
                onScanner = { insertOpen = false; launchScanner() },
                onOcr = { insertOpen = false; launchOcr() },
                onAudio = { insertOpen = false; launchAudio() },
                onDrawing = { insertOpen = false; drawingOpen = true },
                onHandwriting = { insertOpen = false; drawingOpen = true },
                onLinkNote = { insertOpen = false; linkNoteOpen = true },
                onGallery = {
                    insertOpen = false
                    if (currentId > 0L) pickImages.launch(arrayOf("image/*"))
                    else saveCurrent { pickImages.launch(arrayOf("image/*")) }
                }
            )
        }
    }

    if (infoOpen && existing != null) {
        InfoDialog(
            note = existing,
            blocks = blocks,
            vm = vm,
            onOpenNote = onOpenNote,
            onDismiss = { infoOpen = false }
        )
    }

    if (historyOpen && existing != null) {
        HistoryDialog(
            noteId = existing.id,
            vm = vm,
            onDismiss = { historyOpen = false },
            onRestored = {
                historyOpen = false
                onBack()
            }
        )
    }

    if (scannerOpen && scannerSourceUri != null) {
        ScannerDialog(
            context = context,
            sourceUri = scannerSourceUri!!,
            onDismiss = { scannerOpen = false; scannerSourceUri = null },
            onInsert = { path ->
                scannerOpen = false
                scannerSourceUri = null
                blocks.add(
                    NoteBlock(
                        type = NoteBlockType.IMAGE,
                        attachmentUri = path,
                        attachmentName = "Documento escaneado",
                        mimeType = "image/jpeg"
                    )
                )
                dirty = true
            }
        )
    }

    if (ocrOpen && ocrSourceUri != null) {
        OcrDialog(
            context = context,
            sourceUri = ocrSourceUri!!,
            onDismiss = { ocrOpen = false; ocrSourceUri = null },
            onInsertText = { text ->
                ocrOpen = false
                ocrSourceUri = null
                blocks.add(NoteBlock(type = NoteBlockType.PARAGRAPH, text = text))
                dirty = true
            }
        )
    }

    if (audioOpen) {
        AudioRecorderDialog(
            onDismiss = { audioOpen = false },
            onInsert = { path, _ ->
                audioOpen = false
                blocks.add(
                    NoteBlock(
                        type = NoteBlockType.AUDIO,
                        attachmentUri = path,
                        attachmentName = "Audio ${java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())}",
                        mimeType = "audio/mp4"
                    )
                )
                dirty = true
            }
        )
    }

    if (drawingOpen) {
        DrawingDialog(
            onDismiss = { drawingOpen = false },
            onInsert = { path ->
                drawingOpen = false
                blocks.add(
                    NoteBlock(
                        type = NoteBlockType.DRAWING,
                        attachmentUri = path,
                        attachmentName = "Dibujo ${java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())}",
                        mimeType = "image/png"
                    )
                )
                dirty = true
            }
        )
    }

    if (linkNoteOpen) {
        NoteLinkPickerDialog(
            vm = vm,
            excludeId = currentId,
            onDismiss = { linkNoteOpen = false },
            onPick = { targetId, targetTitle ->
                linkNoteOpen = false
                blocks.add(
                    NoteBlock(
                        type = NoteBlockType.LINK,
                        attachmentUri = "note:$targetId",
                        linkTitle = targetTitle
                    )
                )
                dirty = true
            }
        )
    }
}

@Composable
private fun EditorOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    note: NoteEntity?,
    blocks: List<NoteBlock>,
    vm: OrdiaViewModel,
    context: android.content.Context,
    onInfo: () -> Unit,
    onHistory: () -> Unit,
    onDelete: () -> Unit
) {
    var colorPickerOpen by remember { mutableStateOf(false) }
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
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, n.title)
                        putExtra(Intent.EXTRA_TEXT, buildString {
                            if (n.title.isNotBlank()) append(n.title).append("\n\n")
                            append(NoteBlockCodec.toPlainText(blocks))
                        })
                    }
                    val chooser = Intent.createChooser(send, null)
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(chooser) }
                }
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.notes_export_markdown)) },
            leadingIcon = { Icon(Icons.Outlined.Description, null) },
            onClick = {
                note?.let { n ->
                    val md = buildString {
                        if (n.title.isNotBlank()) append("# ").append(n.title).append("\n\n")
                        append(NoteBlockCodec.toMarkdown(blocks))
                    }
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/markdown"
                        putExtra(Intent.EXTRA_SUBJECT, n.title.ifBlank { "nota" })
                        putExtra(Intent.EXTRA_TEXT, md)
                    }
                    runCatching {
                        context.startActivity(Intent.createChooser(send, null).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
                }
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.notes_export_html)) },
            leadingIcon = { Icon(Icons.Outlined.Code, null) },
            onClick = {
                note?.let { n ->
                    val html = NoteBlockCodec.toHtml(blocks, n.title)
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/html"
                        putExtra(Intent.EXTRA_SUBJECT, n.title.ifBlank { "nota" })
                        putExtra(Intent.EXTRA_TEXT, html)
                    }
                    runCatching {
                        context.startActivity(Intent.createChooser(send, null).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
                }
            }
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.notes_info)) },
            leadingIcon = { Icon(Icons.Outlined.Info, null) },
            onClick = onInfo
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.notes_history)) },
            leadingIcon = { Icon(Icons.Outlined.History, null) },
            onClick = onHistory
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.notes_color)) },
            leadingIcon = { Icon(Icons.Outlined.Palette, null) },
            onClick = { colorPickerOpen = true }
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.notes_delete)) },
            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) },
            onClick = onDelete
        )
    }
    if (colorPickerOpen) {
        NoteColorPickerDialog(
            current = note?.colorHex ?: "",
            onPick = { hex ->
                note?.let { vm.setNoteColor(it.id, hex) }
                colorPickerOpen = false
            },
            onDismiss = { colorPickerOpen = false }
        )
    }
}

private val NOTE_COLOR_PALETTE = listOf(
    "" to "Ninguno",
    "#FBF3E2" to "Arena",
    "#F3E9F7" to "Lila",
    "#E7F1F6" to "Cielo",
    "#EAF3E8" to "Menta",
    "#FCE9E4" to "Coral",
    "#F7F0E0" to "Vainilla",
    "#EDEDED" to "Grafito claro"
)

@Composable
private fun NoteColorPickerDialog(
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text(stringResource(R.string.notes_color)) },
        text = {
            Column {
                NOTE_COLOR_PALETTE.forEach { (hex, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(hex) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(
                                    if (hex.isEmpty()) MaterialTheme.colorScheme.surfaceVariant
                                    else androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex)),
                                    androidx.compose.foundation.shape.CircleShape
                                )
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline,
                                    androidx.compose.foundation.shape.CircleShape
                                )
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            label + if (hex == current) "  ✓" else "",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    )
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
    onAttachFile: () -> Unit,
    onCamera: () -> Unit,
    onScanner: () -> Unit,
    onOcr: () -> Unit,
    onAudio: () -> Unit,
    onDrawing: () -> Unit,
    onHandwriting: () -> Unit,
    onLinkNote: () -> Unit,
    onGallery: () -> Unit
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
        item { InsertItem(stringResource(R.string.notes_editor_insert_gallery), Icons.Outlined.PhotoLibrary) { onGallery() } }
        item { InsertItem(stringResource(R.string.notes_editor_insert_file), Icons.Outlined.Add) { onAttachFile() } }
        item { InsertItem(stringResource(R.string.notes_editor_insert_link), Icons.Outlined.Link) { onPick(NoteBlockType.LINK) } }
        item { InsertItem(stringResource(R.string.notes_editor_insert_link_note), Icons.Outlined.Link) { onLinkNote() } }
        item { InsertItem(stringResource(R.string.notes_editor_insert_camera), Icons.Outlined.PhotoCamera) { onCamera() } }
        item { InsertItem(stringResource(R.string.notes_editor_insert_scanner), Icons.Outlined.DocumentScanner) { onScanner() } }
        item { InsertItem(stringResource(R.string.notes_editor_insert_ocr), Icons.Outlined.TextFields) { onOcr() } }
        item { InsertSection("Próximamente") }
        item { InsertItem(stringResource(R.string.notes_editor_insert_audio), Icons.Outlined.Mic) { onAudio() } }
        item { InsertItem(stringResource(R.string.notes_editor_insert_drawing), Icons.Outlined.Brush) { onDrawing() } }
        item { InsertItem(stringResource(R.string.notes_editor_insert_handwriting), Icons.Outlined.Edit) { onHandwriting() } }
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
    onClearFormat: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onSlash: () -> Unit
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
                            NoteImageLoader.load(ctx, uriStr)
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
        NoteBlockType.AUDIO -> {
            block.attachmentUri.takeIf { it.isNotBlank() }?.let { path ->
                AudioBlockView(name = block.attachmentName, path = path, modifier = Modifier.fillMaxWidth())
            }
        }
        NoteBlockType.DRAWING, NoteBlockType.HANDWRITING -> {
            Column(Modifier.fillMaxWidth()) {
                block.attachmentUri.takeIf { it.isNotBlank() }?.let { uriStr ->
                    val ctx = LocalContext.current
                    val bitmap = remember(uriStr) {
                        runCatching { NoteImageLoader.load(ctx, uriStr) }.getOrNull()
                    }
                    bitmap?.let {
                        androidx.compose.foundation.Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = block.attachmentName,
                            modifier = Modifier.fillMaxWidth().heightInMax(360.dp)
                        )
                    } ?: Text("✏️ ${block.attachmentName}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        NoteBlockType.TABLE -> {
            TableBlockView(block = block, onChange = onChange)
        }
        NoteBlockType.LINK -> {
            val uri = block.attachmentUri
            val isInternal = uri.startsWith("note:")
            Surface(
                onClick = {
                    val targetId = uri.removePrefix("note:").toLongOrNull()
                    if (isInternal && targetId != null) onOpenNote(targetId)
                },
                enabled = isInternal,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        block.linkTitle.ifBlank { uri },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isInternal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
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
                    onValueChange = { newText ->
                        // Slash command: si el bloque estaba vacío y el usuario escribe '/',
                        // abrir el panel Insertar (atajo avanzado, no obligatorio).
                        if (block.text.isEmpty() && newText == "/") {
                            onSlash()
                        } else {
                            onChange(block.copy(text = newText))
                        }
                    },
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
    val context = LocalContext.current
    var rows = block.tableRows
    if (rows.isEmpty()) rows = listOf(List(2) { "" }, List(2) { "" })
    val headerRow = block.tableHeader

    Column(Modifier.fillMaxWidth()) {
        rows.forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEachIndexed { c, cell ->
                    OutlinedTextField(
                        value = cell,
                        onValueChange = { newCell ->
                            val newRows: List<List<String>> = rows.mapIndexed { ri, rr ->
                                if (ri == r) rr.mapIndexed { ci, _ -> if (ci == c) newCell else rr[ci] }
                                else rr
                            }
                            onChange(block.copy(tableRows = newRows))
                        },
                        modifier = Modifier.weight(1f),
                        textStyle = if (headerRow && r == 0) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                        singleLine = true
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = {
                val cols = rows.firstOrNull()?.size ?: 2
                onChange(block.copy(tableRows = rows + List(1) { List(cols) { "" } }))
            }) { Text(stringResource(R.string.notes_table_add_row)) }
            TextButton(onClick = {
                onChange(block.copy(tableRows = rows.map { it + "" }))
            }) { Text(stringResource(R.string.notes_table_add_col)) }
            TextButton(onClick = {
                if (rows.size > 1) onChange(block.copy(tableRows = rows.dropLast(1)))
            }) { Text(stringResource(R.string.notes_table_del_row)) }
            TextButton(onClick = {
                if ((rows.firstOrNull()?.size ?: 0) > 1) onChange(block.copy(tableRows = rows.map { it.dropLast(1) }))
            }) { Text(stringResource(R.string.notes_table_del_col)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { onChange(block.copy(tableHeader = !headerRow)) }) {
                Text(if (headerRow) stringResource(R.string.notes_table_header_off) else stringResource(R.string.notes_table_header_on))
            }
            TextButton(onClick = {
                val csv = rows.joinToString("\n") { row -> row.joinToString(",") { escapeCsv(it) } }
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_TEXT, csv)
                }
                runCatching { context.startActivity(Intent.createChooser(intent, "CSV")) }
            }) { Text(stringResource(R.string.notes_table_export_csv)) }
        }
    }
}

private fun escapeCsv(field: String): String {
    return if (field.contains(',') || field.contains('"') || field.contains('\n')) {
        "\"" + field.replace("\"", "\"\"") + "\""
    } else field
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
private fun InfoDialog(
    note: NoteEntity,
    blocks: List<NoteBlock>,
    vm: OrdiaViewModel,
    onOpenNote: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val created = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(note.createdAt))
    val modified = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(note.updatedAt))
    val fullText = remember(blocks) { blocks.joinToString("\n") { it.plainText } + "\n" + note.title }
    val words = remember(fullText) { fullText.split(Regex("\\s+")).filter { it.isNotBlank() }.size }
    val chars = fullText.length
    val attachments = remember(blocks) { blocks.count { it.attachmentUri.isNotBlank() } }

    var backlinks by remember(note.id) { mutableStateOf<List<NoteEntity>>(emptyList()) }
    LaunchedEffect(note.id) {
        backlinks = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { vm.backlinksTo(note.id) }
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        title = { Text(stringResource(R.string.notes_info)) },
        text = {
            Column {
                Text(stringResource(R.string.notes_info_created, created))
                Text(stringResource(R.string.notes_info_modified, modified))
                Text(stringResource(R.string.notes_info_words, words))
                Text(stringResource(R.string.notes_info_chars, chars))
                Text(stringResource(R.string.notes_info_attachments, attachments))
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.notes_info_backlinks, backlinks.size),
                    style = MaterialTheme.typography.bodyMedium
                )
                backlinks.take(5).forEach { bl ->
                    Text(
                        "• ${bl.title.ifBlank { bl.body.take(40) }}",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(start = 8.dp, top = 2.dp)
                            .clickable { onOpenNote(bl.id) }
                    )
                }
            }
        }
    )
}

@Composable
private fun FindBar(
    query: String,
    onQuery: (String) -> Unit,
    index: Int,
    total: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit
) {
    Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.notes_find_hint), fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp)
            )
            Text(
                if (total > 0) "${index + 1}/$total" else "0/0",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            IconButton(onClick = onPrev, enabled = total > 0) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, null)
            }
            IconButton(onClick = onNext, enabled = total > 0) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, null)
            }
        }
    }
}

@Composable
private fun HistoryDialog(
    noteId: Long,
    vm: OrdiaViewModel,
    onDismiss: () -> Unit,
    onRestored: () -> Unit
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var versions by remember { mutableStateOf<List<com.ordia.app.data.local.NoteVersionEntity>>(emptyList()) }
    androidx.compose.runtime.LaunchedEffect(noteId) {
        versions = vm.noteVersions(noteId)
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.notes_history_close)) } },
        title = { Text(stringResource(R.string.notes_history)) },
        text = {
            if (versions.isEmpty()) {
                Text(stringResource(R.string.notes_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    versions.sortedByDescending { it.createdAt }.forEach { v ->
                        val date = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(v.createdAt))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(date, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = {
                                scope.launch {
                                    vm.restoreNoteVersion(v) { onRestored() }
                                }
                            }) { Text(stringResource(R.string.notes_history_restore)) }
                        }
                    }
                }
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

/** Índice del último bloque de un grupo contiguo de checklists que empieza en [startIndex]. */
private fun checklistGroupEnd(blocks: List<NoteBlock>, startIndex: Int): Int {
    var i = startIndex
    while (i + 1 < blocks.size && blocks[i + 1].type == NoteBlockType.CHECKLIST) i++
    return i
}

@Composable
private fun ChecklistGroupHeader(
    blocks: List<NoteBlock>,
    startIndex: Int,
    onMarkAll: (Boolean) -> Unit
) {
    val end = checklistGroupEnd(blocks, startIndex)
    val group = blocks.subList(startIndex, end + 1)
    val done = group.count { it.checked }
    val total = group.size
    val allChecked = done == total && total > 0
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "$done / $total",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(
            onClick = { onMarkAll(!allChecked) },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            Text(
                if (allChecked) stringResource(R.string.notes_checklist_uncheck_all) else stringResource(R.string.notes_checklist_check_all),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
