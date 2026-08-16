package com.ordia.app.shortcuts

internal const val ACTION_SHORTCUT_CAPTURE = "com.ordia.app.action.SHORTCUT_CAPTURE"
internal const val ACTION_SHORTCUT_FOCUS = "com.ordia.app.action.SHORTCUT_FOCUS"

internal enum class ShortcutRoute {
    CAPTURE,
    FOCUS;

    companion object {
        fun fromAction(action: String?): ShortcutRoute? = when (action) {
            ACTION_SHORTCUT_CAPTURE -> CAPTURE
            ACTION_SHORTCUT_FOCUS -> FOCUS
            else -> null
        }
    }
}
