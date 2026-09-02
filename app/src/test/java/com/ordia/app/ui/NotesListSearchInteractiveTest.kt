package com.ordia.app.ui

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.ordia.app.data.NoteEntity
import com.ordia.app.ui.screens.NotesListScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Regresión de BUG-005: el icono de búsqueda de la toolbar estaba cableado con
 * `onSearchQueryChange("")`, así que `isSearching` (derivado de query no vacía)
 * nunca pasaba a verdadero y el campo de búsqueda era inalcanzable desde la UI.
 *
 * Verifica el flujo completo a nivel de pantalla:
 *  1. tocar la lupa abre el campo de búsqueda (query vacío);
 *  2. teclear filtra la lista (se simula el filtrado que hace la App);
 *  3. limpiar (X del campo) sale del modo búsqueda y restaura la lista completa.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class NotesListSearchInteractiveTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val allNotes = listOf(
        NoteEntity(id = 1, title = "Apuntes", content = "Kotlin", createdAt = 10, updatedAt = 10, pinned = false),
        NoteEntity(id = 2, title = "Recetas", content = "Paella", createdAt = 20, updatedAt = 20, pinned = false),
    )

    /** Estado del host simulado: emula NotepadApp manteniendo query y filtrado. */
    private fun setListContent(state: MutableState<String>) {
        compose.setContent {
            val query = state.value
            NotesListScreen(
                notes = if (query.isBlank()) {
                    allNotes
                } else {
                    allNotes.filter {
                        it.title.contains(query, ignoreCase = true) || it.content.contains(query, ignoreCase = true)
                    }
                },
                onOpenNote = {},
                onCreateNote = {},
                onDeleteNote = {},
                onRestoreNote = {},
                onTogglePin = {},
                searchQuery = query,
                onSearchQueryChange = { state.value = it },
            )
        }
    }

    /**
     * Regresión de layout: el campo de búsqueda y la lista comparten el Box del
     * Scaffold. Antes de envolverlos en una Column, el campo se dibujaba ENCIMA de
     * la primera fila (ambos hacían `fillMaxSize().padding(padding)` por separado).
     * El campo debe terminar por encima de donde empieza la primera nota.
     */
    private fun assertSearchFieldDoesNotOverlapFirstRow() {
        val fieldBottom = compose.onAllNodes(hasSetTextAction())[0]
            .fetchSemanticsNode().boundsInRoot.bottom
        val firstRowTop = compose.onNodeWithText("Apuntes")
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue(
            "Search field (bottom=$fieldBottom) overlaps first note row (top=$firstRowTop)",
            fieldBottom <= firstRowTop,
        )
    }

    @Test
    fun searchIcon_opensSearchField_andTypingFiltersList() {
        val query = mutableStateOf("")
        setListContent(query)

        // Estado inicial: lista completa sin campo de texto ni contador.
        compose.onNodeWithText("Apuntes").assertExists()
        compose.onNodeWithText("Recetas").assertExists()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)

        // La lupa abre el modo búsqueda con el campo vacío (BUG-005: antes no hacía nada).
        compose.onNodeWithContentDescription("Buscar notas").performClick()
        compose.waitForIdle()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)

        // El campo expone el rótulo "Buscar notas" como texto accesible del nodo(Text): así
        // TalkBack puede anunciar el propósito del campo(placeholder o label cumplen este rol..

        val searchFieldNode = compose.onAllNodes(hasSetTextAction())[0].fetchSemanticsNode()
        val searchLabel = searchFieldNode.config.getOrNull(SemanticsProperties.Text)?.joinToString()
        assertEquals("Buscar notas", searchLabel)

        compose.onNodeWithText("Apuntes").assertExists() // lista completa aún visible
        assertSearchFieldDoesNotOverlapFirstRow()

        // Escribir filtra.
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("Recetas")
        compose.waitForIdle()

        compose.onNodeWithText("Notas: 1").assertExists()
        compose.onNodeWithText("Apuntes").assertDoesNotExist()

        // El rótulo accesible persiste con query escrita: un placeholder desaparecería al
        // teclear (la línea de detalle del campo se pierde para TalkBack); el label flotante no..
        // Este es el valor real del label frente al placeholder..
        val typedNode = compose.onAllNodes(hasSetTextAction())[0].fetchSemanticsNode()
        val typedLabel = typedNode.config.getOrNull(SemanticsProperties.Text)?.joinToString()
        assertEquals("Buscar notas", typedLabel)

        // Limpiar la query con la X del campo restaura la lista completa y sale de búsqueda.
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("")
        compose.waitForIdle()

        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithText("Apuntes").assertExists()
        compose.onNodeWithText("Recetas").assertExists()
    }

    @Test
    fun searchIcon_toggleOff_exitsSearchMode() {
        val query = mutableStateOf("")
        setListContent(query)

        compose.onNodeWithContentDescription("Buscar notas").performClick()
        compose.waitForIdle()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)

        // Tap en la lupa de nuevo cierra el modo búsqueda.
        compose.onNodeWithContentDescription("Buscar notas").performClick()
        compose.waitForIdle()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithText("Apuntes").assertExists()
        compose.onNodeWithText("Recetas").assertExists()
    }

    @Test
    fun systemBack_withSearchOpen_exitsSearchModeInsteadOfClosing() {
        // REGRESIÓN (P1): con el modo búsqueda abierto, el back del sistema debe
        // salir del modo búsqueda (volver a la lista completa) en vez de cerrar la
        // app, que era el destino de la pila raíz antes de este guard.
        val query = mutableStateOf("")
        compose.setContent {
            NotesListScreen(
                notes = if (query.value.isBlank()) {
                    allNotes
                } else {
                    allNotes.filter {
                        it.title.contains(query.value, ignoreCase = true) || it.content.contains(query.value, ignoreCase = true)
                    }
                },
                onOpenNote = {},
                onCreateNote = {},
                onDeleteNote = {},
                onRestoreNote = {},
                onTogglePin = {},
                searchQuery = query.value,
                onSearchQueryChange = { query.value = it },
            )
        }

        // Abrir el modo búsqueda.

        compose.onNodeWithContentDescription("Buscar notas").performClick()
        compose.waitForIdle()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)

        // El back del sistema sale del modo búsqueda (no cierra la app).
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()

        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithText("Apuntes").assertExists()
        compose.onNodeWithText("Recetas").assertExists()
        // El back siguiente (ya sin búsqueda) puede propagarse a la raíz (cerrar la app.
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()
    }

    @Test
    fun clearingTypedQuery_restoresFullList_whileSearchModeEnds() {
        val query = mutableStateOf("")
        setListContent(query)

        compose.onNodeWithContentDescription("Buscar notas").performClick()
        compose.waitForIdle()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("Apuntes")
        compose.waitForIdle()

        // Solo queda la coincidencia (el campo de búsqueda contiene la misma
        // palabra, por eso se distingue por el contenido único de cada fila).
        compose.onNodeWithText("Kotlin").assertExists()
        compose.onNodeWithText("Paella").assertDoesNotExist()

        // Vaciar el campo de búsqueda restaura la lista completa (y cierra la búsqueda).
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("")
        compose.waitForIdle()

        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithText("Apuntes").assertExists()
        compose.onNodeWithText("Recetas").assertExists()
    }
}