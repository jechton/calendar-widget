package app.agenda.data

import android.content.Context
import app.agenda.domain.AgendaSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
