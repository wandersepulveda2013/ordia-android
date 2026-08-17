package com.ordia.app.shortcuts

internal const val ACTION_SHORTCUT_CAPTURE = "com.ordia.app.action.SHORTCUT_CAPTURE"
internal const val ACTION_SHORTCUT_FOCUS = "com.ordia.app.action.SHORTCUT_FOCUS"
internal const val ACTION_SHORTCUT_NEW_NOTE = "com.ordia.app.action.SHORTCUT_NEW_NOTE"
internal const val ACTION_SHORTCUT_SCANNER = "com.ordia.app.action.SHORTCUT_SCANNER"
internal const val ACTION_SHORTCUT_VOICE_NOTE = "com.ordia.app.action.SHORTCUT_VOICE_NOTE"

internal enum class ShortcutRoute {
    CAPTURE,
    FOCUS,
    NEW_NOTE,
    SCANNER,
    VOICE_NOTE;

    companion object {
        fun fromAction(action: String?): ShortcutRoute? = when (action) {
            ACTION_SHORTCUT_CAPTURE -> CAPTURE
            ACTION_SHORTCUT_FOCUS -> FOCUS
            ACTION_SHORTCUT_NEW_NOTE -> NEW_NOTE
            ACTION_SHORTCUT_SCANNER -> SCANNER
            ACTION_SHORTCUT_VOICE_NOTE -> VOICE_NOTE
            else -> null
        }
    }
}
