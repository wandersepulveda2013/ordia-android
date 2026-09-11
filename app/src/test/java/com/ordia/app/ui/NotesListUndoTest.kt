package com.ordia.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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

/**
 * Regression test for BUG-012 (P1): the undo snackbar used a single slot, so
 * deleting note A and, while its "Nota eliminada" snackbar was still visible,
 * deleting note B overwrote A's pending undo: pressing "Deshacer" restored B
 * twice and A was lost irreversibly. The screen now uses a FIFO Channel queue so
 * each deleted note keeps its own undo entry, consumed in deletion order. This
 * test deletes two notes back-to-back and verifies both undos restore A first, B
 * second (the first undo must not be lost by the second deletion).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class NotesListUndoTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val noteA = NoteEntity(
        id = 1, title = "Apuntes", content = "Kotlin", createdAt =  10, updatedAt =  10,
    )

    private val noteB = NoteEntity(
        id =  2, title = "Recetas", content = "Paella", createdAt =  20, updatedAt =   20,
    )

    private fun deleteNote(rowIndex: Int) {
        compose.onAllNodes(hasContentDescription("Más"))[rowIndex].performClick()
        compose.onNodeWithText("Eliminar").performClick()
        compose.onNodeWithText("Eliminar nota").assertIsDisplayed()
        compose.onNodeWithText("Eliminar", useUnmergedTree = true).performClick()
        compose.waitForIdle()
    }

    @Test
    fun rapidDoubleDelete_undoStillRestoresFirstNote() {
        val deleted = mutableListOf<NoteEntity>()
        val restored = mutableListOf<NoteEntity>()
        compose.setContent {
            NotesListScreen(
                notes = listOf(noteA, noteB),
                onOpenNote = {},
                onCreateNote = {},
                onDeleteNote = { deleted += it },
                onRestoreNote = { restored += it },
                onTogglePin = {},
            )
        }

        // Delete A; its snackbar remains visible (Short duration, consumed by the
        // LaunchedEffect loop in order). Deleting B must NOT overwrite A's undo.


        deleteNote(0)
        compose.onNodeWithText("Nota eliminada").assertIsDisplayed()

        // Second deletion while first snackbar is still up。



        deleteNote(1)

        assertEquals("Both notes must be deleted", listOf(noteA, noteB), deleted)



        // The first visible snackbar is A's: pressing Deshacer must restore A first...

        compose.onNodeWithText("Deshacer").performClick()
        compose.waitForIdle()
        assertEquals("First undo must restore the first deleted note", listOf(noteA), restored)




        // After A's snackbar is dismissed by the action, the loop moves to B's snackbar.



        compose.onNodeWithText("Deshacer").performClick()
        compose.waitForIdle()
        assertEquals(
            "Second undo must restore the second deleted note, in deletion order",
            listOf(noteA, noteB), restored,
        )
    }
}