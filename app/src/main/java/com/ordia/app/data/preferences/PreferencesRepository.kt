package com.ordia.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class InterfaceMode { SIMPLE, ORGANIZED, ADVANCED }
enum class GuardianMode { DORMANT, DISCREET, COMPANION }

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val interfaceMode: InterfaceMode = InterfaceMode.ORGANIZED,
    val guardianEnabled: Boolean = false,
    val guardianMode: GuardianMode = GuardianMode.DISCREET,
    val quietStartMinutes: Int = 22 * 60,
    val quietEndMinutes: Int = 7 * 60,
    val onboardingComplete: Boolean = false,
    val weekStartsMonday: Boolean = true,
    val defaultFocusMinutes: Int = 25,
    val reduceMotion: Boolean = false,
    val compactNavigation: Boolean = false
) {
    val darkMode: Boolean get() = themeMode == ThemeMode.DARK
}

private val Context.ordiaDataStore by preferencesDataStore(name = "ordia_preferences")

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val legacyDarkMode = booleanPreferencesKey("dark_mode")
        val interfaceMode = stringPreferencesKey("interface_mode")
        val guardianEnabled = booleanPreferencesKey("guardian_enabled")
        val guardianMode = stringPreferencesKey("guardian_mode")
        val quietStart = intPreferencesKey("quiet_start")
        val quietEnd = intPreferencesKey("quiet_end")
        val onboardingComplete = booleanPreferencesKey("onboarding_complete")
        val weekStartsMonday = booleanPreferencesKey("week_starts_monday")
        val defaultFocusMinutes = intPreferencesKey("default_focus_minutes")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val compactNavigation = booleanPreferencesKey("compact_navigation")
    }

    val preferences: Flow<UserPreferences> = context.ordiaDataStore.data.map { values ->
        val legacyDark = values[Keys.legacyDarkMode]
        val theme = values[Keys.themeMode]?.toEnumOrNull<ThemeMode>()
            ?: if (legacyDark == true) ThemeMode.DARK else ThemeMode.SYSTEM
        UserPreferences(
            themeMode = theme,
            interfaceMode = values[Keys.interfaceMode]?.toEnumOrNull<InterfaceMode>() ?: InterfaceMode.ORGANIZED,
            guardianEnabled = values[Keys.guardianEnabled] ?: false,
            guardianMode = values[Keys.guardianMode]?.toEnumOrNull<GuardianMode>() ?: GuardianMode.DISCREET,
            quietStartMinutes = (values[Keys.quietStart] ?: 22 * 60).coerceIn(0, 1439),
            quietEndMinutes = (values[Keys.quietEnd] ?: 7 * 60).coerceIn(0, 1439),
            onboardingComplete = values[Keys.onboardingComplete] ?: false,
            weekStartsMonday = values[Keys.weekStartsMonday] ?: true,
            defaultFocusMinutes = (values[Keys.defaultFocusMinutes] ?: 25).coerceIn(5, 180),
            reduceMotion = values[Keys.reduceMotion] ?: false,
            compactNavigation = values[Keys.compactNavigation] ?: false
        )
    }

    suspend fun setThemeMode(value: ThemeMode) = edit { it[Keys.themeMode] = value.name }
    suspend fun setInterfaceMode(value: InterfaceMode) = edit { it[Keys.interfaceMode] = value.name }
    suspend fun setGuardianEnabled(value: Boolean) = edit { it[Keys.guardianEnabled] = value }
    suspend fun setGuardianMode(value: GuardianMode) = edit { it[Keys.guardianMode] = value.name }
    suspend fun setQuietHours(startMinutes: Int, endMinutes: Int) = edit {
        it[Keys.quietStart] = startMinutes.coerceIn(0, 1439)
        it[Keys.quietEnd] = endMinutes.coerceIn(0, 1439)
    }
    suspend fun setOnboardingComplete(value: Boolean) = edit { it[Keys.onboardingComplete] = value }
    suspend fun setWeekStartsMonday(value: Boolean) = edit { it[Keys.weekStartsMonday] = value }
    suspend fun setDefaultFocusMinutes(value: Int) = edit { it[Keys.defaultFocusMinutes] = value.coerceIn(5, 180) }
    suspend fun setReduceMotion(value: Boolean) = edit { it[Keys.reduceMotion] = value }
    suspend fun setCompactNavigation(value: Boolean) = edit { it[Keys.compactNavigation] = value }

    /** Compatibility with the 0.1 API. */
    suspend fun setDarkMode(enabled: Boolean) = setThemeMode(if (enabled) ThemeMode.DARK else ThemeMode.LIGHT)

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.ordiaDataStore.edit { preferences -> block(preferences) }
    }

    private inline fun <reified T : Enum<T>> String.toEnumOrNull(): T? =
        runCatching { enumValueOf<T>(this) }.getOrNull()
}
