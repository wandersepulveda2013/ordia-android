package com.ordia.app.context

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.UUID

class OrdiaNotificationListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        if (sbn.packageName == packageName || sbn.isOngoing || (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val settings = ContextualSettingsStore(this)
        if (!settings.isActive() || !settings.notificationSuggestions) return
        if (!settings.isPackageAllowed(sbn.packageName) || isSensitivePackage(sbn.packageName)) return
        val extras = notification.extras ?: return
        val text = buildString {
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.let { append(it).append(' ') }
            extras.getCharSequence(Notification.EXTRA_TEXT)?.let { append(it).append(' ') }
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { append(it).append(' ') }
            if (isBlank()) extras.getCharSequence(Notification.EXTRA_TITLE)?.let { append(it) }
        }.trim().take(4_000)
        if (text.isBlank()) return

        // Usar el nuevo motor contextual
        val event = ContextEvent(
            source = ContextCaptureSource.NOTIFICATION,
            rawText = text,
            timestampMs = System.currentTimeMillis(),
            sourcePackage = sbn.packageName
        )
        val engine = ContextEngine.getInstance(this)
        val result = engine.processEvent(event)

        when (result) {
            is ContextResult.PendingConfirmation -> {
                // Almacenar en el store antiguo para que la UI lo recoja
                val suggestion = convertToSuggestion(result.intent)
                ContextualSuggestionStore(this).add(suggestion, settings.dailyLimit)
            }
            is ContextResult.Created -> {
                // Confirmado automáticamente (no es el caso por defecto ALWAYS_CONFIRM)
            }
            is ContextResult.Discarded -> {
                // Silenciosamente descartado
            }
        }
    }

    private fun convertToSuggestion(intent: ContextIntent): ContextualSuggestion {
        val kind = when (intent.kind) {
            ContextIntentKind.STUDY -> ContextualKind.STUDY
            ContextIntentKind.EVENT, ContextIntentKind.APPOINTMENT,
            ContextIntentKind.MEETING, ContextIntentKind.CALL,
            ContextIntentKind.VISIT -> ContextualKind.EVENT
            ContextIntentKind.NOTE, ContextIntentKind.GOAL,
            ContextIntentKind.HABIT, ContextIntentKind.COMMITMENT_PERSONAL,
            ContextIntentKind.COMMITMENT_WORK -> ContextualKind.NOTE
            else -> ContextualKind.TASK
        }
        return ContextualSuggestion(
            id = UUID.randomUUID().toString().replace("-", "").take(64).padEnd(64, '0'),
            kind = kind,
            title = intent.title.take(100),
            dueAt = intent.dueAt,
            confidence = intent.confidence.toDouble().coerceIn(0.0, 1.0),
            sourcePackage = intent.sourcePackage
        )
    }

    private fun isSensitivePackage(value: String): Boolean {
        val lower = value.lowercase()
        return SENSITIVE_PACKAGE_TOKENS.any(lower::contains)
    }

    companion object {
        private val SENSITIVE_PACKAGE_TOKENS = setOf(
            "bank", "banco", "wallet", "finance", "authenticator", "password", "vault",
            "health", "medical", "clinic", "hospital", "insurance"
        )
    }
}
