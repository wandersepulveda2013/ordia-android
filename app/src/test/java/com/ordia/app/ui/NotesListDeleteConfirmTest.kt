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
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** La eliminación requiere confirmación y el deshacer sigue disponible tras confirmar. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class NotesListDeleteConfirmTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val note = NoteEntity(
        id = 1, title = "Temporal", content = "Borrar", createdAt =  10, updatedAt =  10,
    )

    @Test
    fun delete_fromMenu_requiresConfirmationBeforeDeleting() {
        var deleted = false
        compose.setContent {
            NotesListScreen(
                notes = listOf(note),
                onOpenNote = {},
                onCreateNote = {},
                onDeleteNote = { deleted = true },
                onRestoreNote = {},
                onTogglePin = {},
            )
        }

        compose.onNodeWithContentDescription("Más").performClick()
        compose.onNodeWithText("Eliminar").performClick()

        compose.onNodeWithText("Eliminar nota").assertIsDisplayed()
        assertFalse("No debe eliminarse sin confirmación", deleted)
        compose.onNodeWithText("Podrás deshacerlo.", substring = true).assertIsDisplayed()


        compose.onNodeWithText("Cancelar").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Eliminar nota").assertDoesNotExist()
        assertFalse("Cancelar no debe eliminar", deleted)

        compose.onNodeWithContentDescription("Más").performClick()
        compose.onNodeWithText("Eliminar").performClick()
        compose.onNodeWithText("Eliminar nota").assertIsDisplayed()
        compose.onNodeWithText("Eliminar", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("Confirmar debe eliminar la nota", true, deleted)


    }

    @Test
    fun delete_confirm_offersUndoAfterDeletion() {
        var deleted: NoteEntity? = null
        var restored: NoteEntity? = null
        compose.setContent {
            NotesListScreen(
                notes = listOf(note),
                onOpenNote = {},
                onCreateNote = {},
                onDeleteNote = { deleted = it },
                onRestoreNote = { restored = it },
                onTogglePin = {},
            )
        }

        compose.onNodeWithContentDescription("Más").performClick()
        compose.onNodeWithText("Eliminar").performClick()
        compose.onNodeWithText("Eliminar nota").assertIsDisplayed()
        compose.onNodeWithText("Eliminar", useUnmergedTree = true).performClick()
        compose.waitForIdle()

        assertEquals("Confirmar debe eliminar la nota", note, deleted)
        compose.onNodeWithText("Nota eliminada").assertIsDisplayed()
        compose.onNodeWithText("Deshacer").performClick()
        compose.waitForIdle()
        assertEquals("Deshacer debe restaurar la nota", note, restored)


    }
}