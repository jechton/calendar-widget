package app.agenda

import android.Manifest
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.ScrollView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.children
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.runComposition
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.agenda.domain.AgendaSettings
import app.agenda.domain.CalInfo
import app.agenda.domain.EventFilter
import app.agenda.domain.WidgetLayout
import app.agenda.ui.AgendaTheme
import app.agenda.widget.AgendaWidget
import app.agenda.widget.PREVIEW_ROWS
import kotlin.math.roundToInt

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
                        if (state.merges.isNotEmpty()) {
                            Text(
                                "Merged right now",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                            state.merges.take(MAX_PREVIEW).forEach { m ->
                                Text(
                                    "${m.kept.title}  (hid: ${m.dropped.joinToString { it.title }})",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }
                    }
                }
                item {
                    Section("Finished events") {
                        SwitchRow("Hide events after they end", s.hideFinished) { v ->
                            edit { it.copy(hideFinished = v) }
                        }
                        if (s.hideFinished) {
                            SliderRow(
                                "Keep visible for ${s.hideOffsetMinutes} min after the end",
                                s.hideOffsetMinutes.toFloat(),
                                0f..MAX_OFFSET.toFloat(),
                            ) { v -> edit { it.copy(hideOffsetMinutes = (v / STEP).toInt() * STEP) } }
                        }
                    }
                }
                item {
                    Section("Calendars") {
                        state.calendars.forEach { cal -> CalendarRow(cal, s) { change -> edit(change) } }
                    }
                }
                item {
                    Section("Display") {
                        SliderRow(
                            "Days ahead: ${s.daysAhead}",
                            s.daysAhead.toFloat(),
                            1f..MAX_DAYS.toFloat(),
                            MAX_DAYS - 2,
                        ) { v ->
                            edit { it.copy(daysAhead = v.toInt()) }
                        }
                        SliderRow("Text size: ${(s.textScale * 100).roundToInt()}%", s.textScale, 0.8f..1.5f) { v ->
                            edit { it.copy(textScale = v) }
                        }
                        SliderRow(
                            "Widget background: ${(s.backgroundOpacity * 100).roundToInt()}%",
                            s.backgroundOpacity,
                            0f..1f,
                            OPACITY_STEPS,
                        ) { v ->
                            edit { it.copy(backgroundOpacity = v) }
                        }
                        EnumChoice("Layout", s.layout) { l -> edit { it.copy(layout = l) } }
                        if (s.layout == WidgetLayout.LIST) {
                            SwitchRow("Always show end times", s.alwaysShowEndTime) { v ->
                                edit { it.copy(alwaysShowEndTime = v) }
                            }
                        }
                        EnumChoice("Widget theme", s.themeMode) { m -> edit { it.copy(themeMode = m) } }
                        SliderRow(
                            "Corner radius: ${s.cornerRadius} dp",
                            s.cornerRadius.toFloat(),
                            0f..MAX_RADIUS.toFloat(),
                            MAX_RADIUS / RADIUS_STEP - 1,
                        ) { v -> edit { it.copy(cornerRadius = v.roundToInt()) } }
                        EnumChoice("Spacing", s.density) { d -> edit { it.copy(density = d) } }
                        SliderRow(
                            "Events per day: ${if (s.maxEventsPerDay == 0) "no limit" else s.maxEventsPerDay}",
                            (if (s.maxEventsPerDay == 0) NO_LIMIT else s.maxEventsPerDay).toFloat(),
                            1f..NO_LIMIT.toFloat(),
                            NO_LIMIT - 2,
                        ) { v -> edit { it.copy(maxEventsPerDay = v.roundToInt().takeIf { n -> n < NO_LIMIT } ?: 0) } }
                        if (s.layout == WidgetLayout.CARDS) {
                            SliderRow(
                                "Date column width: ${s.dateColumnWidth} dp",
                                s.dateColumnWidth.toFloat(),
                                MIN_DATE_WIDTH.toFloat()..MAX_DATE_WIDTH.toFloat(),
                                (MAX_DATE_WIDTH - MIN_DATE_WIDTH) / DATE_STEP - 1,
                            ) { v -> edit { it.copy(dateColumnWidth = v.roundToInt()) } }
                        }
                        SwitchRow("Title and time on one line", s.singleLineCards) { v ->
                            edit { it.copy(singleLineCards = v) }
                        }
                        SwitchRow("Hide declined events", s.hideDeclined) { v -> edit { it.copy(hideDeclined = v) } }
                    }
                }
            }
        }
    }
}

private const val MAX_PREVIEW = 10
private const val STEP = 5
private const val MAX_OFFSET = 240
private const val MAX_DAYS = 14
private const val OPACITY_STEPS = 9
private const val MAX_RADIUS = 32
private const val RADIUS_STEP = 4
private const val NO_LIMIT = 11
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

/** A titled group of settings on a tonal surface. */
@Composable
private fun Section(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = MaterialTheme.shapes.extraLarge,
        ) { Column(Modifier.padding(vertical = 8.dp), content = content) }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    supporting: String? = null,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = { AgendaSwitch(checked, onChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun AgendaSwitch(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        thumbContent =
            if (checked) {
                (
                    {
                        Icon(
                            Icons.Default.Check,
                            null,
                            Modifier.size(SwitchDefaults.IconSize),
                        )
                    }
                )
            } else {
                null
            },
    )
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label)
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun RegexEditor(
    patterns: List<String>,
    onChange: (List<String>) -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    val invalid = EventFilter.invalidPatterns(patterns)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Ignore text when comparing titles (regex, case-insensitive)", style = MaterialTheme.typography.labelLarge)
        Text(
            "Example: \\(.*?\\) makes \"Standup (Room 4)\" match \"Standup\".",
            style = MaterialTheme.typography.bodySmall,
        )
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                patterns.forEach { p ->
                    InputChip(
                        selected = p in invalid,
                        onClick = { onChange(patterns - p) },
                        label = { Text(if (p in invalid) "$p  (invalid, ignored)" else p) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                "Remove",
                                Modifier.size(InputChipDefaults.IconSize),
                            )
                        },
                    )
                }
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
            FilledTonalButton(
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
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Box(Modifier.size(14.dp).clip(CircleShape).background(Color(cal.color))) },
        headlineContent = { Text(cal.name) },
        supportingContent = {
            Column {
                if (cal.account != cal.name) Text(cal.account)
                Box {
                    AssistChip(
                        onClick = { menu = true },
                        label = { Text("Hide after end: " + overrideLabel(s.calendarOffsets[cal.id])) },
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
        },
        trailingContent = {
            AgendaSwitch(cal.id !in s.hiddenCalendarIds) { show ->
                edit {
                    it.copy(
                        hiddenCalendarIds = if (show) it.hiddenCalendarIds - cal.id else it.hiddenCalendarIds + cal.id,
                    )
                }
            }
        },
    )
}

private val OFFSET_CHOICES = listOf(0, 15, 30, 60, 120)

private fun overrideLabel(v: Int?) =
    when (v) {
        null -> "use default"
        EventFilter.NEVER_HIDE -> "never"
        0 -> "right away"
        else -> "after $v min"
    }

/** Renders the real widget (not a copy) by running its Glance composition into a RemoteViews. */
@OptIn(ExperimentalGlanceApi::class)
@Composable
private fun WidgetPreview(
    settings: AgendaSettings,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // lazy: needs storage permission on some Android versions; falls back to a gradient so transparency still shows.
    val wallpaper =
        remember {
            runCatching { WallpaperManager.getInstance(context).drawable?.toBitmap()?.asImageBitmap() }.getOrNull()
        }
    // Needs no permission (API 27+): the wallpaper's own palette stands in when the image itself is unreadable.
    val backdrop = remember { wallpaperGradient(context) }
    val views by produceState<RemoteViews?>(null, settings) {
        AgendaWidget(preview = true).runComposition(context, AppWidgetId(PREVIEW_ID)).collect { value = it }
    }
    Box(modifier.fillMaxWidth().height(PREVIEW_HEIGHT.dp).clip(RoundedCornerShape(24.dp))) {
        wallpaper?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            ?: Box(Modifier.fillMaxSize().background(Brush.linearGradient(backdrop)))
        AndroidView(
            factory = { FrameLayout(it) },
            update = { host ->
                host.removeAllViews()
                views?.let { host.addView(it.apply(context, host).also(::disableClicks).also(::scrollRows)) }
            },
            modifier = Modifier.fillMaxSize().clipToBounds(),
        )
    }
}

/** Like the real widget list, only the rows scroll; the header and background stay put. */
private fun scrollRows(root: View) {
    val found = ArrayList<View>()
    root.findViewsWithText(found, PREVIEW_ROWS, View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION)
    val rows = found.firstOrNull() ?: return
    val parent = rows.parent as ViewGroup
    val index = parent.indexOfChild(rows)
    val params = rows.layoutParams
    parent.removeView(rows)
    val scroll = ScrollView(rows.context).apply { addView(rows, FrameLayout.LayoutParams(MATCH, WRAP)) }
    parent.addView(scroll, index, params)
}

/** The preview only shows the widget: drop its tap actions so it can be scrolled without leaving the app. */
private fun disableClicks(view: View) {
    view.setOnClickListener(null)
    view.isClickable = false
    (view as? ViewGroup)?.children?.forEach(::disableClicks)
}

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
private const val PREVIEW_ID = -1
private const val PREVIEW_HEIGHT = 260

@Composable
private inline fun <reified T : Enum<T>> EnumChoice(
    label: String,
    selected: T,
    crossinline onChange: (T) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            enumValues<T>().forEachIndexed { i, mode ->
                SegmentedButton(
                    selected = mode == selected,
                    onClick = { onChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(i, enumValues<T>().size),
                ) { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
            }
        }
    }
}

private val FALLBACK_BACKDROP = listOf(Color(0xFF6A8CAF), Color(0xFFE0A96D))

private fun wallpaperGradient(context: Context): List<Color> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return FALLBACK_BACKDROP
    val manager = WallpaperManager.getInstance(context)
    val c = manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return FALLBACK_BACKDROP
    return listOfNotNull(c.primaryColor, c.secondaryColor, c.tertiaryColor).map { Color(it.toArgb()) }
}
