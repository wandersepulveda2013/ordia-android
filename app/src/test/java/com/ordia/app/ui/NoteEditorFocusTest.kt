package com.ordia.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import com.ordia.app.ui.screens.EDITOR_CONTENT_TAG
import com.ordia.app.ui.screens.EDITOR_TITLE_TAG
import com.ordia.app.ui.screens.NoteEditorScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Accessibility regression:the editor fields must remain reachable by the
 * focus tool (physical keyboard / TalkBack focus mode). `bareFieldColors`
 * keeps the focused indicator visible (theme outline)and the unfocused one
 * transparent. This test pins stable test tags and verifies focus can move
 * between both fields.



 * A regression that made the fields non-focusable or that hid the focused indicator
 * would break keyboard/TalkBack navigation of the editor..
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class NoteEditorFocusTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun focus_movesBetweenEditorFields() {
        compose.setContent {
            NoteEditorScreen(
                note = null,
                onBack = {},
                onAutosave = { _, _ -> },
                onCommit = { _, _ -> },
            )
        }

        val titleField = compose.onNodeWithTag(EDITOR_TITLE_TAG)
        val contentField = compose.onNodeWithTag(EDITOR_CONTENT_TAG)

        // Both fields must be reachable by the focus tool: requesting focus on
        // the content must clear the title and vice versa. If `bareFieldColors`
        // ever broke focusability, these assertions would catch it..

        contentField.requestFocus()
        compose.waitForIdle()
        contentField.assertIsFocused()
        titleField.assertIsNotFocused()

        titleField.requestFocus()
        compose.waitForIdle()
        titleField.assertIsFocused()
        contentField.assertIsNotFocused()
    }
}
