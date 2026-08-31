package com.ordia.app.ui

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.ordia.app.data.NoteEntity
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
 * Regresion de BUG-003 (reseed del editor): NoteEditorScreen tenia un
 * LaunchedEffect(note?.id) que reasignaba title/content partiendo de la
 * instantanea obsoleta note de la BD, sobrescribiendo lo que el usuario
 * acababa de teclear (y aun no persistido, dentro de la ventana del debounce
 * de 800 ms) al recrearse la pantalla. El fix elimino el reseed redundante;
 * este test verifica que una recreacion (rotacion/proceso-muerte, via
 * StateRestorationTester.emulateSavedInstanceStateRestore) preserva el texto
 * sin persistir y que el commit posterior persiste lo tecleado, no el snapshot viejo.

 * Tambien cubre el hueco de cobertura de UI anotado en TEST_STATUS ("sin UI
 * test Compose que ejercite rememberSaveable del editor + recreacion real").
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class NoteEditorRecreationTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** Nota ya persistida en BD (contenido viejo)que el editor abre para editar. */
    private val existingNote = NoteEntity(
        id = 7, title = "Titulo viejo", content = "Contenido viejo", createdAt = 100, updatedAt =  100,
    )

    /**
     * El editor se abre con la instantanea de la BD y el usuario teclea; una
     * recreacion (rotacion, proceso-muerte) restaura el texto en curso via
     * rememberSaveable y NADA lo reseedea desde la nota obsoleta. El back hace
     * commit de lo tecleado, no del contenido viejo.

     */
    @Test
    fun recreation_preservesUnsavedText_commitSavesTypedContent() {
        val commit = arrayOfNulls<Pair<String, String>>(1)

        val restorationTester = StateRestorationTester(compose)
        restorationTester.setContent {
            NoteEditorScreen(
                note = existingNote,
                onBack = {},
                onAutosave = { _, _ -> },
                onCommit = { title, content -> commit[0] = title to content },
            )
        }

        // Reemplazar el contenido viejo (solo en memoria, aun no persistido en
        // la BD)). Se limpia cada campo primero para no depender de la posicion del cursor.

        val textFields = compose.onAllNodes(hasSetTextAction())
        textFields[0].performTextClearance()
        textFields[0].performTextInput("Nuevo titulo")
        textFields[1].performTextClearance()
        textFields[1].performTextInput("texto nuevo")

        // Recreacion (p.ej. rotacion / proceso-muerte:: se restaura el estado
        // guardado del editor). Antes del fix, el LaunchedEffect reasignaba title/
        // content desde la instantanea vieja de la BD (BUG-003) y se perdia lo tecleado.


        restorationTester.emulateSavedInstanceStateRestore()

        compose.waitForIdle()

        (compose.activity as OnBackPressedDispatcherOwner).onBackPressedDispatcher.onBackPressed()

        compose.waitForIdle()

        val saved = commit[0]
        assertTrue("El back tras la recreacion debe hacer commit", saved != null)
        assertEquals("Nuevo titulo", saved!!.first)
        assertEquals("texto nuevo", saved.second)
    }

    /**
     * Una nota nueva en curso (aun sin fila en la BD) tampoco debe perder el
     * texto al recrearse la pantalla; el commit posterior crea la nota con lo
     * tecleado, no una nota vacia.



     */
    @Test
    fun recreation_preservesUnsavedText_forNewNote() {
        val commit = arrayOfNulls<Pair<String, String>>(1)

        val restorationTester = StateRestorationTester(compose)
        restorationTester.setContent {
            NoteEditorScreen(
                note = null,
                onBack = {},
                onAutosave = { _, _ -> },
                onCommit = { title, content -> commit[0] = title to content },
            )
        }

        compose.onAllNodes(hasSetTextAction())[0].performTextInput("Borrador nuevo")

        restorationTester.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        (compose.activity as OnBackPressedDispatcherOwner).onBackPressedDispatcher.onBackPressed()

        compose.waitForIdle()

        val saved = commit[0]
        assertTrue("El commit tras la recreacion de una nota nueva debe existir", saved != null)

        assertEquals("Borrador nuevo", saved!!.first)
        assertEquals("", saved.second)
    }
}
