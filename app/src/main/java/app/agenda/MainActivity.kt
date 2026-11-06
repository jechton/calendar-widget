package app.agenda

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.agenda.domain.AgendaSettings
import app.agenda.domain.Preset
import app.agenda.domain.WidgetLayout
import app.agenda.ui.AccountGroup
import app.agenda.ui.AgendaTheme
import app.agenda.ui.EnumChoice
import app.agenda.ui.RegexEditor
import app.agenda.ui.Section
import app.agenda.ui.SliderRow
import app.agenda.ui.SwitchRow
import app.agenda.ui.SynonymEditor
import app.agenda.ui.WidgetPreview
import kotlin.math.roundToInt

/**
 * The app's only screen. Android starts it two ways: from the launcher icon, and when a widget is added or
 * reconfigured (APPWIDGET_CONFIGURE in the manifest). The UI is Jetpack Compose: [SettingsScreen] is a function that
 * describes the screen from the current [UiState], and it is called again whenever that state changes.
 */
class MainActivity : ComponentActivity() {
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Widget picker/reconfigure launches us with an id and expects RESULT_OK back, else the widget is dropped.
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        setContent {
            AgendaTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.reload() }
                SettingsScreen(state, viewModel::edit, viewModel::reload)
            }
        }
    }
}

/**
 * Widget preview on top, then the settings grouped in sections. Every control calls [edit] with a function that
 * returns a changed copy of the settings, e.g. `edit { it.copy(layout = l) }`. Settings are immutable; a change is
 * always a new copy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    state: UiState,
    edit: ((AgendaSettings) -> AgendaSettings) -> Unit,
    onPermission: () -> Unit,
) {
    val s = state.settings
    Scaffold(topBar = {
        TopAppBar(title = { Text("Agenda", style = MaterialTheme.typography.headlineSmall) })
    }) { padding ->
        Column(Modifier.padding(padding)) {
            WidgetPreview(s, Modifier.padding(horizontal = 16.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (!state.permission) {
                    item { PermissionCard(onPermission) }
                }
                item {
                    Section("Presets") {
                        FlowRow(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Preset.entries.forEach { p ->
                                AssistChip(onClick = { edit(p.apply) }, label = { Text(p.label) })
                            }
                        }
                    }
                }
                item {
                    Section("Layout") {
                        EnumChoice("Layout", s.layout) { l -> edit { it.copy(layout = l) } }
                        if (s.layout == WidgetLayout.LIST) {
                            SwitchRow("Always show end times", s.alwaysShowEndTime) { v ->
                                edit { it.copy(alwaysShowEndTime = v) }
                            }
                        }
                        EnumChoice("Spacing", s.density) { d -> edit { it.copy(density = d) } }
                        SliderRow(
                            "Corner radius: ${s.cornerRadius} dp",
                            s.cornerRadius.toFloat(),
                            0f..MAX_CORNER_RADIUS.toFloat(),
                            MAX_CORNER_RADIUS / CORNER_RADIUS_STEP - 1,
                        ) { v -> edit { it.copy(cornerRadius = v.roundToInt()) } }
                        if (s.layout == WidgetLayout.CARDS) {
                            SliderRow(
                                "Date column width: ${s.dateColumnWidth} dp",
                                s.dateColumnWidth.toFloat(),
                                MIN_DATE_WIDTH.toFloat()..MAX_DATE_WIDTH.toFloat(),
                                (MAX_DATE_WIDTH - MIN_DATE_WIDTH) / DATE_STEP - 1,
                            ) { v -> edit { it.copy(dateColumnWidth = v.roundToInt()) } }
                            SwitchRow("Title and time on one line", s.singleLineCards) { v ->
                                edit { it.copy(singleLineCards = v) }
                            }
                        }
                        SliderRow(
                            "Events per day: ${if (s.maxEventsPerDay == 0) "no limit" else s.maxEventsPerDay}",
                            (if (s.maxEventsPerDay == 0) EVENTS_SLIDER_MAX else s.maxEventsPerDay).toFloat(),
                            1f..EVENTS_SLIDER_MAX.toFloat(),
                            EVENTS_SLIDER_MAX - 2,
                        ) { v ->
                            // The top of the slider means "no limit", stored as 0.
                            val limit = v.roundToInt().takeIf { n -> n < EVENTS_SLIDER_MAX } ?: 0
                            edit { it.copy(maxEventsPerDay = limit) }
                        }
                    }
                }
                item {
                    Section("Appearance") {
                        EnumChoice("Widget theme", s.themeMode) { m -> edit { it.copy(themeMode = m) } }
                        SliderRow(
                            "Widget background: ${(s.backgroundOpacity * 100).roundToInt()}%",
                            s.backgroundOpacity,
                            0f..1f,
                            OPACITY_SLIDER_STEPS,
                        ) { v ->
                            edit { it.copy(backgroundOpacity = v) }
                        }
                        SliderRow("Text size: ${(s.textScale * 100).roundToInt()}%", s.textScale, 0.8f..1.5f) { v ->
                            edit { it.copy(textScale = v) }
                        }
                    }
                }
                item {
                    Section("Content") {
                        SliderRow(
                            "Days ahead: ${s.daysAhead}",
                            s.daysAhead.toFloat(),
                            1f..MAX_DAYS_AHEAD.toFloat(),
                            MAX_DAYS_AHEAD - 2,
                        ) { v ->
                            edit { it.copy(daysAhead = v.toInt()) }
                        }
                        SwitchRow("Week labels", s.weekDividers) { v -> edit { it.copy(weekDividers = v) } }
                        SwitchRow("Show description", s.showDetails) { v -> edit { it.copy(showDetails = v) } }
                        SwitchRow("Join link for video calls", s.tapToJoin) { v -> edit { it.copy(tapToJoin = v) } }
                    }
                }
                item {
                    Section("Calendars") {
                        state.calendars.groupBy { it.account }.forEach { (account, cals) ->
                            AccountGroup(account, cals, s) { change -> edit(change) }
                        }
                    }
                }
                item {
                    Section("Hide events") {
                        SwitchRow("Hide declined events", s.hideDeclined) { v -> edit { it.copy(hideDeclined = v) } }
                        SwitchRow("Hide events after they end", s.hideFinished) { v ->
                            edit { it.copy(hideFinished = v) }
                        }
                        if (s.hideFinished) {
                            SliderRow(
                                "Keep visible for ${s.hideOffsetMinutes} min after the end",
                                s.hideOffsetMinutes.toFloat(),
                                0f..MAX_OFFSET_MINUTES.toFloat(),
                            ) { v -> edit { it.copy(hideOffsetMinutes = v.snapTo(OFFSET_STEP_MINUTES)) } }
                        }
                        SwitchRow("Dim events after they end", s.dimFinished) { v -> edit { it.copy(dimFinished = v) } }
                        if (s.dimFinished) {
                            SliderRow(
                                "Dim ${s.dimOffsetMinutes} min after the end",
                                s.dimOffsetMinutes.toFloat(),
                                0f..MAX_OFFSET_MINUTES.toFloat(),
                            ) { v -> edit { it.copy(dimOffsetMinutes = v.snapTo(OFFSET_STEP_MINUTES)) } }
                        }
                    }
                }
                item {
                    Section("Duplicates") {
                        SwitchRow(
                            "Hide duplicates across calendars",
                            s.dedupe,
                            "Same title, start and end. The copy with the most info (location, notes) is kept.",
                        ) { v -> edit { it.copy(dedupe = v) } }
                        SwitchRow("Prefer the longer title", s.preferLongTitle, "Used when info is equal") { v ->
                            edit { it.copy(preferLongTitle = v) }
                        }
                        RegexEditor(s.ignoreRegexes) { list -> edit { it.copy(ignoreRegexes = list) } }
                        SynonymEditor(s.synonyms) { list -> edit { it.copy(synonyms = list) } }
                        if (state.merges.isNotEmpty()) {
                            Text(
                                "Merged right now",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                            state.merges.take(MAX_MERGES_SHOWN).forEach { m ->
                                Text(
                                    "${m.kept.title}  (hid: ${m.dropped.joinToString { it.title }})",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Slider limits for the settings below. Slider "steps" count the stops between the two ends.
private const val MAX_MERGES_SHOWN = 10
private const val OFFSET_STEP_MINUTES = 5
private const val MAX_OFFSET_MINUTES = 240
private const val MAX_DAYS_AHEAD = 14
private const val OPACITY_SLIDER_STEPS = 9
private const val MAX_CORNER_RADIUS = 32
private const val CORNER_RADIUS_STEP = 4

// The events-per-day slider ends at this value, which stands for "no limit" (stored as 0).
private const val EVENTS_SLIDER_MAX = 11
private const val MIN_DATE_WIDTH = 40
private const val MAX_DATE_WIDTH = 72
private const val DATE_STEP = 4

@Composable
private fun PermissionCard(onResult: () -> Unit) {
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onResult() }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Agenda needs to read your calendars to fill the widget.")
            FilledTonalButton(onClick = { launcher.launch(Manifest.permission.READ_CALENDAR) }) {
                Text("Grant calendar access")
            }
        }
    }
}

/** Rounds down to a multiple of [step], so a smooth slider moves in fixed jumps. */
private fun Float.snapTo(step: Int): Int = toInt() / step * step
