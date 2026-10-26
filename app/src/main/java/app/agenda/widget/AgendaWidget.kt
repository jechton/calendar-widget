package app.agenda.widget

import android.content.ContentUris
import android.content.Context
import android.content.Intent
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
import app.agenda.util.Fmt
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import java.time.format.TextStyle as DateTextStyle

/** Marks the rows container in preview mode so the app can make just that part scroll. */
const val PREVIEW_ROWS = "agenda-preview-rows"

// Cards were 14dp inside a 24dp widget.
private const val CARD_RADIUS_RATIO = 14f / 24f

private val LocalTextScale = compositionLocalOf { 1f }

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
                // Today always shows, so an empty day still reads as "nothing left".
                (byDay + (today to (byDay[today] ?: emptyList()))).toSortedMap().map { (d, e) -> DayRow(d, e) }
            } ?: emptyList()
        val settings = snapshot?.settings ?: AgendaSettings()
        val everythingEmpty = rows.all { it.events.isEmpty() }

        provideContent {
            CompositionLocalProvider(LocalTextScale provides (settings.textScale)) {
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
                        Header(today)
                        Spacer(GlanceModifier.height(8.dp))
                        when {
                            !allowed -> Message("Calendar access needed. Tap to open Agenda.", openApp())
                            everythingEmpty -> Message("Nothing coming up.", openApp())
                            else ->
                                if (preview) {
                                    Column(
                                        modifier =
                                            GlanceModifier
                                                .fillMaxWidth()
                                                .defaultWeight()
                                                .semantics { contentDescription = PREVIEW_ROWS },
                                    ) { rows.forEach { DaySection(context, it, today, zone, settings) } }
                                } else {
                                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                                        items(rows) { DaySection(context, it, today, zone, settings) }
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
                    text = "Nothing else today",
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
    val bg = Color(e.color).copy(alpha = 1f)
    val fg = if (bg.luminance() > 0.5f) Color.Black else Color.White
    val place = if (e.location.isNotBlank()) " at ${e.location.lineSequence().first()}" else ""
    val time = Fmt.range(context, e, date)
    val sub = time + place
    val title = TextStyle(color = ColorProvider(fg), fontSize = sz(15), fontWeight = FontWeight.Medium)
    val detail = TextStyle(color = ColorProvider(fg), fontSize = sz(13))
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
            if (s.singleLineCards) {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(Fmt.title(e, date), GlanceModifier.defaultWeight(), maxLines = 1, style = title)
                    Spacer(GlanceModifier.width(8.dp))
                    Text(time, maxLines = 1, style = detail)
                }
            } else {
                Text(Fmt.title(e, date), maxLines = 1, style = title)
                Text(sub, maxLines = 1, style = detail)
            }
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
