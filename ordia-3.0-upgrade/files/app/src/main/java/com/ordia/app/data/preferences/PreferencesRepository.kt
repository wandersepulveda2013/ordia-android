package com.ordia.app.data.preferences

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ordia.app.domain.GuardianEngine
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class InterfaceMode { SIMPLE, ORGANIZED, ADVANCED }
enum class GuardianMode { DORMANT, DISCREET, COMPANION }

enum class GuardianSpecies(val label: String, val defaultName: String, val description: String) {
    LUMI("Lumi", "Lumi", "Ser de luz curioso que cambia de brillo con tus avances."),
    MOSS("Moss", "Moss", "Criatura de bosque tranquila que florece con tus hábitos."),
    ORBIT("Orbit", "Orbit", "Compañero cósmico que reúne pequeñas estrellas de progreso."),
    EMBER("Ember", "Ember", "Espíritu de fuego amable que se fortalece con tu enfoque."),
    TIDE("Tide", "Tide", "Guardián acuático adaptable que aprende de tus ritmos."),
    NOVA("Nova", "Nova", "Ser celeste expresivo que evoluciona con grandes proyectos.")
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
    val compactNavigation: Boolean = false
) {
    val darkMode: Boolean get() = themeMode == ThemeMode.DARK
}

private val Context.ordiaDataStore by preferencesDataStore(
    name = "ordia_preferences",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val legacyDarkMode = booleanPreferencesKey("dark_mode")
        val interfaceMode = stringPreferencesKey("interface_mode")
        val guardianEnabled = booleanPreferencesKey("guardian_enabled")
        val guardianMode = stringPreferencesKey("guardian_mode")
        val guardianName = stringPreferencesKey("guardian_name")
        val guardianSpecies = stringPreferencesKey("guardian_species")
        val guardianBond = intPreferencesKey("guardian_bond")
        val guardianExperience = intPreferencesKey("guardian_experience")
        val guardianLastInteraction = longPreferencesKey("guardian_last_interaction")
        val guardianLastEvent = stringPreferencesKey("guardian_last_event")
        val guardianAnimations = booleanPreferencesKey("guardian_animations")
        val guardianInteractionDay = longPreferencesKey("guardian_interaction_day")
        val guardianInteractionsToday = intPreferencesKey("guardian_interactions_today")
        val autoUpdateEnabled = booleanPreferencesKey("auto_update_enabled")
        val autoDownloadUpdates = booleanPreferencesKey("auto_download_updates")
        val quietStart = intPreferencesKey("quiet_start")
        val quietEnd = intPreferencesKey("quiet_end")
        val onboardingComplete = booleanPreferencesKey("onboarding_complete")
        val weekStartsMonday = booleanPreferencesKey("week_starts_monday")
        val defaultFocusMinutes = intPreferencesKey("default_focus_minutes")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val compactNavigation = booleanPreferencesKey("compact_navigation")
    }

    val preferences: Flow<UserPreferences> = context.ordiaDataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map(::toUserPreferences)

    suspend fun setThemeMode(value: ThemeMode) = edit { it[Keys.themeMode] = value.name }
    suspend fun setInterfaceMode(value: InterfaceMode) = edit { it[Keys.interfaceMode] = value.name }
    suspend fun setGuardianEnabled(value: Boolean) = edit { it[Keys.guardianEnabled] = value }
    suspend fun setGuardianMode(value: GuardianMode) = edit { it[Keys.guardianMode] = value.name }
    suspend fun setGuardianName(value: String) = edit { values ->
        val species = values[Keys.guardianSpecies]?.toEnumOrNull<GuardianSpecies>() ?: GuardianSpecies.LUMI
        val normalized = value.trim().filterNot(Char::isISOControl).take(24).ifBlank { species.defaultName }
        values[Keys.guardianName] = normalized
        values[Keys.guardianLastInteraction] = System.currentTimeMillis()
        values[Keys.guardianLastEvent] = "rename"
    }

    suspend fun setGuardianSpecies(value: GuardianSpecies) = edit { values ->
        val currentName = values[Keys.guardianName].orEmpty().trim()
        val wasAutomaticName = currentName.isBlank() || GuardianSpecies.entries.any {
            currentName.equals(it.defaultName, ignoreCase = true)
        }
        values[Keys.guardianSpecies] = value.name
        if (wasAutomaticName) values[Keys.guardianName] = value.defaultName
        values[Keys.guardianLastInteraction] = System.currentTimeMillis()
        values[Keys.guardianLastEvent] = "evolve"
    }

    suspend fun interactGuardian(interaction: GuardianEngine.Interaction) = edit { values ->
        val today = LocalDate.now().toEpochDay()
        val currentCount = if (values[Keys.guardianInteractionDay] == today) {
            values[Keys.guardianInteractionsToday] ?: 0
        } else 0
        if (currentCount < DAILY_INTERACTION_LIMIT) {
            values[Keys.guardianBond] = ((values[Keys.guardianBond] ?: 0) + interaction.bond).coerceIn(0, 9_999)
        }
        values[Keys.guardianInteractionDay] = today
        values[Keys.guardianInteractionsToday] = (currentCount + 1).coerceAtMost(DAILY_INTERACTION_LIMIT)
        values[Keys.guardianLastInteraction] = System.currentTimeMillis()
        values[Keys.guardianLastEvent] = interaction.event
    }

    /** Persisted experience is monotonic and derived from real Ordia records. */
    suspend fun syncGuardianExperience(derivedExperience: Int) = edit { values ->
        val current = values[Keys.guardianExperience] ?: 0
        val normalized = derivedExperience.coerceIn(0, MAX_GUARDIAN_EXPERIENCE)
        if (normalized > current) {
            values[Keys.guardianExperience] = normalized
            values[Keys.guardianLastInteraction] = System.currentTimeMillis()
            values[Keys.guardianLastEvent] = "progress"
        }
    }

    suspend fun setGuardianAnimations(value: Boolean) = edit { it[Keys.guardianAnimations] = value }
    suspend fun setAutoUpdateEnabled(value: Boolean) = edit { it[Keys.autoUpdateEnabled] = value }
    suspend fun setAutoDownloadUpdates(value: Boolean) = edit { it[Keys.autoDownloadUpdates] = value }
    suspend fun setQuietHours(startMinutes: Int, endMinutes: Int) = edit {
        it[Keys.quietStart] = startMinutes.coerceIn(0, 1439)
        it[Keys.quietEnd] = endMinutes.coerceIn(0, 1439)
    }
    suspend fun setOnboardingComplete(value: Boolean) = edit { it[Keys.onboardingComplete] = value }
    suspend fun setWeekStartsMonday(value: Boolean) = edit { it[Keys.weekStartsMonday] = value }
    suspend fun setDefaultFocusMinutes(value: Int) = edit { it[Keys.defaultFocusMinutes] = value.coerceIn(5, 180) }
    suspend fun setReduceMotion(value: Boolean) = edit { it[Keys.reduceMotion] = value }
    suspend fun setCompactNavigation(value: Boolean) = edit { it[Keys.compactNavigation] = value }
    suspend fun setDarkMode(enabled: Boolean) = setThemeMode(if (enabled) ThemeMode.DARK else ThemeMode.LIGHT)

    suspend fun exportJson(): JSONObject = preferences.first().toJson()

    suspend fun snapshot(): UserPreferences = preferences.first()

    /** Decodes a backup without mutating DataStore. Every v3 field is type- and range-checked. */
    fun decodeBackupJson(json: JSONObject): UserPreferences = json.toValidatedUserPreferences()

    /** Overlay activation is deliberately disabled until the user opens Ordia in a visible context. */
    suspend fun restoreSnapshot(value: UserPreferences, allowGuardianEnabled: Boolean = false) {
        edit { values ->
            values.clear()
            write(values, if (allowGuardianEnabled) value else value.copy(guardianEnabled = false))
        }
    }

    suspend fun restoreJson(json: JSONObject) = restoreSnapshot(decodeBackupJson(json))

    private fun toUserPreferences(values: androidx.datastore.preferences.core.Preferences): UserPreferences {
        val today = LocalDate.now().toEpochDay()
        val legacyDark = values[Keys.legacyDarkMode]
        val theme = values[Keys.themeMode]?.toEnumOrNull<ThemeMode>()
            ?: if (legacyDark == true) ThemeMode.DARK else ThemeMode.SYSTEM
        val species = values[Keys.guardianSpecies]?.toEnumOrNull<GuardianSpecies>() ?: GuardianSpecies.LUMI
        return UserPreferences(
            themeMode = theme,
            interfaceMode = values[Keys.interfaceMode]?.toEnumOrNull<InterfaceMode>() ?: InterfaceMode.ORGANIZED,
            guardianEnabled = values[Keys.guardianEnabled] ?: false,
            guardianMode = values[Keys.guardianMode]?.toEnumOrNull<GuardianMode>() ?: GuardianMode.DISCREET,
            guardianName = values[Keys.guardianName]?.take(24).orEmpty().ifBlank { species.defaultName },
            guardianSpecies = species,
            guardianBond = (values[Keys.guardianBond] ?: 0).coerceIn(0, 9_999),
            guardianExperience = (values[Keys.guardianExperience] ?: 0).coerceIn(0, MAX_GUARDIAN_EXPERIENCE),
            guardianLastInteraction = (values[Keys.guardianLastInteraction] ?: 0L).coerceAtLeast(0L),
            guardianLastEvent = values[Keys.guardianLastEvent]?.take(40) ?: "welcome",
            guardianAnimations = values[Keys.guardianAnimations] ?: true,
            guardianInteractionEpochDay = values[Keys.guardianInteractionDay] ?: today,
            guardianInteractionsToday = if ((values[Keys.guardianInteractionDay] ?: today) == today)
                (values[Keys.guardianInteractionsToday] ?: 0).coerceIn(0, DAILY_INTERACTION_LIMIT) else 0,
            autoUpdateEnabled = values[Keys.autoUpdateEnabled] ?: true,
            autoDownloadUpdates = values[Keys.autoDownloadUpdates] ?: true,
            quietStartMinutes = (values[Keys.quietStart] ?: 22 * 60).coerceIn(0, 1439),
            quietEndMinutes = (values[Keys.quietEnd] ?: 7 * 60).coerceIn(0, 1439),
            onboardingComplete = values[Keys.onboardingComplete] ?: false,
            weekStartsMonday = values[Keys.weekStartsMonday] ?: true,
            defaultFocusMinutes = (values[Keys.defaultFocusMinutes] ?: 25).coerceIn(5, 180),
            reduceMotion = values[Keys.reduceMotion] ?: false,
            compactNavigation = values[Keys.compactNavigation] ?: false
        )
    }

    private fun UserPreferences.toJson(): JSONObject = JSONObject()
        .put("themeMode", themeMode.name)
        .put("interfaceMode", interfaceMode.name)
        .put("guardianEnabled", guardianEnabled)
        .put("guardianMode", guardianMode.name)
        .put("guardianName", guardianName)
        .put("guardianSpecies", guardianSpecies.name)
        .put("guardianBond", guardianBond)
        .put("guardianExperience", guardianExperience)
        .put("guardianLastInteraction", guardianLastInteraction)
        .put("guardianLastEvent", guardianLastEvent)
        .put("guardianAnimations", guardianAnimations)
        .put("guardianInteractionEpochDay", guardianInteractionEpochDay)
        .put("guardianInteractionsToday", guardianInteractionsToday)
        .put("autoUpdateEnabled", autoUpdateEnabled)
        .put("autoDownloadUpdates", autoDownloadUpdates)
        .put("quietStartMinutes", quietStartMinutes)
        .put("quietEndMinutes", quietEndMinutes)
        .put("onboardingComplete", onboardingComplete)
        .put("weekStartsMonday", weekStartsMonday)
        .put("defaultFocusMinutes", defaultFocusMinutes)
        .put("reduceMotion", reduceMotion)
        .put("compactNavigation", compactNavigation)

    private fun JSONObject.toValidatedUserPreferences(): UserPreferences {
        val required = setOf(
            "themeMode", "interfaceMode", "guardianEnabled", "guardianMode", "guardianName",
            "guardianSpecies", "guardianBond", "guardianExperience", "guardianLastInteraction",
            "guardianLastEvent", "guardianAnimations", "guardianInteractionEpochDay",
            "guardianInteractionsToday", "autoUpdateEnabled", "autoDownloadUpdates",
            "quietStartMinutes", "quietEndMinutes", "onboardingComplete", "weekStartsMonday",
            "defaultFocusMinutes", "reduceMotion", "compactNavigation"
        )
        val missing = required.filter { !has(it) }
        require(missing.isEmpty()) { "Faltan ajustes en la copia: ${missing.sorted().joinToString()}." }

        val species = requiredEnum<GuardianSpecies>("guardianSpecies")
        val name = requiredString("guardianName")
        require(name == name.trim() && name.isNotBlank() && name.length <= 24 && name.none(Char::isISOControl) && hasValidUnicodeScalars(name)) {
            "El nombre del guardián no es válido."
        }
        val lastEvent = requiredString("guardianLastEvent")
        require(hasValidUnicodeScalars(lastEvent)) { "El evento del guardián contiene Unicode inválido." }
        require(lastEvent in ALLOWED_GUARDIAN_EVENTS) { "El último evento del guardián no es reconocido." }
        val bond = requiredInt("guardianBond")
        require(bond in 0..9_999) { "El vínculo del guardián está fuera de rango." }
        val experience = requiredInt("guardianExperience")
        require(experience in 0..MAX_GUARDIAN_EXPERIENCE) { "La experiencia del guardián está fuera de rango." }
        val lastInteraction = requiredLong("guardianLastInteraction")
        require(lastInteraction in 0L..32_503_680_000_000L) { "La fecha de interacción del guardián no es válida." }
        val interactionDay = requiredLong("guardianInteractionEpochDay")
        require(interactionDay in -1_000_000L..1_000_000L) { "El día de interacción del guardián no es válido." }
        val interactionsToday = requiredInt("guardianInteractionsToday")
        require(interactionsToday in 0..DAILY_INTERACTION_LIMIT) { "El contador diario del guardián no es válido." }
        val quietStart = requiredInt("quietStartMinutes")
        val quietEnd = requiredInt("quietEndMinutes")
        require(quietStart in 0..1439 && quietEnd in 0..1439) { "Las horas silenciosas no son válidas." }
        val focusMinutes = requiredInt("defaultFocusMinutes")
        require(focusMinutes in 5..180) { "La duración de enfoque no es válida." }

        return UserPreferences(
            themeMode = requiredEnum("themeMode"),
            interfaceMode = requiredEnum("interfaceMode"),
            guardianEnabled = requiredBoolean("guardianEnabled"),
            guardianMode = requiredEnum("guardianMode"),
            guardianName = name,
            guardianSpecies = species,
            guardianBond = bond,
            guardianExperience = experience,
            guardianLastInteraction = lastInteraction,
            guardianLastEvent = lastEvent,
            guardianAnimations = requiredBoolean("guardianAnimations"),
            guardianInteractionEpochDay = interactionDay,
            guardianInteractionsToday = interactionsToday,
            autoUpdateEnabled = requiredBoolean("autoUpdateEnabled"),
            autoDownloadUpdates = requiredBoolean("autoDownloadUpdates"),
            quietStartMinutes = quietStart,
            quietEndMinutes = quietEnd,
            onboardingComplete = requiredBoolean("onboardingComplete"),
            weekStartsMonday = requiredBoolean("weekStartsMonday"),
            defaultFocusMinutes = focusMinutes,
            reduceMotion = requiredBoolean("reduceMotion"),
            compactNavigation = requiredBoolean("compactNavigation")
        )
    }

    private fun JSONObject.requiredString(name: String): String {
        require(has(name) && !isNull(name)) { "Falta el ajuste $name." }
        val value = get(name)
        require(value is String) { "$name debe ser texto." }
        return value
    }

    private fun JSONObject.requiredBoolean(name: String): Boolean {
        require(has(name) && !isNull(name)) { "Falta el ajuste $name." }
        val value = get(name)
        require(value is Boolean) { "$name debe ser verdadero o falso." }
        return value
    }

    private fun JSONObject.requiredInt(name: String): Int {
        val value = requiredLong(name)
        require(value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "$name está fuera de rango." }
        return value.toInt()
    }

    private fun JSONObject.requiredLong(name: String): Long {
        require(has(name) && !isNull(name)) { "Falta el ajuste $name." }
        val value = get(name)
        require(value is Number) { "$name debe ser numérico." }
        val asDouble = value.toDouble()
        val asLong = value.toLong()
        require(asDouble.isFinite() && asDouble == asLong.toDouble()) { "$name debe ser un entero." }
        return asLong
    }

    private inline fun <reified T : Enum<T>> JSONObject.requiredEnum(name: String): T {
        val raw = requiredString(name)
        return runCatching { enumValueOf<T>(raw) }
            .getOrElse { error("$name contiene un valor desconocido.") }
    }


    private fun hasValidUnicodeScalars(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            val char = value[index]
            when {
                char.isHighSurrogate() -> {
                    if (index + 1 >= value.length || !value[index + 1].isLowSurrogate()) return false
                    index += 2
                }
                char.isLowSurrogate() -> return false
                else -> index++
            }
        }
        return true
    }

    private fun write(values: MutablePreferences, value: UserPreferences) {
        values[Keys.themeMode] = value.themeMode.name
        values[Keys.interfaceMode] = value.interfaceMode.name
        values[Keys.guardianEnabled] = value.guardianEnabled
        values[Keys.guardianMode] = value.guardianMode.name
        values[Keys.guardianName] = value.guardianName
        values[Keys.guardianSpecies] = value.guardianSpecies.name
        values[Keys.guardianBond] = value.guardianBond
        values[Keys.guardianExperience] = value.guardianExperience
        values[Keys.guardianLastInteraction] = value.guardianLastInteraction
        values[Keys.guardianLastEvent] = value.guardianLastEvent
        values[Keys.guardianAnimations] = value.guardianAnimations
        values[Keys.guardianInteractionDay] = value.guardianInteractionEpochDay
        values[Keys.guardianInteractionsToday] = value.guardianInteractionsToday
        values[Keys.autoUpdateEnabled] = value.autoUpdateEnabled
        values[Keys.autoDownloadUpdates] = value.autoDownloadUpdates
        values[Keys.quietStart] = value.quietStartMinutes
        values[Keys.quietEnd] = value.quietEndMinutes
        values[Keys.onboardingComplete] = value.onboardingComplete
        values[Keys.weekStartsMonday] = value.weekStartsMonday
        values[Keys.defaultFocusMinutes] = value.defaultFocusMinutes
        values[Keys.reduceMotion] = value.reduceMotion
        values[Keys.compactNavigation] = value.compactNavigation
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.ordiaDataStore.edit { values -> block(values) }
    }

    private inline fun <reified T : Enum<T>> String.toEnumOrNull(): T? =
        runCatching { enumValueOf<T>(this) }.getOrNull()

    companion object {
        const val DAILY_INTERACTION_LIMIT = 12
        private const val MAX_GUARDIAN_EXPERIENCE = 100_000
        private val ALLOWED_GUARDIAN_EVENTS = setOf(
            "welcome", "rename", "evolve", "pet", "play", "feed", "talk", "rest", "progress"
        )
    }
}
