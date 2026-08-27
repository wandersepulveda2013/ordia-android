package com.ordia.app.ui

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performTextInput
import com.ordia.app.ui.screens.NoteEditorScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Regression test para el bug de pérdida de datos P0: el botón retroceso del
 * sistema (gesto/tecla atrás) en el editor salía sin persistir la edición en
 * curso. El editor debe hacer commit de la edición (vía [NoteEditorScreen]'s
 * `onCommit`) antes de navegar hacia atrás.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class NoteEditorBackSaveTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun systemBack_savesUncommittedEditsBeforeNavigating() {
        val commit = arrayOfNulls<Pair<String, String>>(1)
        var autosaved = false
        var navigated = false

        compose.setContent {
            NoteEditorScreen(
                note = null, // nota nueva (id nulo): sin contenido inicial
                onBack = { navigated = true },
                onAutosave = { _, _ -> autosaved = true },
                onCommit = { title, content -> commit[0] = title to content },
            )
        }

        // El usuario escribe un título y algo de contenido (dos campos con setTextAction).
        val textFields = compose.onAllNodes(hasSetTextAction())
        textFields[0].performTextInput("Mi nota")
        textFields[1].performTextInput("contenido")

        // Dispara el retroceso del sistema (botón atrás físico o gesto de Android).
        (compose.activity as OnBackPressedDispatcherOwner)
            .onBackPressedDispatcher
            .onBackPressed()

        compose.waitForIdle()

        val saved = commit[0]
        assertTrue("El back del sistema debe hacer commit antes de salir", saved != null)
        assertEquals("Mi nota", saved!!.first)
        assertEquals("contenido", saved.second)
        assertTrue("La edición fue autosaveada durante la sesión", autosaved)
        assertTrue("Tras el commit debe navegar hacia atrás", navigated)
    }
}