package com.ordia.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import com.ordia.app.data.NoteEntity
import com.ordia.app.ui.screens.NotesListScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Accessibility regression:each note row must be reachable by the focus tool
 * (physical keyboard / TalkBack focus mode)and rows must focus independently,
 * so the focused row gets the visible indicator (background highlight). A
 * regression that merged rows into a single node or made them non-focusable
 * would break keyboard/TalkBack navigation of the list.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class NotesListFocusTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun focus_movesBetweenNoteRows() {
        val first = NoteEntity(id = 1, title = "Recetas", content = "Paella", createdAt =  10, updatedAt =  10)
        val second = NoteEntity(id = 2, title = "Apuntes", content = "Kotlin", createdAt =  20, updatedAt =  20)
        compose.setContent {
            NotesListScreen(
                notes = listOf(first, second),
                onOpenNote = {},
                onCreateNote = {},
                onDeleteNote = {},
                onRestoreNote = {},
                onTogglePin = {},
            )
        }

        val firstRow = compose.onNodeWithTag("note_row_1")
        val secondRow = compose.onNodeWithTag("note_row_2")

        firstRow.requestFocus()
        compose.waitForIdle()
        firstRow.assertIsFocused()
        secondRow.assertIsNotFocused()

        secondRow.requestFocus()
        compose.waitForIdle()
        secondRow.assertIsFocused()
        firstRow.assertIsNotFocused()
    }
}