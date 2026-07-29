package com.ordia.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.ordia.app.ui.OrdiaRoot

class MainActivity : ComponentActivity() {
    private val incomingText = mutableStateOf<String?>(null)
    private val requestedDestination = mutableStateOf<String?>(null)
    private val requestedTaskId = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            OrdiaRoot(
                incomingText = incomingText.value,
                requestedDestination = requestedDestination.value,
                requestedTaskId = requestedTaskId.value,
                onIncomingTextConsumed = { incomingText.value = null },
                onNavigationRequestConsumed = {
                    requestedDestination.value = null
                    requestedTaskId.value = null
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        incomingText.value = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> incomingText.value
        }
        requestedDestination.value = intent.getStringExtra(EXTRA_DESTINATION)
        requestedTaskId.value = intent.getLongExtra(EXTRA_TASK_ID, -1L).takeIf { it > 0 }
    }


    companion object {
        const val EXTRA_DESTINATION = "destination"
        const val EXTRA_TASK_ID = "task_id"
        const val OPEN_FOCUS = "focus"
    }
}
