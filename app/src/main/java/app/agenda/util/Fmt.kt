package app.agenda.util

import android.content.Context
import android.text.format.DateFormat
import app.agenda.domain.CalEvent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object Fmt {
    private fun time(
        t: LocalTime,
        is24: Boolean,
        meridiem: Boolean,
    ): String {
        if (is24) return DateTimeFormatter.ofPattern("HH:mm").format(t)
        val base = if (t.minute == 0) "h" else "h:mm"
        return DateTimeFormatter.ofPattern(if (meridiem) "$base a" else base, Locale.getDefault()).format(t)
    }

    /** First and last local date the event touches. */
    private fun span(
        e: CalEvent,
        zone: ZoneId,
    ): Pair<LocalDate, LocalDate> {
        val first = Instant.ofEpochMilli(e.beginMillis).atZone(zone).toLocalDate()
        if (e.endMillis <= e.beginMillis) return first to first
        return first to Instant.ofEpochMilli(e.endMillis - 1).atZone(zone).toLocalDate()
    }

    /** "Offsite (Day 2/3)" on multi-day events, else the plain title. */
    fun title(
        e: CalEvent,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val (first, last) = span(e, zone)
        val total = ChronoUnit.DAYS.between(first, last) + 1
        if (total <= 1) return e.title
        return "${e.title} (Day ${ChronoUnit.DAYS.between(first, date) + 1}/$total)"
    }

    /** "5:30 – 7:30 PM", "11 AM – 1 PM", or "All day". On multi-day events: the start time, "Until 4 PM", or "All day". */
    fun range(
        context: Context,
        e: CalEvent,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val (first, last) = span(e, zone)
        if (e.allDay || (first < date && date < last)) return "All day"
        val is24 = DateFormat.is24HourFormat(context)
        val begin = Instant.ofEpochMilli(e.beginMillis).atZone(zone).toLocalTime()
        val end = Instant.ofEpochMilli(e.endMillis).atZone(zone).toLocalTime()
        if (e.endMillis <= e.beginMillis) return time(begin, is24, true)
        if (date == last && first < last) return "Until ${time(end, is24, true)}"
        if (first < last) return time(begin, is24, true)
        val sameHalf = (begin.hour < 12) == (end.hour < 12)
        return "${time(begin, is24, !sameHalf)} – ${time(end, is24, true)}"
    }
}
