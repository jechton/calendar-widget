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

/** Identifies one entry in an account's event color palette. */
private data class PaletteKey(val accountType: String?, val accountName: String?, val colorKey: String?)

/**
 * Positions of the columns requested in [CalendarRepository.events]. A database cursor hands values back by
 * position, so these must stay in the same order as the projection array there.
 */
private object Col {
    const val EVENT_ID = 0
    const val CALENDAR_ID = 1
    const val TITLE = 2
    const val BEGIN = 3
    const val END = 4
    const val ALL_DAY = 5
    const val LOCATION = 6
    const val DESCRIPTION = 7
    const val EVENT_COLOR = 8
    const val CALENDAR_COLOR = 9
    const val SELF_STATUS = 10
    const val COLOR_KEY = 11
    const val ACCOUNT_NAME = 12
    const val ACCOUNT_TYPE = 13
}

/**
 * Reads the phone's calendars through Android's Calendar Provider, the shared database that Google Calendar,
 * Samsung Calendar and others sync into. Reading it needs the READ_CALENDAR permission, which the user grants
 * in the app. Nothing here writes to the calendar.
 */
class CalendarRepository(private val context: Context) {
    /** True once the user has granted calendar access. Every read below returns nothing without it. */
    fun hasPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /** Every calendar on the phone, including ones the user has switched off in their calendar app. */
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
        // Dispatchers.IO moves the database read off the main (UI) thread, which Android requires.
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
            // Each account has its own palette, hence the three-part key.
            val eventColors = mutableMapOf<PaletteKey, Int>()
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
                while (c.moveToNext()) {
                    val key = PaletteKey(c.getString(0), c.getString(1), c.getString(2))
                    eventColors[key] = c.getInt(3)
                }
            }
            val out = mutableListOf<CalEvent>()
            context.contentResolver.query(uri, projection, null, null, "${Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) {
                    val declined = c.getInt(Col.SELF_STATUS) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED
                    if (hideDeclined && declined) continue
                    val allDay = c.getInt(Col.ALL_DAY) == 1
                    var begin = c.getLong(Col.BEGIN)
                    var end = c.getLong(Col.END)
                    if (allDay) {
                        // All-day instances are stored as UTC midnights; shift to the same dates in local time.
                        begin = utcDateToLocalMillis(begin, zone)
                        end = utcDateToLocalMillis(end, zone)
                    }
                    val paletteKey =
                        PaletteKey(
                            accountType = c.getString(Col.ACCOUNT_TYPE),
                            accountName = c.getString(Col.ACCOUNT_NAME),
                            colorKey = c.getString(Col.COLOR_KEY),
                        )
                    val own = eventColors[paletteKey] ?: c.getInt(Col.EVENT_COLOR).takeIf { it != 0 }
                    val color = own ?: c.getInt(Col.CALENDAR_COLOR).takeIf { it != 0 } ?: Color.GRAY
                    out +=
                        CalEvent(
                            id = c.getLong(Col.EVENT_ID),
                            calendarId = c.getLong(Col.CALENDAR_ID),
                            title = c.getString(Col.TITLE).orEmpty().ifBlank { "(No title)" },
                            beginMillis = begin,
                            endMillis = end,
                            allDay = allDay,
                            location = c.getString(Col.LOCATION).orEmpty(),
                            description = c.getString(Col.DESCRIPTION).orEmpty(),
                            color = color,
                            ownColor = own != null,
                        )
                }
            }
            out
        }

    /**
     * Everything the widget needs for one draw: events for the window, filtered, plus when to redraw next.
     * Reads the saved settings itself unless the caller passes some (the settings screen passes its own).
     */
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

    /** "2026-11-05 00:00 UTC" becomes "2026-11-05 00:00 in [zone]", so all-day events land on the right local day. */
    private fun utcDateToLocalMillis(
        utcMillis: Long,
        zone: ZoneId,
    ): Long {
        val date = Instant.ofEpochMilli(utcMillis).atOffset(ZoneOffset.UTC).toLocalDate()
        return date.atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
