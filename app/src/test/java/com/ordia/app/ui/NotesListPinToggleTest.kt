package com.ordia.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ordia.app.data.NoteEntity
import com.ordia.app.ui.screens.NotesListScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** El menú de la fila permite fijar/desfijar una nota y propaga el id correcto. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class NotesListPinToggleTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun unpinnedNote_menuItemPins_itAndClosesMenu() {
        val note = NoteEntity(id = 7, title = "Recetas", content = "Paella", createdAt =  10, updatedAt =  10)
        var toggled: Long? = null
        compose.setContent {
            NotesListScreen(
                notes = listOf(note),
                onOpenNote = {},
                onCreateNote = {},
                onDeleteNote = {},
                onRestoreNote = {},
                onTogglePin = { toggled = it },
            )
        }

        compose.onNodeWithContentDescription("Más").performClick()
        compose.onNodeWithText("Fijar").assertIsDisplayed()
        compose.onNodeWithText("Fijar").performClick()
        compose.waitForIdle()

        assertEquals("Fijar debe propagar el id de la nota", 7L, toggled)
        compose.onNodeWithText("Fijar").assertDoesNotExist()
    }

    @Test
    fun pinnedNote_menuItemUnpins_it() {
        val note = NoteEntity(id = 9, title = "Apuntes", content = "Kotlin", createdAt =  10, updatedAt =  10, pinned = true)
        var toggled: Long? = null
        compose.setContent {
            NotesListScreen(
                notes = listOf(note),
                onOpenNote = {},
                onCreateNote = {},
                onDeleteNote = {},
                onRestoreNote = {},
                onTogglePin = { toggled = it },
            )
        }

        compose.onNodeWithContentDescription("Más").performClick()
        compose.onNodeWithText("Desfijar").assertIsDisplayed()
        compose.onNodeWithText("Desfijar").performClick()
        compose.waitForIdle()

        assertEquals("Desfijar debe propagar el id de la nota", 9L, toggled)
        compose.onNodeWithText("Desfijar").assertDoesNotExist()
    }
}