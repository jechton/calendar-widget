package app.agenda.widget

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.agenda.MainActivity
import app.agenda.data.CalendarRepository
import app.agenda.domain.AgendaSettings
import app.agenda.domain.CalEvent
import app.agenda.domain.EventFilter
import app.agenda.domain.ThemeMode
import app.agenda.domain.WidgetLayout
import app.agenda.domain.detail
import app.agenda.domain.joinUrl
import app.agenda.util.Fmt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import java.time.format.TextStyle as DateTextStyle

/** Marks the rows container in preview mode so the app can make just that part scroll. */
const val PREVIEW_ROWS = "agenda-preview-rows"

// Cards were 14dp inside a 24dp widget.
private const val CARD_RADIUS_RATIO = 14f / 24f
private const val STRIPE_ONE_LINE = 22f
private const val STRIPE_TWO_LINES = 42f
private const val STRIPE_EXTRA_LINE = 18f
private const val DIM_ALPHA = 0.5f

private val LocalTextScale = compositionLocalOf { 1f }
private val LocalNow = compositionLocalOf { 0L }

@Composable
private fun sz(size: Int) = (size * LocalTextScale.current).sp

private data class DayRow(val date: LocalDate, val events: List<CalEvent>)

/** [preview] swaps the lazy list for a plain column: a LazyColumn needs a bound widget id to fill in. */
class AgendaWidget(private val preview: Boolean = false) : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val repo = CalendarRepository(context)
        val allowed = repo.hasPermission()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val snapshot = if (allowed) repo.snapshot() else null
        val rows =
            snapshot?.let {
                val byDay = EventFilter.groupByDay(it.result.events, today, it.settings.daysAhead, zone)
                // Today always shows, and so does every gap day up to the last event.
                val last = maxOf(today, byDay.keys.maxOrNull() ?: today)
                generateSequence(today) { d -> d.plusDays(1).takeIf { it <= last } }
                    .map { d -> DayRow(d, byDay[d] ?: emptyList()) }
                    .toList()
            } ?: emptyList()
        val settings = snapshot?.settings ?: AgendaSettings()
        val now = System.currentTimeMillis()
        val everythingEmpty = rows.all { it.events.isEmpty() }

        provideContent {
            CompositionLocalProvider(LocalTextScale provides settings.textScale, LocalNow provides now) {
                WidgetTheme(settings.themeMode) {
                    val opacity = settings.backgroundOpacity
                    Column(
                        modifier =
                            GlanceModifier
                                .fillMaxSize()
                                .background(
                                    ColorProvider(
                                        GlanceTheme.colors.widgetBackground.getColor(context).copy(alpha = opacity),
                                    ),
                                )
                                .cornerRadius(settings.cornerRadius.dp)
                                .padding(settings.density.outer.dp),
                    ) {
                        val list = settings.layout == WidgetLayout.LIST
                        if (!list) {
                            Header(today)
                            Spacer(GlanceModifier.height(8.dp))
                        }
                        val firstDate = rows.firstOrNull()?.date
                        val section: @Composable (DayRow) -> Unit = {
                            Column(modifier = GlanceModifier.fillMaxWidth()) {
                                if (settings.weekDividers && it.date != firstDate) WeekLabel(it.date)
                                if (list) {
                                    ListDaySection(context, it, today, zone, settings, it.date == firstDate)
                                } else {
                                    DaySection(context, it, today, zone, settings)
                                }
                            }
                        }
                        when {
                            !allowed -> Message("Calendar access needed. Tap to open Agenda.", openApp())
                            everythingEmpty -> EmptyState(settings.daysAhead, snapshot?.upcoming)
                            else ->
                                if (preview) {
                                    Column(
                                        modifier =
                                            GlanceModifier
                                                .fillMaxWidth()
                                                .defaultWeight()
                                                .semantics { contentDescription = PREVIEW_ROWS },
                                    ) { rows.forEach { section(it) } }
                                } else {
                                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                                        items(rows) { section(it) }
                                    }
                                }
                        }
                    }
                }
            }
        }
    }
}

private fun openApp(): Action = actionStartActivity<MainActivity>()

private fun openCalendar(millis: Long = System.currentTimeMillis()): Action =
    actionStartActivity(
        Intent(
            Intent.ACTION_VIEW,
            CalendarContract.CONTENT_URI
                .buildUpon()
                .appendPath("time")
                .appendPath(millis.toString())
                .build(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

private fun newEventAction(): Action =
    actionStartActivity(
        Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

// The Calendar app looks all-day instances up by their stored UTC midnights, not our local-midnight copies.
private fun localMidnightToUtc(millis: Long): Long =
    Instant
        .ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

private fun viewEventAction(e: CalEvent): Action {
    val begin = if (e.allDay) localMidnightToUtc(e.beginMillis) else e.beginMillis
    val end = if (e.allDay) localMidnightToUtc(e.endMillis) else e.endMillis
    return actionStartActivity(
        Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

@Composable
private fun Header(today: LocalDate) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = today.month.getDisplayName(DateTextStyle.FULL, Locale.getDefault()),
            modifier = GlanceModifier.defaultWeight().clickable(openCalendar()),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = sz(20), fontWeight = FontWeight.Medium),
        )
        AddButton()
    }
}

@Composable
private fun AddButton() {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            GlanceModifier
                .width(56.dp)
                .height(32.dp)
                .background(GlanceTheme.colors.primaryContainer)
                .cornerRadius(16.dp)
                .clickable(newEventAction()),
    ) {
        Text(
            text = "+",
            style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = sz(22)),
        )
    }
}

@Composable
private fun ListDaySection(
    context: Context,
    row: DayRow,
    today: LocalDate,
    zone: ZoneId,
    s: AgendaSettings,
    first: Boolean,
) {
    val label = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()).format(row.date)
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        if (!first) {
            Spacer(GlanceModifier.height(s.density.gap.dp))
            Box(
                modifier =
                    GlanceModifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(GlanceTheme.colors.outline),
            ) {}
            Spacer(GlanceModifier.height(s.density.gap.dp))
        }
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (row.date == today) "Today, $label" else label,
                modifier =
                    GlanceModifier
                        .defaultWeight()
                        .clickable(openCalendar(row.date.atStartOfDay(zone).toInstant().toEpochMilli())),
                style =
                    TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = sz(18),
                        fontWeight = FontWeight.Medium,
                    ),
            )
            if (first) AddButton()
        }
        if (row.events.isEmpty()) {
            Text(
                text = if (row.date == today) "Nothing else today" else "No events",
                modifier = GlanceModifier.padding(vertical = s.density.gap.dp),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = sz(13)),
            )
        }
        val shown = if (s.maxEventsPerDay > 0) row.events.take(s.maxEventsPerDay) else row.events
        shown.forEach { ListEvent(context, it, row.date, s) }
        val hidden = row.events.size - shown.size
        if (hidden > 0) {
            Text(
                text = "+$hidden more",
                modifier =
                    GlanceModifier
                        .padding(vertical = s.density.gap.dp)
                        .clickable(openCalendar(row.date.atStartOfDay(zone).toInstant().toEpochMilli())),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = sz(12)),
            )
        }
    }
}

@Composable
private fun ListEvent(
    context: Context,
    e: CalEvent,
    date: LocalDate,
    s: AgendaSettings,
) {
    val place = e.location.lineSequence().first()
    val dim = EventFilter.isDimmed(e, s, LocalNow.current)
    val join = e.joinUrl().takeIf { s.tapToJoin && !dim }
    val detail = e.detail().takeIf { s.showDetails && it.isNotEmpty() }
    val twoLines = place.isNotBlank() && !s.singleLineCards
    // lazy: Glance has no stretch-to-row-height, so the stripe height is a per-line estimate scaled by text size.
    val lines = (if (twoLines) 2 else 1) + (if (detail != null) 1 else 0)
    val stripe =
        (if (lines == 1) STRIPE_ONE_LINE else STRIPE_TWO_LINES + (lines - 2) * STRIPE_EXTRA_LINE) *
            LocalTextScale.current
    val main = GlanceTheme.colors.onSurface.dimmed(dim)
    val sub = GlanceTheme.colors.onSurfaceVariant.dimmed(dim)
    Row(
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .padding(vertical = s.density.cardPad.dp)
                .clickable(viewEventAction(e)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                GlanceModifier
                    .width(4.dp)
                    .height(stripe.dp)
                    .background(ColorProvider(Color(e.color).copy(alpha = if (dim) DIM_ALPHA else 1f)))
                    .cornerRadius(2.dp),
        ) {}
        Spacer(GlanceModifier.width(8.dp))
        // Time on the left, then a column so the second line lines up under the title, as in Samsung's calendar.
        Row(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = if (s.alwaysShowEndTime) Fmt.range(context, e, date) else Fmt.start(context, e, date),
                style = TextStyle(color = main, fontSize = sz(15), fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.width(6.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = Fmt.title(e, date),
                    maxLines = 1,
                    style = TextStyle(color = main, fontSize = sz(15)),
                )
                if (twoLines) {
                    Text(
                        text = if (s.alwaysShowEndTime) place else "${Fmt.range(context, e, date)} $place",
                        maxLines = 1,
                        style = TextStyle(color = sub, fontSize = sz(13)),
                    )
                }
                if (detail != null) Text(detail, maxLines = 1, style = TextStyle(color = sub, fontSize = sz(13)))
            }
            if (join != null) JoinLink(join, GlanceTheme.colors.primary)
        }
    }
}

/** Label on the first day of each week, e.g. "Week 44". */
@Composable
private fun WeekLabel(date: LocalDate) {
    val fields = WeekFields.of(Locale.getDefault())
    if (date.dayOfWeek != fields.firstDayOfWeek) return
    Text(
        text = "Week ${date.get(fields.weekOfWeekBasedYear())}",
        modifier = GlanceModifier.padding(top = 8.dp, bottom = 2.dp),
        style = TextStyle(color = GlanceTheme.colors.primary, fontSize = sz(12), fontWeight = FontWeight.Medium),
    )
}

@Composable
private fun EmptyState(
    daysAhead: Int,
    next: CalEvent?,
) {
    val days = if (daysAhead == 1) "today" else "the next $daysAhead days"
    if (next == null) return Message("Nothing in $days.", openApp())
    val dayLabel =
        DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
            .format(Instant.ofEpochMilli(next.beginMillis).atZone(ZoneId.systemDefault()))
    Column(modifier = GlanceModifier.fillMaxWidth().padding(8.dp).clickable(viewEventAction(next))) {
        Text(
            text = "Nothing in $days.",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = sz(14)),
        )
        Text(
            text = "Next: ${next.title}, $dayLabel",
            maxLines = 2,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = sz(14)),
        )
    }
}

private fun joinAction(url: String): Action =
    actionStartActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

/** Fades [color] when [on]; Glance has no alpha modifier, so the colour itself carries it. */
@Composable
private fun ColorProvider.dimmed(on: Boolean): ColorProvider =
    if (on) ColorProvider(getColor(LocalContext.current).copy(alpha = DIM_ALPHA)) else this

@Composable
private fun JoinLink(
    url: String,
    color: ColorProvider,
) {
    Text(
        text = "Join",
        modifier = GlanceModifier.padding(start = 8.dp).clickable(joinAction(url)),
        style = TextStyle(color = color, fontSize = sz(13), fontWeight = FontWeight.Bold),
    )
}

@Composable
private fun Message(
    text: String,
    action: Action,
) {
    Text(
        text = text,
        modifier = GlanceModifier.fillMaxWidth().padding(8.dp).clickable(action),
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = sz(14)),
    )
}

@Composable
private fun DaySection(
    context: Context,
    row: DayRow,
    today: LocalDate,
    zone: ZoneId,
    s: AgendaSettings,
) {
    val isToday = row.date == today
    Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                GlanceModifier
                    .width(s.dateColumnWidth.dp)
                    .padding(vertical = 4.dp)
                    .clickable(openCalendar(row.date.atStartOfDay(zone).toInstant().toEpochMilli()))
                    .let {
                        if (isToday) {
                            it.background(
                                GlanceTheme.colors.primaryContainer,
                            ).cornerRadius(s.cornerRadius.dp)
                        } else {
                            it
                        }
                    },
        ) {
            val color = if (isToday) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSurfaceVariant
            Text(
                text = row.date.dayOfWeek.getDisplayName(DateTextStyle.SHORT, Locale.getDefault()),
                style = TextStyle(color = color, fontSize = sz(12)),
            )
            Text(
                text = row.date.dayOfMonth.toString(),
                style = TextStyle(color = color, fontSize = sz(18), fontWeight = FontWeight.Medium),
            )
        }
        Spacer(GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            if (row.events.isEmpty()) {
                Text(
                    text = if (row.date == today) "Nothing else today" else "No events",
                    modifier = GlanceModifier.padding(vertical = 8.dp),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = sz(13)),
                )
            }
            val shown = if (s.maxEventsPerDay > 0) row.events.take(s.maxEventsPerDay) else row.events
            shown.forEach { EventCard(context, it, row.date, s) }
            val hidden = row.events.size - shown.size
            if (hidden > 0) {
                Text(
                    text = "+$hidden more",
                    modifier =
                        GlanceModifier
                            .padding(bottom = s.density.gap.dp)
                            .clickable(openCalendar(row.date.atStartOfDay(zone).toInstant().toEpochMilli())),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = sz(12)),
                )
            }
        }
    }
}

@Composable
private fun EventCard(
    context: Context,
    e: CalEvent,
    date: LocalDate,
    s: AgendaSettings,
) {
    val dim = EventFilter.isDimmed(e, s, LocalNow.current)
    val join = e.joinUrl().takeIf { s.tapToJoin && !dim }
    val bg = Color(e.color).copy(alpha = if (dim) DIM_ALPHA else 1f)
    val fg = if (Color(e.color).luminance() > 0.5f) Color.Black else Color.White
    val fgColor = ColorProvider(if (dim) fg.copy(alpha = DIM_ALPHA + 0.2f) else fg)
    val place = if (e.location.isNotBlank()) " at ${e.location.lineSequence().first()}" else ""
    val time = Fmt.range(context, e, date)
    val sub = time + place
    val title = TextStyle(color = fgColor, fontSize = sz(15), fontWeight = FontWeight.Medium)
    val detail = TextStyle(color = fgColor, fontSize = sz(13))
    val more = e.detail().takeIf { s.showDetails && it.isNotEmpty() }
    Column(
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .padding(bottom = s.density.gap.dp),
    ) {
        Column(
            modifier =
                GlanceModifier
                    .fillMaxWidth()
                    .background(ColorProvider(bg))
                    .cornerRadius((s.cornerRadius * CARD_RADIUS_RATIO).dp)
                    .padding(horizontal = 12.dp, vertical = s.density.cardPad.dp)
                    .clickable(viewEventAction(e)),
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(Fmt.title(e, date), GlanceModifier.defaultWeight(), maxLines = 1, style = title)
                if (s.singleLineCards) {
                    Spacer(GlanceModifier.width(8.dp))
                    Text(time, maxLines = 1, style = detail)
                }
                if (join != null) JoinLink(join, fgColor)
            }
            if (!s.singleLineCards) Text(sub, maxLines = 1, style = detail)
            if (more != null) Text(more, maxLines = 1, style = detail)
        }
    }
}

class AgendaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgendaWidget()
}

/** Follows the system unless [mode] forces one palette (Material You on Android 12+, static otherwise). */
@Composable
private fun WidgetTheme(
    mode: ThemeMode,
    content: @Composable () -> Unit,
) {
    if (mode == ThemeMode.SYSTEM) return GlanceTheme(content = content)
    val context = LocalContext.current
    val dark = mode == ThemeMode.DARK
    val scheme =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dark -> darkColorScheme()
            else -> lightColorScheme()
        }
    GlanceTheme(colors = ColorProviders(light = scheme, dark = scheme), content = content)
}
