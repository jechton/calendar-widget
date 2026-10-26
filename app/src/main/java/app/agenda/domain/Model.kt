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
    /** True when the event has its own color, not just its calendar's. */
    val ownColor: Boolean = false,
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

/** CARDS: coloured cards beside a date column. LIST: day headings with dividers and a colour stripe per event. */
@Serializable
enum class WidgetLayout { CARDS, LIST }

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
    val hideDeclined: Boolean = true,
    val daysAhead: Int = 7,
    /** Multiplies every font size in the widget. */
    val textScale: Float = 1f,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Widget background alpha: 0 is see-through, 1 is solid. */
    val backgroundOpacity: Float = 1f,
    /** Widget corner radius in dp; event cards and the today pill follow it. */
    val cornerRadius: Int = 24,
    val layout: WidgetLayout = WidgetLayout.CARDS,
    /** List layout: show "10:30 – 11:30 AM" instead of just the start time. */
    val alwaysShowEndTime: Boolean = false,
    val density: Density = Density.COMFORTABLE,
    /** 0 shows every event of a day. */
    val maxEventsPerDay: Int = 0,
    /** Title and time on one line instead of two. */
    val singleLineCards: Boolean = false,
    /** Width in dp of the weekday and date column. */
    val dateColumnWidth: Int = 48,
    /** Fade events that ended [dimOffsetMinutes] ago; separate from the hide-finished cutoff. */
    val dimFinished: Boolean = false,
    val dimOffsetMinutes: Int = 0,
    /** Label the first day of each week. */
    val weekDividers: Boolean = false,
    /** Extra line with the first line of the event description. */
    val showDetails: Boolean = false,
    /** Show a Join link on events with a Meet, Zoom, Teams or Webex URL. */
    val tapToJoin: Boolean = true,
)

/** One-tap bundles of the look settings; everything else is left alone. */
enum class Preset(val label: String, val apply: (AgendaSettings) -> AgendaSettings) {
    CARDS("Cards", {
        it.copy(
            layout = WidgetLayout.CARDS,
            density = Density.COMFORTABLE,
            cornerRadius = 24,
            maxEventsPerDay = 0,
            singleLineCards = false,
            dateColumnWidth = 48,
            backgroundOpacity = 1f,
        )
    }),
    SAMSUNG("Samsung", {
        it.copy(
            layout = WidgetLayout.LIST,
            density = Density.COMFORTABLE,
            cornerRadius = 20,
            maxEventsPerDay = 0,
            singleLineCards = false,
            alwaysShowEndTime = false,
            backgroundOpacity = 1f,
        )
    }),
    COMPACT("Compact", {
        it.copy(
            layout = WidgetLayout.LIST,
            density = Density.COMPACT,
            cornerRadius = 12,
            maxEventsPerDay = 3,
            singleLineCards = true,
            backgroundOpacity = 1f,
        )
    }),
    GLASS("Glass", {
        it.copy(
            layout = WidgetLayout.LIST,
            density = Density.COMFORTABLE,
            cornerRadius = 28,
            maxEventsPerDay = 0,
            singleLineCards = false,
            backgroundOpacity = 0.4f,
        )
    }),
}
