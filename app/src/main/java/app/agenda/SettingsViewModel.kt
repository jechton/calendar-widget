package app.agenda

import android.app.Application
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.agenda.data.CalendarRepository
import app.agenda.data.SettingsStore
import app.agenda.domain.AgendaSettings
import app.agenda.domain.CalInfo
import app.agenda.domain.Merge
import app.agenda.widget.AgendaWidget
import app.agenda.work.Refresh
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val permission: Boolean = false,
    val settings: AgendaSettings = AgendaSettings(),
    val calendars: List<CalInfo> = emptyList(),
    /** Duplicates currently merged under the saved rules, for tuning regexes. */
    val merges: List<Merge> = emptyList(),
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app)
    private val repo = CalendarRepository(app)
    private val _state = MutableStateFlow(UiState(settings = store.load()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        reload()
    }

    /** Re-reads permission, calendars and the duplicate preview. */
    fun reload() {
        viewModelScope.launch {
            val permission = repo.hasPermission()
            val calendars = repo.calendars()
            val snapshot = if (permission) repo.snapshot(_state.value.settings) else null
            _state.update {
                it.copy(permission = permission, calendars = calendars, merges = snapshot?.result?.merges.orEmpty())
            }
        }
    }

    fun edit(change: (AgendaSettings) -> AgendaSettings) {
        val updated = change(_state.value.settings)
        store.save(updated)
        _state.update { it.copy(settings = updated) }
        viewModelScope.launch {
            AgendaWidget().updateAll(getApplication())
            Refresh.now(getApplication())
        }
        reload()
    }
}
