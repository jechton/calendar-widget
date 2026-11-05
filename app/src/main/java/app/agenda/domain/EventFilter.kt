package app.agenda.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class Merge(val kept: CalEvent, val dropped: List<CalEvent>)

data class Dedupe(val events: List<CalEvent>, val merges: List<Merge>)

/**
 * All the rules for deciding what the widget shows. Plain Kotlin with no Android code, so it can be unit tested
 * on a laptop (see EventFilterTest). Order of work: drop hidden calendars and finished events ([apply]), then
 * merge duplicates ([dedupe]), then group by day ([groupByDay]).
 */
object EventFilter {
    private const val MINUTE = 60_000L

    private data class Key(val title: String, val begin: Long, val end: Long, val allDay: Boolean)

    /** Turns the user's patterns into case-insensitive regexes. Invalid ones are skipped; see [invalidPatterns]. */
    fun compile(patterns: List<String>): List<Regex> =
        patterns.mapNotNull { runCatching { Regex(it, RegexOption.IGNORE_CASE) }.getOrNull() }

    fun invalidPatterns(patterns: List<String>): Set<String> =
        patterns.filter { runCatching { Regex(it) }.isFailure }.toSet()

    /**
     * The form of a title used to decide if two events match: patterns removed, spaces collapsed, lowercase.
     * If the patterns remove everything, the plain lowercase title is used so the event keeps a key.
     */
    fun normalizedTitle(
        title: String,
        regexes: List<Regex>,
    ): String {
        val stripped = regexes.fold(title) { acc, r -> acc.replace(r, " ") }
        val clean = stripped.trim().replace(Regex("\\s+"), " ").lowercase()
        return clean.ifEmpty { title.trim().lowercase() }
    }

    /** Maps each normalized synonym to the first title of its line. Lines look like "a = b = c". */
    fun synonymMap(
        synonyms: List<String>,
        regexes: List<Regex>,
    ): Map<String, String> {
        val out = mutableMapOf<String, String>()
        for (line in synonyms) {
            val parts = line.split('=').map { it.trim() }.filter { it.isNotEmpty() }
            val titles = parts.map { normalizedTitle(it, regexes) }
            val head = titles.firstOrNull() ?: continue
            titles.forEach { out.putIfAbsent(it, head) }
        }
        return out
    }

    private fun info(e: CalEvent) = (if (e.location.isNotBlank()) 1 else 0) + (if (e.description.isNotBlank()) 1 else 0)

    /**
     * Merges events that share title (after [normalizedTitle] and synonyms), start, end and all-day flag.
     * From each group one event is kept: join link first, then most info, then title length, then calendar id.
     * [Dedupe.merges] records what was merged, for the preview in the settings screen.
     */
    fun dedupe(
        events: List<CalEvent>,
        settings: AgendaSettings,
    ): Dedupe {
        if (!settings.dedupe) return Dedupe(events, emptyList())
        val regexes = compile(settings.ignoreRegexes)
        val synonyms = synonymMap(settings.synonyms, regexes)
        val kept = mutableListOf<CalEvent>()
        val merges = mutableListOf<Merge>()
        events.groupBy {
            val title = normalizedTitle(it.title, regexes)
            Key(synonyms[title] ?: title, it.beginMillis, it.endMillis, it.allDay)
        }
            .values
            .forEach { group ->
                val ranked =
                    group
                        .sortedWith(
                            // A copy with a join link wins, so the link survives the merge.
                            compareByDescending<CalEvent> { it.joinUrl() != null }
                                .thenByDescending { info(it) }
                                .thenBy { if (settings.preferLongTitle) -it.title.length else it.title.length }
                                .thenBy { it.calendarId },
                        )
                val first = ranked.first()
                // An event-set color beats a calendar color; if several copies have one, the best-ranked wins.
                val own = ranked.firstOrNull { it.ownColor }
                val winner = if (own == null || own === first) first else first.copy(color = own.color, ownColor = true)
                kept += winner
                if (group.size > 1) merges += Merge(winner, group - first)
            }
        return Dedupe(kept.sortedWith(ORDER), merges)
    }

    private val ORDER = compareBy<CalEvent>({ !it.allDay }, { it.beginMillis }, { it.endMillis }, { it.title })

    /** Minutes after the end at which the event disappears, or null if it never does. */
    private fun hideAfterMinutes(s: AgendaSettings): Int? = s.hideOffsetMinutes.takeIf { s.hideFinished }

    /** True once [now] is past the end time plus the hide delay. All-day events are never "finished" mid-day. */
    fun isFinished(
        e: CalEvent,
        s: AgendaSettings,
        now: Long,
    ): Boolean {
        val offset = hideAfterMinutes(s) ?: return false
        // All-day events drop off with the day itself, not at a clock time.
        return !e.allDay && now >= e.endMillis + offset * MINUTE
    }

    /** Same test as [isFinished], but for fading an event instead of hiding it. */
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
                    hideAfterMinutes(s)?.let { e.endMillis + it * MINUTE },
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

/** Video call hosts that get a Join link. Add a host here to support another service. */
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
