package app.agenda.widget

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.agenda.MainActivity
import app.agenda.data.CalendarRepository
import app.agenda.domain.CalEvent
import app.agenda.domain.EventFilter
import app.agenda.util.Fmt
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.time.format.TextStyle as DateTextStyle

private data class DayRow(val date: LocalDate, val events: List<CalEvent>)

class AgendaWidget : GlanceAppWidget() {
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
        val everythingEmpty = rows.all { it.events.isEmpty() }

        provideContent {
            GlanceTheme {
                Column(
                    modifier =
                        GlanceModifier
                            .fillMaxSize()
                            .background(GlanceTheme.colors.widgetBackground)
                            .cornerRadius(24.dp)
                            .padding(12.dp),
                ) {
                    Header(today)
                    Spacer(GlanceModifier.height(8.dp))
                    when {
                        !allowed -> Message("Calendar access needed. Tap to open Agenda.", openApp())
                        everythingEmpty -> Message("Nothing coming up.", openApp())
                        else ->
                            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                                items(rows) { DaySection(context, it, today, zone) }
                            }
                    }
                }
            }
        }
    }
}

private fun openApp(): Action = actionStartActivity<MainActivity>()

private fun newEventAction(): Action =
    actionStartActivity(
        Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

private fun viewEventAction(e: CalEvent): Action =
    actionStartActivity(
        Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.beginMillis)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.endMillis)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

@Composable
private fun Header(today: LocalDate) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = today.month.getDisplayName(DateTextStyle.FULL, Locale.getDefault()),
            modifier = GlanceModifier.defaultWeight().clickable(openApp()),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Medium),
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
                style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 22.sp),
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
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
    )
}

@Composable
private fun DaySection(
    context: Context,
    row: DayRow,
    today: LocalDate,
    zone: ZoneId,
) {
    val isToday = row.date == today
    Row(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                GlanceModifier
                    .width(48.dp)
                    .padding(vertical = 4.dp)
                    .let {
                        if (isToday) it.background(GlanceTheme.colors.primaryContainer).cornerRadius(24.dp) else it
                    },
        ) {
            val color = if (isToday) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSurfaceVariant
            Text(
                text = row.date.dayOfWeek.getDisplayName(DateTextStyle.SHORT, Locale.getDefault()),
                style = TextStyle(color = color, fontSize = 12.sp),
            )
            Text(
                text = row.date.dayOfMonth.toString(),
                style = TextStyle(color = color, fontSize = 18.sp, fontWeight = FontWeight.Medium),
            )
        }
        Spacer(GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            if (row.events.isEmpty()) {
                Text(
                    text = "Nothing else today",
                    modifier = GlanceModifier.padding(vertical = 8.dp),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                )
            }
            row.events.forEach { EventCard(context, it) }
        }
    }
}

@Composable
private fun EventCard(
    context: Context,
    e: CalEvent,
) {
    val bg = Color(e.color).copy(alpha = 1f)
    val fg = if (bg.luminance() > 0.5f) Color.Black else Color.White
    val sub = Fmt.range(context, e) + if (e.location.isNotBlank()) " at ${e.location.lineSequence().first()}" else ""
    Column(
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
    ) {
        Column(
            modifier =
                GlanceModifier
                    .fillMaxWidth()
                    .background(ColorProvider(bg))
                    .cornerRadius(14.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clickable(viewEventAction(e)),
        ) {
            Text(
                text = e.title,
                maxLines = 1,
                style = TextStyle(color = ColorProvider(fg), fontSize = 15.sp, fontWeight = FontWeight.Medium),
            )
            Text(
                text = sub,
                maxLines = 1,
                style = TextStyle(color = ColorProvider(fg), fontSize = 13.sp),
            )
        }
    }
}

class AgendaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgendaWidget()
}
