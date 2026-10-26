package app.agenda.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.provider.CalendarContract
import android.provider.CalendarContract.Instances
import androidx.core.content.ContextCompat
import app.agenda.domain.AgendaSettings
import app.agenda.domain.CalEvent
import app.agenda.domain.CalInfo
import app.agenda.domain.Dedupe
import app.agenda.domain.EventFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** What the widget draws, plus when it next needs redrawing. */
data class Snapshot(
    val settings: AgendaSettings,
    val result: Dedupe,
    val nextChangeMillis: Long,
    /** First event after the window, only looked up when the window is empty. */
    val upcoming: CalEvent? = null,
)

private const val UPCOMING_LOOKAHEAD_DAYS = 365L
private const val DAY_MILLIS = 86_400_000L

class CalendarRepository(private val context: Context) {
    fun hasPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun calendars(): List<CalInfo> =
        withContext(Dispatchers.IO) {
            if (!hasPermission()) return@withContext emptyList()
            val projection =
                arrayOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME,
                    CalendarContract.Calendars.CALENDAR_COLOR,
                )
            val out = mutableListOf<CalInfo>()
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                // Instances returns events from unselected calendars too, so list them all or they can't be hidden.
                null,
                null,
                "${CalendarContract.Calendars.ACCOUNT_NAME}, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME}",
            )?.use { c ->
                while (c.moveToNext()) {
                    out += CalInfo(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getInt(3))
                }
            }
            out
        }

    /** Raw instances overlapping [fromMillis, toMillis), before any filtering. */
    suspend fun events(
        fromMillis: Long,
        toMillis: Long,
        zone: ZoneId,
        hideDeclined: Boolean,
    ): List<CalEvent> =
        withContext(Dispatchers.IO) {
            if (!hasPermission()) return@withContext emptyList()
            val uri =
                Instances.CONTENT_URI.buildUpon()
                    .appendPath(fromMillis.toString())
                    .appendPath(toMillis.toString())
                    .build()
            val projection =
                arrayOf(
                    Instances.EVENT_ID,
                    Instances.CALENDAR_ID,
                    Instances.TITLE,
                    Instances.BEGIN,
                    Instances.END,
                    Instances.ALL_DAY,
                    Instances.EVENT_LOCATION,
                    Instances.DESCRIPTION,
                    Instances.EVENT_COLOR,
                    Instances.CALENDAR_COLOR,
                    Instances.SELF_ATTENDEE_STATUS,
                    Instances.EVENT_COLOR_KEY,
                    CalendarContract.Calendars.ACCOUNT_NAME,
                    CalendarContract.Calendars.ACCOUNT_TYPE,
                )
            // Google syncs only a palette key for event colors; the real color lives in the Colors table.
            val eventColors = mutableMapOf<Triple<String?, String?, String?>, Int>()
            context.contentResolver.query(
                CalendarContract.Colors.CONTENT_URI,
                arrayOf(
                    CalendarContract.Colors.ACCOUNT_TYPE,
                    CalendarContract.Colors.ACCOUNT_NAME,
                    CalendarContract.Colors.COLOR_KEY,
                    CalendarContract.Colors.COLOR,
                ),
                "${CalendarContract.Colors.COLOR_TYPE} = ${CalendarContract.Colors.TYPE_EVENT}",
                null,
                null,
            )?.use { c ->
                while (c.moveToNext()) eventColors[Triple(c.getString(0), c.getString(1), c.getString(2))] = c.getInt(3)
            }
            val out = mutableListOf<CalEvent>()
            context.contentResolver.query(uri, projection, null, null, "${Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) {
                    if (hideDeclined && c.getInt(10) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue
                    val allDay = c.getInt(5) == 1
                    var begin = c.getLong(3)
                    var end = c.getLong(4)
                    if (allDay) {
                        // All-day instances are stored as UTC midnights; shift to the same dates in local time.
                        begin = utcDateToLocalMillis(begin, zone)
                        end = utcDateToLocalMillis(end, zone)
                    }
                    val own =
                        eventColors[Triple(c.getString(13), c.getString(12), c.getString(11))]
                            ?: c.getInt(8).takeIf { it != 0 }
                    val color = own ?: c.getInt(9).takeIf { it != 0 } ?: Color.GRAY
                    out +=
                        CalEvent(
                            id = c.getLong(0),
                            calendarId = c.getLong(1),
                            title = c.getString(2).orEmpty().ifBlank { "(No title)" },
                            beginMillis = begin,
                            endMillis = end,
                            allDay = allDay,
                            location = c.getString(6).orEmpty(),
                            description = c.getString(7).orEmpty(),
                            color = color,
                            ownColor = own != null,
                        )
                }
            }
            out
        }

    suspend fun snapshot(
        settings: AgendaSettings = SettingsStore(context).load(),
        now: Long = System.currentTimeMillis(),
    ): Snapshot {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).atStartOfDay(zone)
        val raw =
            events(
                start.toInstant().toEpochMilli(),
                start.plusDays(settings.daysAhead.toLong()).toInstant().toEpochMilli(),
                zone,
                settings.hideDeclined,
            )
        val result = EventFilter.apply(raw, settings, now)
        val upcoming =
            if (result.events.isNotEmpty()) {
                null
            } else {
                val from = start.plusDays(settings.daysAhead.toLong()).toInstant().toEpochMilli()
                val later = events(from, from + UPCOMING_LOOKAHEAD_DAYS * DAY_MILLIS, zone, settings.hideDeclined)
                EventFilter.apply(
                    later,
                    settings,
                    now,
                ).events.minWithOrNull(compareBy({ it.beginMillis }, { it.title }))
            }
        return Snapshot(settings, result, EventFilter.nextChange(raw, settings, now, zone), upcoming)
    }

    private fun utcDateToLocalMillis(
        utcMillis: Long,
        zone: ZoneId,
    ): Long {
        val date = Instant.ofEpochMilli(utcMillis).atOffset(ZoneOffset.UTC).toLocalDate()
        return date.atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
