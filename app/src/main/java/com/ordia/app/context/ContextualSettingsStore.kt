package com.ordia.app.context

import android.content.Context

class ContextualSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ordia_contextual_settings", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()
    var notificationSuggestions: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATIONS, value).apply()
    var dailyLimit: Int
        get() = prefs.getInt(KEY_DAILY_LIMIT, 5).coerceIn(1, 20)
        set(value) = prefs.edit().putInt(KEY_DAILY_LIMIT, value.coerceIn(1, 20)).apply()
    var minimumConfidence: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_CONFIDENCE, java.lang.Double.doubleToRawLongBits(0.70))).coerceIn(0.50, 0.95)
        set(value) = prefs.edit().putLong(KEY_CONFIDENCE, java.lang.Double.doubleToRawLongBits(value.coerceIn(0.50, 0.95))).apply()
    var pausedUntil: Long
        get() = prefs.getLong(KEY_PAUSED_UNTIL, 0L).coerceAtLeast(0L)
        set(value) = prefs.edit().putLong(KEY_PAUSED_UNTIL, value.coerceAtLeast(0L)).apply()

    fun allowedPackages(): Set<String> = prefs.getStringSet(KEY_ALLOWED_PACKAGES, emptySet()).orEmpty().toSet()
    fun isPackageAllowed(packageName: String): Boolean = packageName in allowedPackages()
    fun setPackageAllowed(packageName: String, allowed: Boolean) {
        require(packageName.matches(Regex("[A-Za-z0-9_.]{3,180}")))
        val next = allowedPackages().toMutableSet().apply { if (allowed) add(packageName) else remove(packageName) }
        prefs.edit().putStringSet(KEY_ALLOWED_PACKAGES, next).apply()
    }

    fun isActive(now: Long = System.currentTimeMillis()): Boolean = enabled && pausedUntil <= now
    fun pauseOneHour(now: Long = System.currentTimeMillis()) { pausedUntil = now + 60 * 60_000L }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_NOTIFICATIONS = "notification_suggestions"
        private const val KEY_DAILY_LIMIT = "daily_limit"
        private const val KEY_CONFIDENCE = "minimum_confidence"
        private const val KEY_PAUSED_UNTIL = "paused_until"
        private const val KEY_ALLOWED_PACKAGES = "allowed_packages"
    }
}
