package com.ordia.app.shortcuts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortcutRouteTest {
    @Test
    fun knownActionsMapOnlyToRealRoutes() {
        assertEquals(ShortcutRoute.CAPTURE, ShortcutRoute.fromAction(ACTION_SHORTCUT_CAPTURE))
        assertEquals(ShortcutRoute.FOCUS, ShortcutRoute.fromAction(ACTION_SHORTCUT_FOCUS))
        assertEquals(ShortcutRoute.NEW_NOTE, ShortcutRoute.fromAction(ACTION_SHORTCUT_NEW_NOTE))
        assertEquals(ShortcutRoute.SCANNER, ShortcutRoute.fromAction(ACTION_SHORTCUT_SCANNER))
        assertEquals(ShortcutRoute.VOICE_NOTE, ShortcutRoute.fromAction(ACTION_SHORTCUT_VOICE_NOTE))
    }

    @Test
    fun absentOrUnknownActionDoesNotLaunchAnything() {
        assertNull(ShortcutRoute.fromAction(null))
        assertNull(ShortcutRoute.fromAction("com.ordia.app.action.UNKNOWN"))
    }
}
