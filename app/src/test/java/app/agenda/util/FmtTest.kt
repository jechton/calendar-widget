package app.agenda.util

import app.agenda.domain.CalEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FmtTest {
    private val zone = ZoneId.of("UTC")
    private val day = LocalDate.of(2026, 10, 26)

    private fun ev(
        title: String,
        days: Long,
    ): CalEvent {
        val begin = day.atStartOfDay(zone).toInstant().toEpochMilli()
        return CalEvent(1, 1, title, begin, begin + days * 86_400_000L, true, "", "", 0)
    }

    @Test
    fun multiDayTitlesCountDays() {
        val e = ev("Offsite", 2)
        assertEquals("Offsite (Day 1/2)", Fmt.title(e, day, zone))
        assertEquals("Offsite (Day 2/2)", Fmt.title(e, day.plusDays(1), zone))
    }

    @Test
    fun singleDayTitleIsUnchanged() {
        assertEquals("Birthday", Fmt.title(ev("Birthday", 1), day, zone))
    }
}
