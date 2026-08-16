package com.ordia.app.shortcuts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.ordia.app.MainActivity
import com.ordia.app.overlay.QuickCaptureActivity

/** Despacha shortcuts declarados por Ordía únicamente a destinos internos reales. */
class OrdiaShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val destination = when (ShortcutRoute.fromAction(intent?.action)) {
            ShortcutRoute.CAPTURE -> Intent(this, QuickCaptureActivity::class.java)
            ShortcutRoute.FOCUS -> Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.OPEN_FOCUS)
            null -> null
        }
        destination?.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        if (destination != null) startActivity(destination)
        finish()
    }
}
