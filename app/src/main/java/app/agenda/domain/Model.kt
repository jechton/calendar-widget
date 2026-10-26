package app.agenda.domain

import kotlinx.serialization.Serializable

data class CalEvent(
    val id: Long,
    val calendarId: Long,
    val title: String,
    /** Epoch millis. For all-day events, local midnight of the first day. */
    val beginMillis: Long,
    /** Epoch millis. For all-day events, local midnight after the last day. */
    val endMillis: Long,
    val allDay: Boolean,
    val location: String,
    val description: String,
    val color: Int,
)

data class CalInfo(
    val id: Long,
    val name: String,
    val account: String,
    val color: Int,
)

/** Dp values for the widget padding, the gap between cards and the padding inside a card. */
@Serializable
enum class Density(val outer: Int, val gap: Int, val cardPad: Int) {
    COMPACT(8, 2, 4),
    COMFORTABLE(12, 4, 8),
    SPACIOUS(16, 8, 12),
}

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class AgendaSettings(
    val hiddenCalendarIds: Set<Long> = emptySet(),
    val dedupe: Boolean = true,
    /** Among duplicates with equal info, keep the longer title instead of the shorter one. */
    val preferLongTitle: Boolean = false,
    /** Removed from titles before comparing, so "Standup (Room 4)" matches "Standup". */
    val ignoreRegexes: List<String> = emptyList(),
    val hideFinished: Boolean = true,
    /** Minutes to keep an event visible after it ends. */
    val hideOffsetMinutes: Int = 0,
    /** Per-calendar override of [hideOffsetMinutes]. [EventFilter.NEVER_HIDE] keeps finished events. */
    val calendarOffsets: Map<Long, Int> = emptyMap(),
    val hideDeclined: Boolean = true,
    val daysAhead: Int = 7,
    /** Multiplies every font size in the widget. */
    val textScale: Float = 1f,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Widget background alpha: 0 is see-through, 1 is solid. */
    val backgroundOpacity: Float = 1f,
    /** Widget corner radius in dp; event cards and the today pill follow it. */
    val cornerRadius: Int = 24,
    val density: Density = Density.COMFORTABLE,
    /** 0 shows every event of a day. */
    val maxEventsPerDay: Int = 0,
    /** Title and time on one line instead of two. */
    val singleLineCards: Boolean = false,
    /** Width in dp of the weekday and date column. */
    val dateColumnWidth: Int = 48,
)
