package com.ordia.app.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ordia.app.R
import com.ordia.app.data.local.NoteEntity
import com.ordia.app.domain.NoteBlockCodec
import com.ordia.app.ui.OrdiaUiState
import com.ordia.app.ui.OrdiaViewModel
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Filtros rápidos disponibles en la home de notas. */
enum class NoteFilter(val labelRes: Int) {
    NONE(0),
    FAVORITES(R.string.notes_filter_favorites),
    PINNED(R.string.notes_filter_pinned),
    IMAGES(R.string.notes_filter_images),
    AUDIO(R.string.notes_filter_audio),
    CHECKLIST(R.string.notes_filter_checklist),
    LOCKED(R.string.notes_filter_locked)
}

enum class NoteViewMode { CARDS, LIST, COMPACT, GALLERY, TITLES }

enum class NoteSortMode(val labelRes: Int) {
    MODIFIED(R.string.notes_sort_modified),
    CREATED(R.string.notes_sort_created),
    OLDEST(R.string.notes_sort_oldest),
    ALPHA(R.string.notes_sort_alpha),
    PINNED(R.string.notes_sort_pinned),
    FAVORITE(R.string.notes_sort_favorite)
}

/**
 * Nueva home de ORDÍA NOTES: notas, y solo notas.
 *
 * Sin navegación inferior. La barra superior tiene ⋮ a la izquierda,
 * búsqueda y ＋ a la derecha. Las notas se organizan por vista/orden/filtro
 * discretos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesHomeScreen(
    state: OrdiaUiState,
    vm: OrdiaViewModel,
    padding: PaddingValues,
    onNote: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(NoteFilter.NONE) }
    var sort by remember { mutableStateOf(NoteSortMode.MODIFIED) }
    var view by remember { mutableStateOf(NoteViewMode.CARDS) }
    var menuExpanded by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<NoteEntity>?>(null) }

    fun performSearch(q: String) {
        query = q
        if (q.isBlank()) {
            searchResults = null
        } else {
            scope.launch { searchResults = vm.searchNotes(q) }
        }
    }

    val deleteWithUndo: (NoteEntity) -> Unit = { note ->
        vm.trashNote(note)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.notes_deleted_snackbar, note.title.ifBlank { context.getString(R.string.notes_deleted_snackbar_untitled) }),
                actionLabel = context.getString(R.string.notes_undo),
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                vm.restoreNoteFromTrash(note.id)
            }
        }
    }

    val activeList = searchResults ?: state.notes
    val pinnedNotes = activeList.filter { it.pinned }
    val regularNotes = activeList.filter { !it.pinned }
    val filteredRegular = applyFilter(regularNotes, filter)
    val filteredPinned = applyFilter(pinnedNotes, filter)
    val sortedRegular = applySort(filteredRegular, sort)
    val sortedPinned = applySort(filteredPinned, sort)

    val listToRender = if (filter != NoteFilter.NONE) sortedRegular else sortedRegular

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0)
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            Column(Modifier.fillMaxSize()) {
            // --- Barra superior: ⋮ (izq) | ORDÍA | 🔍 ＋ (der) ---
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.notes_home_title),
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp
                    )
                },
                navigationIcon = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Outlined.MoreVert, stringResource(R.string.notes_menu_overflow))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.notes_menu_settings)) },
                                leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                                onClick = { menuExpanded = false; onOpenSettings() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.notes_menu_trash)) },
                                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) },
                                onClick = { menuExpanded = false; onOpenTrash() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.notes_menu_import)) },
                                leadingIcon = { Icon(Icons.Outlined.Download, null) },
                                onClick = { menuExpanded = false; onOpenImport() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.notes_menu_export)) },
                                leadingIcon = { Icon(Icons.Outlined.Backup, null) },
                                onClick = { menuExpanded = false; onOpenExport() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.notes_menu_select)) },
                                leadingIcon = { Icon(Icons.Outlined.CheckBox, null) },
                                onClick = { menuExpanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.notes_menu_about)) },
                                leadingIcon = { Icon(Icons.Outlined.HelpOutline, null) },
                                onClick = { menuExpanded = false; onOpenAbout() }
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { /* enfoca búsqueda */ }) {
                        Icon(Icons.Outlined.Search, stringResource(R.string.notes_search_hint))
                    }
                    IconButton(onClick = {
                        vm.createBlankNote(onCreated = onNote)
                    }) {
                        Icon(Icons.Outlined.Add, stringResource(R.string.notes_create_new))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )

            // --- Búsqueda ---
            OutlinedTextField(
                value = query,
                onValueChange = ::performSearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text(stringResource(R.string.notes_search_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp)
            )

            // --- Controles discretos: filtros + vista/orden ---
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                NoteFilter.entries.filter { it != NoteFilter.NONE }.forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = if (filter == f) NoteFilter.NONE else f },
                        label = { Text(stringResource(f.labelRes), fontSize = 12.sp) }
                    )
                }
                ViewSortControl(view = view, sort = sort, onView = { view = it }, onSort = { sort = it })
            }

            // --- Contenido ---
            if (activeList.isEmpty() && query.isBlank()) {
                EmptyNotesState(onCreate = { vm.createBlankNote(onCreated = onNote) })
            } else if (activeList.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.notes_search_empty, query),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (sortedPinned.isNotEmpty() && filter == NoteFilter.NONE) {
                        item {
                            SectionHeader(stringResource(R.string.notes_section_pinned))
                        }
                        items(sortedPinned, key = { "p-${it.id}" }) { note ->
                            SwipeToDeleteNoteCard(note, view, onClick = { onNote(note.id) }, onDelete = { deleteWithUndo(note) })
                        }
                    }
                    item {
                        if (sortedPinned.isNotEmpty() && filter == NoteFilter.NONE) {
                            SectionHeader(stringResource(R.string.notes_section_all))
                        }
                    }
                    items(listToRender, key = { it.id }) { note ->
                        SwipeToDeleteNoteCard(note, view, onClick = { onNote(note.id) }, onDelete = { deleteWithUndo(note) })
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun EmptyNotesState(onCreate: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.notes_home_empty_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.notes_empty_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onCreate) {
                Icon(Icons.Outlined.Add, null)
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.notes_empty_action))
            }
        }
    }
}

@Composable
private fun SwipeToDeleteNoteCard(
    note: NoteEntity,
    mode: NoteViewMode,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { it * 0.5f }
    )
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            onDelete()
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.notes_delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(end = 20.dp)
                )
            }
        },
        enableDismissFromStartToEnd = false
    ) {
        NoteCard(note, mode, onClick = onClick)
    }
}

@Composable
private fun NoteCard(
    note: NoteEntity,
    mode: NoteViewMode,
    onClick: () -> Unit
) {
    val blocks = remember(note.blocksData) { NoteBlockCodec.decode(note.blocksData, note.body) }
    val excerpt = remember(note.body, blocks) {
        note.body.ifBlank { blocks.joinToString(" ") { it.text } }.take(160)
    }
    val date = remember(note.updatedAt) {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(note.updatedAt))
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        val pad = if (mode == NoteViewMode.COMPACT) 8.dp else 14.dp
        Column(Modifier.padding(pad)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    val title = note.title.ifBlank { excerpt.take(60) }
                    Text(
                        title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = if (mode == NoteViewMode.COMPACT) 14.sp else 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (mode != NoteViewMode.TITLES && excerpt.isNotBlank()) {
                        Text(
                            excerpt,
                            fontSize = 13.sp,
                            maxLines = if (mode == NoteViewMode.COMPACT) 1 else 3,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (note.favorite) {
                    Icon(
                        Icons.Outlined.StarBorder,
                        contentDescription = stringResource(R.string.notes_card_favorite),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                if (note.pinned) {
                    Icon(
                        Icons.Outlined.PushPin,
                        contentDescription = stringResource(R.string.notes_card_pinned),
                        modifier = Modifier.size(16.dp).padding(start = 4.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (mode != NoteViewMode.TITLES) {
                Spacer(Modifier.height(6.dp))
                Text(date, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ViewSortControl(
    view: NoteViewMode,
    sort: NoteSortMode,
    onView: (NoteViewMode) -> Unit,
    onSort: (NoteSortMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = false,
            onClick = { expanded = true },
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ViewAgenda, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(stringResource(sort.labelRes), fontSize = 12.sp)
                }
            }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            // Vista
            Text(
                stringResource(R.string.notes_view_label),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 2.dp)
            )
            NoteViewMode.entries.forEach { v ->
                DropdownMenuItem(
                    text = { Text(viewNameRes(v)) },
                    leadingIcon = { if (view == v) Icon(Icons.Outlined.PushPin, null) else null },
                    onClick = { onView(v) }
                )
            }
            // Orden
            Text(
                stringResource(R.string.notes_sort_label),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp)
            )
            NoteSortMode.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(stringResource(s.labelRes)) },
                    leadingIcon = { if (sort == s) Icon(Icons.Outlined.PushPin, null) else null },
                    onClick = { onSort(s) }
                )
            }
        }
    }
}

@Composable
private fun viewNameRes(mode: NoteViewMode): String = stringResource(when (mode) {
    NoteViewMode.CARDS -> R.string.notes_view_cards
    NoteViewMode.LIST -> R.string.notes_view_list
    NoteViewMode.COMPACT -> R.string.notes_view_compact
    NoteViewMode.GALLERY -> R.string.notes_view_gallery
    NoteViewMode.TITLES -> R.string.notes_view_titles
})

private fun applyFilter(notes: List<NoteEntity>, filter: NoteFilter): List<NoteEntity> = when (filter) {
    NoteFilter.NONE -> notes
    NoteFilter.FAVORITES -> notes.filter { it.favorite }
    NoteFilter.PINNED -> notes.filter { it.pinned }
    NoteFilter.LOCKED -> notes.filter { it.locked }
    NoteFilter.IMAGES -> notes.filter { hasAttachmentKind(it, "IMAGE") }
    NoteFilter.AUDIO -> notes.filter { hasAttachmentKind(it, "AUDIO") }
    NoteFilter.CHECKLIST -> notes.filter { hasChecklist(it) }
}

private fun hasAttachmentKind(note: NoteEntity, kind: String): Boolean {
    return note.blocksData.contains("\"type\":\"$kind\"") || note.blocksData.contains("\"type\": \"$kind\"")
}

private fun hasChecklist(note: NoteEntity): Boolean {
    return note.blocksData.contains("\"type\":\"CHECKLIST\"") || note.blocksData.contains("\"type\": \"CHECKLIST\"")
}

private fun applySort(notes: List<NoteEntity>, sort: NoteSortMode): List<NoteEntity> = when (sort) {
    NoteSortMode.MODIFIED -> notes.sortedByDescending { it.updatedAt }
    NoteSortMode.CREATED -> notes.sortedByDescending { it.createdAt }
    NoteSortMode.OLDEST -> notes.sortedBy { it.updatedAt }
    NoteSortMode.ALPHA -> notes.sortedBy { it.title.lowercase() }
    NoteSortMode.PINNED -> notes.sortedByDescending { it.pinned }
    NoteSortMode.FAVORITE -> notes.sortedByDescending { it.favorite }
}
