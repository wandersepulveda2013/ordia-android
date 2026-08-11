package com.ordia.app.data.preferences

import com.ordia.app.data.local.AutomationRuleEntity
import com.ordia.app.data.local.TaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate

// Stubs JVM para poder compilar y ejecutar los tests unitarios del dominio sin
// Android DataStore. No forman parte de la app; solo se usan desde tools/.

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class InterfaceMode { SIMPLE, ORGANIZED, ADVANCED }
enum class GuardianMode { DORMANT, DISCREET, COMPANION }

enum class GuardianSpecies(val defaultName: String) {
    LUMI("Lumi"),
    MOSS("Moss"),
    ORBIT("Orbit"),
    EMBER("Ember"),
    TIDE("Tide"),
    NOVA("Nova")
}

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val interfaceMode: InterfaceMode = InterfaceMode.ORGANIZED,
    val guardianEnabled: Boolean = false,
    val guardianMode: GuardianMode = GuardianMode.DISCREET,
    val guardianName: String = GuardianSpecies.LUMI.defaultName,
    val guardianSpecies: GuardianSpecies = GuardianSpecies.LUMI,
    val guardianBond: Int = 0,
    val guardianExperience: Int = 0,
    val guardianLastInteraction: Long = 0L,
    val guardianLastEvent: String = "welcome",
    val guardianAnimations: Boolean = true,
    val guardianInteractionEpochDay: Long = LocalDate.now().toEpochDay(),
    val guardianInteractionsToday: Int = 0,
    val autoUpdateEnabled: Boolean = true,
    val autoDownloadUpdates: Boolean = true,
    val quietStartMinutes: Int = 22 * 60,
    val quietEndMinutes: Int = 7 * 60,
    val onboardingComplete: Boolean = false,
    val weekStartsMonday: Boolean = true,
    val defaultFocusMinutes: Int = 25,
    val reduceMotion: Boolean = false,
    val compactNavigation: Boolean = false,
    val showFloatingCapture: Boolean = true,
    val learningEnabled: Boolean = false
) {
    val darkMode: Boolean get() = themeMode == ThemeMode.DARK
}

class PreferencesRepository {
    val preferences: Flow<UserPreferences> = flowOf(UserPreferences())
    fun snapshot(): UserPreferences = UserPreferences()
    fun automationSuggestions(): Flow<List<AutomationRuleEntity>> = flowOf(emptyList())
    fun suggestedTasks(): Flow<List<TaskEntity>> = flowOf(emptyList())

    companion object {
        const val DAILY_INTERACTION_LIMIT = 12
    }
}
