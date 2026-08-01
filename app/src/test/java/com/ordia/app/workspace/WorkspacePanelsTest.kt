package com.ordia.app.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspacePanelsTest {
    @Test fun panelsCanBeOpenedMinimizedRestoredAndClosed() {
        var state = WorkspaceState()
        state = WorkspacePanels.reduce(state, WorkspaceEvent.Open(WorkspacePanel.CONVERSATIONS))
        assertEquals(WorkspacePanel.CONVERSATIONS, state.active)
        state = WorkspacePanels.reduce(state, WorkspaceEvent.Minimize(WorkspacePanel.CONVERSATIONS))
        assertTrue(WorkspacePanel.CONVERSATIONS in state.minimized)
        state = WorkspacePanels.reduce(state, WorkspaceEvent.Activate(WorkspacePanel.CONVERSATIONS))
        assertFalse(WorkspacePanel.CONVERSATIONS in state.minimized)
        state = WorkspacePanels.reduce(state, WorkspaceEvent.Close(WorkspacePanel.CONVERSATIONS))
        assertFalse(WorkspacePanel.CONVERSATIONS in state.open)
    }

    @Test fun stateSurvivesRotationSerialization() {
        val state = WorkspaceState(listOf(WorkspacePanel.TASKS, WorkspacePanel.SEARCH), WorkspacePanel.SEARCH, setOf(WorkspacePanel.TASKS))
        assertEquals(state, WorkspacePanels.decode(WorkspacePanels.encode(state)))
    }

    @Test fun malformedSavedStateFallsBackSafely() {
        assertEquals(WorkspaceState(), WorkspacePanels.decode("unknown|broken"))
    }
}
