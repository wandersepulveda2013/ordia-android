package com.ordia.app.shortcuts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortcutRouteTest {
    @Test
    fun knownActionsMapOnlyToRealRoutes() {
        assertEquals(ShortcutRoute.CAPTURE, ShortcutRoute.fromAction(ACTION_SHORTCUT_CAPTURE))
        assertEquals(ShortcutRoute.FOCUS, ShortcutRoute.fromAction(ACTION_SHORTCUT_FOCUS))
    }

    @Test
    fun absentOrUnknownActionDoesNotLaunchAnything() {
        assertNull(ShortcutRoute.fromAction(null))
        assertNull(ShortcutRoute.fromAction("com.ordia.app.action.UNKNOWN"))
    }
}
