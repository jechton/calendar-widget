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

/** Where the day is written: a column beside its events, or a heading line above them. */
@Serializable
enum class DayLabel(val label: String) { SIDE("Date column"), HEADING("Heading") }

/** How an event is coloured: its whole row filled, or a thin stripe at the left. */
@Serializable
enum class EventLook(val label: String) { SOLID("Solid fill"), STRIPE("Stripe") }

/** Where the start time goes: in front of the title, or in a line under it. */
@Serializable
enum class TimePosition(val label: String) { LEFT("Left of title"), SECOND_LINE("Second line") }

/** The old two-way layout switch, kept only so saved settings can be converted. See [migrated]. */
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
    /** Lines like "Team meeting = Driftwood". Titles on one line count as the same event when comparing. */
    val synonyms: List<String> = emptyList(),
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
    val dayLabel: DayLabel = DayLabel.SIDE,
    val eventLook: EventLook = EventLook.SOLID,
    val timePosition: TimePosition = TimePosition.SECOND_LINE,
    /** A thin line between days. Only drawn when [dayLabel] is HEADING. */
    val dayDividers: Boolean = false,
    /** Read from old saved settings, converted by [migrated], then cleared. Never set by new code. */
    val layout: WidgetLayout? = null,
    /** Time on the left: show "10:30 – 11:30 AM" instead of just the start time. */
    val alwaysShowEndTime: Boolean = false,
    val density: Density = Density.COMFORTABLE,
    /** 0 shows every event of a day. */
    val maxEventsPerDay: Int = 0,
    /** Time on the second line: put it beside the title instead. Time on the left: drop the second line. */
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

/** Copy with the calendars in [ids] shown or hidden. */
fun AgendaSettings.withCalendarsShown(
    ids: Set<Long>,
    show: Boolean,
) = copy(hiddenCalendarIds = if (show) hiddenCalendarIds - ids else hiddenCalendarIds + ids)

/** Turns a saved old-style [AgendaSettings.layout] into the four separate choices that replaced it. */
fun AgendaSettings.migrated(): AgendaSettings =
    when (layout) {
        null -> this
        WidgetLayout.CARDS -> Preset.GOOGLE.apply(this).copy(layout = null)
        WidgetLayout.LIST -> Preset.SAMSUNG.apply(this).copy(layout = null)
    }

/**
 * One-tap bundles of the look settings; everything else is left alone. A preset is only a starting point:
 * changing any single choice afterwards just gives you your own mix.
 */
enum class Preset(val label: String, val apply: (AgendaSettings) -> AgendaSettings) {
    GOOGLE("Google", {
        it.copy(
            dayLabel = DayLabel.SIDE,
            eventLook = EventLook.SOLID,
            timePosition = TimePosition.SECOND_LINE,
            dayDividers = false,
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
            dayLabel = DayLabel.HEADING,
            eventLook = EventLook.STRIPE,
            timePosition = TimePosition.LEFT,
            dayDividers = true,
            density = Density.COMFORTABLE,
            cornerRadius = 20,
            maxEventsPerDay = 0,
            singleLineCards = false,
            alwaysShowEndTime = false,
            backgroundOpacity = 1f,
        )
    }),
}
