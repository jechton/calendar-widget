package app.agenda.data

import android.content.Context
import app.agenda.domain.AgendaSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Saves the settings as one JSON string in SharedPreferences, Android's small key-value file for an app.
 * A missing or unreadable value yields the defaults, and unknown keys are ignored, so adding or removing a setting
 * in [AgendaSettings] never breaks an existing install.
 */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("agenda", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): AgendaSettings =
        prefs.getString(KEY, null)
            ?.let { runCatching { json.decodeFromString<AgendaSettings>(it) }.getOrNull() }
            ?: AgendaSettings()

    fun save(settings: AgendaSettings) {
        prefs.edit().putString(KEY, json.encodeToString(settings)).apply()
    }

    private companion object {
        const val KEY = "settings"
    }
}
