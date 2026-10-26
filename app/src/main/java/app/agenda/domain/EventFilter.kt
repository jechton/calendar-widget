package app.agenda.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class Merge(val kept: CalEvent, val dropped: List<CalEvent>)

data class Dedupe(val events: List<CalEvent>, val merges: List<Merge>)

object EventFilter {
    const val NEVER_HIDE = -1

    private const val MINUTE = 60_000L

    private data class Key(val title: String, val begin: Long, val end: Long, val allDay: Boolean)

    /** Invalid patterns are skipped; use [invalidPatterns] to report them. */
    fun compile(patterns: List<String>): List<Regex> =
        patterns.mapNotNull { runCatching { Regex(it, RegexOption.IGNORE_CASE) }.getOrNull() }

    fun invalidPatterns(patterns: List<String>): Set<String> =
        patterns.filter { runCatching { Regex(it) }.isFailure }.toSet()

    fun normalizedTitle(
        title: String,
        regexes: List<Regex>,
    ): String {
        val stripped = regexes.fold(title) { acc, r -> acc.replace(r, " ") }
        val clean = stripped.trim().replace(Regex("\\s+"), " ").lowercase()
        return clean.ifEmpty { title.trim().lowercase() }
    }

    private fun info(e: CalEvent) = (if (e.location.isNotBlank()) 1 else 0) + (if (e.description.isNotBlank()) 1 else 0)

    fun dedupe(
        events: List<CalEvent>,
        settings: AgendaSettings,
    ): Dedupe {
        if (!settings.dedupe) return Dedupe(events, emptyList())
        val regexes = compile(settings.ignoreRegexes)
        val kept = mutableListOf<CalEvent>()
        val merges = mutableListOf<Merge>()
        events.groupBy { Key(normalizedTitle(it.title, regexes), it.beginMillis, it.endMillis, it.allDay) }
            .values
            .forEach { group ->
                val winner =
                    group
                        .sortedWith(
                            compareByDescending<CalEvent> { info(it) }
                                .thenBy { if (settings.preferLongTitle) -it.title.length else it.title.length }
                                .thenBy { it.calendarId },
                        ).first()
                kept += winner
                if (group.size > 1) merges += Merge(winner, group - winner)
            }
        return Dedupe(kept.sortedWith(ORDER), merges)
    }

    private val ORDER = compareBy<CalEvent>({ !it.allDay }, { it.beginMillis }, { it.endMillis }, { it.title })

    /** Minutes after the end at which the event disappears, or null if it never does. */
    private fun hideAfterMinutes(
        e: CalEvent,
        s: AgendaSettings,
    ): Int? {
        if (!s.hideFinished && e.calendarId !in s.calendarOffsets) return null
        val offset = s.calendarOffsets[e.calendarId] ?: s.hideOffsetMinutes
        return offset.takeIf { it != NEVER_HIDE }
    }

    fun isFinished(
        e: CalEvent,
        s: AgendaSettings,
        now: Long,
    ): Boolean {
        val offset = hideAfterMinutes(e, s) ?: return false
        // All-day events drop off with the day itself, not at a clock time.
        return !e.allDay && now >= e.endMillis + offset * MINUTE
    }

    fun isDimmed(
        e: CalEvent,
        s: AgendaSettings,
        now: Long,
    ) = s.dimFinished && !e.allDay && now >= e.endMillis + s.dimOffsetMinutes * MINUTE

    /** Hidden calendars, declined events and finished events, then duplicates. */
    fun apply(
        events: List<CalEvent>,
        s: AgendaSettings,
        now: Long,
    ): Dedupe =
        dedupe(
            events.filter { it.calendarId !in s.hiddenCalendarIds && !isFinished(it, s, now) },
            s,
        )

    /** When the visible set next changes without any calendar edit: an event's hide time, or midnight. */
    fun nextChange(
        events: List<CalEvent>,
        s: AgendaSettings,
        now: Long,
        zone: ZoneId,
    ): Long {
        val midnight = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val changes =
            events.filter { !it.allDay }.flatMap { e ->
                listOfNotNull(
                    hideAfterMinutes(e, s)?.let { e.endMillis + it * MINUTE },
                    (e.endMillis + s.dimOffsetMinutes * MINUTE).takeIf { s.dimFinished },
                )
            }.filter { it > now }
        return minOf(midnight, changes.minOrNull() ?: Long.MAX_VALUE)
    }

    /** Days each event shows on, clipped to [from, from + days). All-day and multi-day events repeat per day. */
    fun groupByDay(
        events: List<CalEvent>,
        from: LocalDate,
        days: Int,
        zone: ZoneId,
    ): Map<LocalDate, List<CalEvent>> {
        val last = from.plusDays(days - 1L)
        val out = sortedMapOf<LocalDate, MutableList<CalEvent>>()
        for (e in events) {
            val start = Instant.ofEpochMilli(e.beginMillis).atZone(zone).toLocalDate()
            val end =
                if (e.endMillis > e.beginMillis) {
                    Instant.ofEpochMilli(e.endMillis - 1).atZone(zone).toLocalDate()
                } else {
                    start
                }
            var d = maxOf(start, from)
            while (d <= minOf(end, last)) {
                out.getOrPut(d) { mutableListOf() } += e
                d = d.plusDays(1)
            }
        }
        return out.mapValues { (_, list) -> list.sortedWith(ORDER) }
    }
}

private val JOIN_HOSTS = listOf("meet.google.com", "zoom.us", "teams.microsoft.com", "teams.live.com", "webex.com")
private val JOIN_LINK =
    Regex("""https?://(?:[\w-]+\.)?(?:${JOIN_HOSTS.joinToString("|") { Regex.escape(it) }})/[^\s<>"]+""")

/** First video-call URL in the location or description, if any. */
fun CalEvent.joinUrl(): String? = JOIN_LINK.find(location)?.value ?: JOIN_LINK.find(description)?.value

/** First readable line of the description, without links or HTML tags. */
fun CalEvent.detail(): String =
    description.replace(Regex("<[^>]*>"), " ").lineSequence()
        .map { it.replace(Regex("""https?://\S+"""), "").trim() }
        .firstOrNull { it.isNotEmpty() }.orEmpty()
