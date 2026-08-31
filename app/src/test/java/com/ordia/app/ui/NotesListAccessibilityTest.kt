package com.ordia.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.ordia.app.data.NoteEntity
import com.ordia.app.ui.screens.NotesListScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Accesibilidad de la lista de notas (P3): la fila en sí es el destino táctil
 * principal y el icono de pin debe distinguir qué nota está fijada para
 * lectores de pantalla. Verifica que TalkBack anuncie un rótulo descriptivo
 * ("Abrir nota: <título>") para la acción de la fila y que el pin incluya
 * el título de la nota.

 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class NotesListAccessibilityTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val pinnedNote = NoteEntity(
        id = 1, title = "Apuntes", content = "Kotlin", createdAt = 10, updatedAt =  10, pinned = true,
    )
    private val regularNote = NoteEntity(
        id =  2, title = "Recetas", content = "Paella", createdAt =  20, updatedAt =  20, pinned = false,
    )
    private val untitledNote = NoteEntity(
        id =  9, title = "", content = "Solo cuerpo", createdAt =  10, updatedAt =  10,
    )

    /** Matcher para una acción OnClick cuyo rótulo es exactamente [label]. */
    private fun hasOnClickLabel(label: String): SemanticsMatcher = SemanticsMatcher(
        "onClickLabel='$label'",
    ) { node: SemanticsNode ->
        node.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

    @Test
    fun noteRow_exposesClickActionWithTitle_andPinDescribesTitle() {
        var opened: Long? = null
        compose.setContent {
            NotesListScreen(
                notes = listOf(pinnedNote, regularNote),
                onOpenNote = { opened = it.id },
                onCreateNote = {},
                onDeleteNote = {},
                onRestoreNote = {},
                onTogglePin = {},
            )
        }

        // La fila (nodo con la acción "Abrir nota: <título>") es alcanzable.



        compose.onAllNodes(hasOnClickLabel("Abrir nota: Apuntes")).assertCountEquals(1)
        compose.onAllNodes(hasOnClickLabel("Abrir nota: Recetas")).assertCountEquals(1)

        // El pin de la nota fijada menciona el título;la nota sin pin no expone
        // ninguna descripción de pin.


        compose.onNodeWithContentDescription("Fijada: Apuntes").assertExists()

        compose.onNodeWithContentDescription("Fijada: Recetas").assertDoesNotExist()

        // La acción sigue abriendo la nota(no solo anunciarse.


        compose.onAllNodes(hasOnClickLabel("Abrir nota: Apuntes"))[0].performClick()
        compose.waitForIdle()
        assertTrue("El tap on la fila debe abrir la nota", opened == pinnedNote.id)



    }

    @Test
    fun untitledNote_usesFallbackRowLabel() {
        compose.setContent {
            NotesListScreen(
                notes = listOf(untitledNote),
                onOpenNote = {},
                onCreateNote = {},
                onDeleteNote = {},
                onRestoreNote = {},
                onTogglePin = {},
            )
        }
        compose.onAllNodes(hasOnClickLabel("Abrir nota sin título")).assertCountEquals(1)
    }
}
