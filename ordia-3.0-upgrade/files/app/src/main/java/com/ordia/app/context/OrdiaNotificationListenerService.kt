package com.ordia.app.context

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

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
        val suggestion = ContextualAnalyzer.analyze(text, sourcePackage = sbn.packageName) ?: return
        if (suggestion.confidence < settings.minimumConfidence) return
        ContextualSuggestionStore(this).add(suggestion, settings.dailyLimit)
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
