package app.agenda.util

import android.content.Context
import android.text.format.DateFormat
import app.agenda.domain.CalEvent
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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

    /** "5:30 – 7:30 PM", "11 AM – 1 PM", or "All day". */
    fun range(
        context: Context,
        e: CalEvent,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        if (e.allDay) return "All day"
        val is24 = DateFormat.is24HourFormat(context)
        val begin = Instant.ofEpochMilli(e.beginMillis).atZone(zone).toLocalTime()
        val end = Instant.ofEpochMilli(e.endMillis).atZone(zone).toLocalTime()
        if (e.endMillis <= e.beginMillis) return time(begin, is24, true)
        val sameHalf = (begin.hour < 12) == (end.hour < 12)
        return "${time(begin, is24, !sameHalf)} – ${time(end, is24, true)}"
    }
}
