package com.ordia.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ordia.app.overlay.GuardianOverlayService
import com.ordia.app.ui.OrdiaRoot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val incomingText = androidx.compose.runtime.mutableStateOf<String?>(null)
    private val requestedDestination = androidx.compose.runtime.mutableStateOf<String?>(null)
    private val requestedTaskId = androidx.compose.runtime.mutableStateOf<Long?>(null)

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

    override fun onStart() {
        super.onStart()
        reconcileGuardianFromVisibleActivity()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                incomingText.value = intent.getStringExtra(Intent.EXTRA_TEXT)?.take(MAX_SHARED_TEXT_CHARS)
                intent.removeExtra(Intent.EXTRA_TEXT)
                intent.action = null
            }
            Intent.ACTION_PROCESS_TEXT -> {
                incomingText.value = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
                    ?.toString()
                    ?.take(MAX_SHARED_TEXT_CHARS)
                intent.removeExtra(Intent.EXTRA_PROCESS_TEXT)
                intent.action = null
            }
        }
        requestedDestination.value = intent.getStringExtra(EXTRA_DESTINATION)
        requestedTaskId.value = intent.getLongExtra(EXTRA_TASK_ID, -1L).takeIf { it > 0L }
        intent.removeExtra(EXTRA_DESTINATION)
        intent.removeExtra(EXTRA_TASK_ID)
    }

    private fun reconcileGuardianFromVisibleActivity() {
        lifecycleScope.launch {
            val app = application as OrdiaApplication
            val enabled = app.container.preferencesRepository.preferences.first().guardianEnabled
            val serviceIntent = Intent(this@MainActivity, GuardianOverlayService::class.java)
            val overlayGranted = Settings.canDrawOverlays(this@MainActivity)
            if (enabled && overlayGranted) {
                runCatching { ContextCompat.startForegroundService(this@MainActivity, serviceIntent) }
            } else {
                stopService(serviceIntent)
                if (enabled && !overlayGranted) app.container.preferencesRepository.setGuardianEnabled(false)
            }
        }
    }

    companion object {
        const val EXTRA_DESTINATION = "destination"
        const val EXTRA_TASK_ID = "task_id"
        const val OPEN_FOCUS = "focus"
        const val OPEN_GUARDIAN = "guardian"
        const val OPEN_SETTINGS = "settings"
        const val OPEN_CONTEXTUAL = "contextual"
        private const val MAX_SHARED_TEXT_CHARS = 100_000
    }
}
