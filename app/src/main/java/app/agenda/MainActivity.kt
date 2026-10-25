package app.agenda

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.agenda.domain.AgendaSettings
import app.agenda.domain.CalInfo
import app.agenda.domain.EventFilter
import app.agenda.domain.KeepPolicy
import app.agenda.ui.AgendaTheme

class MainActivity : ComponentActivity() {
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AgendaTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.reload() }
                SettingsScreen(state, viewModel::edit, viewModel::reload)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    state: UiState,
    edit: ((AgendaSettings) -> AgendaSettings) -> Unit,
    onPermission: () -> Unit,
) {
    val s = state.settings
    Scaffold(topBar = { TopAppBar(title = { Text("Agenda") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!state.permission) {
                item { PermissionCard(onPermission) }
            }
            item { SectionTitle("Duplicates") }
            item { SwitchRow("Hide duplicates across calendars", s.dedupe) { v -> edit { it.copy(dedupe = v) } } }
            item {
                Text("Duplicates share the same title, start and end.", style = MaterialTheme.typography.bodySmall)
            }
            item { Text("When merging, keep the event with") }
            item {
                RadioRow("Most info (location, notes, longest title)", s.keepPolicy == KeepPolicy.MOST_INFO) {
                    edit { it.copy(keepPolicy = KeepPolicy.MOST_INFO) }
                }
            }
            item {
                RadioRow("Shortest title", s.keepPolicy == KeepPolicy.SHORTEST_TITLE) {
                    edit { it.copy(keepPolicy = KeepPolicy.SHORTEST_TITLE) }
                }
            }
            item { RegexEditor(s.ignoreRegexes) { list -> edit { it.copy(ignoreRegexes = list) } } }
            if (state.merges.isNotEmpty()) {
                item { Text("Merged right now", style = MaterialTheme.typography.labelLarge) }
                items(state.merges.take(MAX_PREVIEW)) { m ->
                    Text(
                        "${m.kept.title}  (hid: ${m.dropped.joinToString { it.title }})",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            item { SectionTitle("Finished events") }
            item { SwitchRow("Hide events after they end", s.hideFinished) { v -> edit { it.copy(hideFinished = v) } } }
            if (s.hideFinished) {
                item {
                    Text("Keep visible for ${s.hideOffsetMinutes} min after the end")
                    Slider(
                        value = s.hideOffsetMinutes.toFloat(),
                        onValueChange = { v -> edit { it.copy(hideOffsetMinutes = (v / STEP).toInt() * STEP) } },
                        valueRange = 0f..MAX_OFFSET.toFloat(),
                    )
                }
            }

            item { SectionTitle("Calendars") }
            items(state.calendars, key = { it.id }) { cal -> CalendarRow(cal, s) { change -> edit(change) } }

            item { SectionTitle("Display") }
            item {
                Text("Days ahead: ${s.daysAhead}")
                Slider(
                    value = s.daysAhead.toFloat(),
                    onValueChange = { v -> edit { it.copy(daysAhead = v.toInt()) } },
                    valueRange = 1f..MAX_DAYS.toFloat(),
                    steps = MAX_DAYS - 2,
                )
            }
            item { SwitchRow("Hide declined events", s.hideDeclined) { v -> edit { it.copy(hideDeclined = v) } } }
        }
    }
}

private const val MAX_PREVIEW = 10
private const val STEP = 5
private const val MAX_OFFSET = 240
private const val MAX_DAYS = 14

@Composable
private fun PermissionCard(onResult: () -> Unit) {
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onResult() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Agenda needs to read your calendars to fill the widget.")
        Button(onClick = { launcher.launch(Manifest.permission.READ_CALENDAR) }) { Text("Grant calendar access") }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().selectable(selected, onClick = onClick, role = Role.RadioButton),
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun RegexEditor(
    patterns: List<String>,
    onChange: (List<String>) -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    val invalid = EventFilter.invalidPatterns(patterns)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Ignore text when comparing titles (regex, case-insensitive)", style = MaterialTheme.typography.labelLarge)
        Text(
            "Example: \\(.*?\\) makes \"Standup (Room 4)\" match \"Standup\".",
            style = MaterialTheme.typography.bodySmall,
        )
        patterns.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (p in invalid) "$p  (invalid, ignored)" else p,
                    color = if (p in invalid) MaterialTheme.colorScheme.error else Color.Unspecified,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onChange(patterns - p) }) { Icon(Icons.Default.Close, "Remove") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("New pattern") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                enabled = draft.isNotBlank() && draft !in patterns,
                onClick = {
                    onChange(patterns + draft)
                    draft = ""
                },
            ) { Text("Add") }
        }
    }
}

@Composable
private fun CalendarRow(
    cal: CalInfo,
    s: AgendaSettings,
    edit: ((AgendaSettings) -> AgendaSettings) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val override = s.calendarOffsets[cal.id]
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(Color(cal.color)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(cal.name)
            Text(cal.account, style = MaterialTheme.typography.bodySmall)
            Box {
                Text(
                    "Hide after end: " + overrideLabel(override),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(role = Role.Button) { menu = true },
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    (listOf<Int?>(null, EventFilter.NEVER_HIDE) + OFFSET_CHOICES).forEach { choice ->
                        DropdownMenuItem(
                            text = { Text(overrideLabel(choice)) },
                            onClick = {
                                menu = false
                                edit {
                                    it.copy(
                                        calendarOffsets =
                                            if (choice == null) {
                                                it.calendarOffsets - cal.id
                                            } else {
                                                it.calendarOffsets + (cal.id to choice)
                                            },
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
        Switch(
            checked = cal.id !in s.hiddenCalendarIds,
            onCheckedChange = { show ->
                edit {
                    it.copy(
                        hiddenCalendarIds = if (show) it.hiddenCalendarIds - cal.id else it.hiddenCalendarIds + cal.id,
                    )
                }
            },
        )
    }
}

private val OFFSET_CHOICES = listOf(0, 15, 30, 60, 120)

private fun overrideLabel(v: Int?) =
    when (v) {
        null -> "use default"
        EventFilter.NEVER_HIDE -> "never"
        0 -> "right away"
        else -> "after $v min"
    }
