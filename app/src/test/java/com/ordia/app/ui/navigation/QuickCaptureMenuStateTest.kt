package com.ordia.app.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickCaptureMenuStateTest {
    @Test fun sameButtonOpensAndClosesMenu() {
        val opened = QuickCaptureMenuState.reduce(false, QuickCaptureMenuEvent.TOGGLE)
        assertTrue(opened)
        assertFalse(QuickCaptureMenuState.reduce(opened, QuickCaptureMenuEvent.TOGGLE))
    }

    @Test fun everyDismissPathClosesMenu() {
        listOf(
            QuickCaptureMenuEvent.OUTSIDE,
            QuickCaptureMenuEvent.BACK,
            QuickCaptureMenuEvent.ESCAPE,
            QuickCaptureMenuEvent.NAVIGATE
        ).forEach { event -> assertFalse(QuickCaptureMenuState.reduce(true, event)) }
    }
}
