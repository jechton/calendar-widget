package app.agenda.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class EventFilterTest {
    private val zone = ZoneId.of("UTC")
    private val hour = 3_600_000L
    private val day = 24 * hour

    private fun ev(
        title: String,
        cal: Long = 1,
        begin: Long = 10 * hour,
        end: Long = 12 * hour,
        allDay: Boolean = false,
        location: String = "",
        description: String = "",
    ) = CalEvent(title.hashCode().toLong() + cal, cal, title, begin, end, allDay, location, description, 0)

    @Test
    fun exactDuplicatesAcrossCalendarsCollapse() {
        val r = EventFilter.dedupe(listOf(ev("Dinner", 1), ev("Dinner", 2)), AgendaSettings())
        assertEquals(1, r.events.size)
        assertEquals(1, r.merges.size)
    }

    @Test
    fun differentTimesAreNotDuplicates() {
        val r = EventFilter.dedupe(listOf(ev("Dinner"), ev("Dinner", 2, end = 13 * hour)), AgendaSettings())
        assertEquals(2, r.events.size)
    }

    @Test
    fun regexMergesNearMatches() {
        val events = listOf(ev("Standup (Room 4)", 1), ev("Standup", 2))
        val plain = EventFilter.dedupe(events, AgendaSettings())
        assertEquals(2, plain.events.size)
        val s = AgendaSettings(ignoreRegexes = listOf("""\(.*?\)"""))
        assertEquals(1, EventFilter.dedupe(events, s).events.size)
    }

    @Test
    fun moreInfoBeatsShorterTitle() {
        val events = listOf(ev("Standup (Room 4)", 1, location = "Office"), ev("Standup", 2))
        val s = AgendaSettings(ignoreRegexes = listOf("""\(.*?\)"""))
        assertEquals(1L, EventFilter.dedupe(events, s).events.single().calendarId)
    }

    @Test
    fun tieKeepsLongestTitleWhenAsked() {
        val events = listOf(ev("Standup (Room 4)", 1), ev("Standup", 2))
        val s = AgendaSettings(ignoreRegexes = listOf("""\(.*?\)"""), preferLongTitle = true)
        assertEquals("Standup (Room 4)", EventFilter.dedupe(events, s).events.single().title)
    }

    @Test
    fun tieKeepsShortestTitle() {
        val events = listOf(ev("Standup (Room 4)", 1), ev("Standup", 2))
        val s = AgendaSettings(ignoreRegexes = listOf("""\(.*?\)"""))
        assertEquals("Standup", EventFilter.dedupe(events, s).events.single().title)
    }

    @Test
    fun keepsMostInfoWhenAsked() {
        val events = listOf(ev("Standup", 1), ev("Standup", 2, location = "Office"))
        assertEquals(2L, EventFilter.dedupe(events, AgendaSettings()).events.single().calendarId)
    }

    @Test
    fun invalidRegexIsIgnored() {
        val s = AgendaSettings(ignoreRegexes = listOf("("))
        assertEquals(1, EventFilter.dedupe(listOf(ev("A", 1), ev("A", 2)), s).events.size)
        assertEquals(setOf("("), EventFilter.invalidPatterns(s.ignoreRegexes))
    }

    @Test
    fun patternThatStripsEverythingFallsBackToRawTitle() {
        val s = AgendaSettings(ignoreRegexes = listOf(".*"))
        assertEquals(2, EventFilter.dedupe(listOf(ev("A", 1), ev("B", 2)), s).events.size)
    }

    @Test
    fun finishedEventsHideAfterOffset() {
        val e = ev("Lunch", end = 12 * hour)
        val s = AgendaSettings(hideOffsetMinutes = 60)
        assertFalse(EventFilter.isFinished(e, s, 12 * hour + 59 * 60_000))
        assertTrue(EventFilter.isFinished(e, s, 13 * hour))
    }

    @Test
    fun allDayEventsAreNotHiddenByClock() {
        val e = ev("Holiday", begin = 0, end = day, allDay = true)
        assertFalse(EventFilter.isFinished(e, AgendaSettings(), 2 * day))
    }

    @Test
    fun hiddenCalendarsAreDropped() {
        val s = AgendaSettings(hiddenCalendarIds = setOf(2L))
        assertEquals(1, EventFilter.apply(listOf(ev("A", 1), ev("B", 2)), s, 0).events.size)
    }

    @Test
    fun nextChangeIsEarliestHideTime() {
        val s = AgendaSettings(hideOffsetMinutes = 30)
        val events = listOf(ev("A", end = 12 * hour), ev("B", end = 11 * hour))
        assertEquals(11 * hour + 30 * 60_000, EventFilter.nextChange(events, s, 9 * hour, zone))
    }

    @Test
    fun multiDayAndAllDayEventsRepeatPerDay() {
        val from = LocalDate.of(1970, 1, 1)
        val holiday = ev("Trip", begin = 0, end = 2 * day, allDay = true)
        val g = EventFilter.groupByDay(listOf(holiday), from, 7, zone)
        assertEquals(listOf(from, from.plusDays(1)), g.keys.toList())
    }

    fun dimmingHasItsOwnCutoffAndSchedulesARedraw() {
        val s = AgendaSettings(dimFinished = true, dimOffsetMinutes = 30, hideOffsetMinutes = 120)
        val e = ev("Lunch", begin = 10 * hour, end = 12 * hour)
        assertFalse(EventFilter.isDimmed(e, s, 12 * hour))
        assertTrue(EventFilter.isDimmed(e, s, 12 * hour + 30 * 60_000L))
        assertFalse(EventFilter.isFinished(e, s, 12 * hour + 30 * 60_000L))
        assertEquals(12 * hour + 30 * 60_000L, EventFilter.nextChange(listOf(e), s, 11 * hour, zone))
    }

    @Test
    fun joinLinkAndDetailAreExtracted() {
        val e =
            ev("Sync", location = "Room 2", description = "<p>Agenda notes</p>\nhttps://meet.google.com/abc-defg-hij")
        assertEquals("https://meet.google.com/abc-defg-hij", e.joinUrl())
        assertEquals("Agenda notes", e.detail())
        assertEquals(null, ev("Dinner", location = "https://example.com/x").joinUrl())
    }

    @Test
    fun mergedCopyKeepsAnEventSetColorOverACalendarColor() {
        val calendarGreen = ev("Dinner", 1).copy(color = 0xFF00FF00.toInt())
        val eventBlue = ev("Dinner", 2).copy(color = 0xFF0000FF.toInt(), ownColor = true)
        val r = EventFilter.dedupe(listOf(calendarGreen, eventBlue), AgendaSettings())
        assertEquals(0xFF0000FF.toInt(), r.events.single().color)
    }
}
