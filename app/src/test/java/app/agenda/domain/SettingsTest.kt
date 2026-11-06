package app.agenda.domain

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun oldCardsLayoutBecomesTheGooglePreset() {
        val old = json.decodeFromString<AgendaSettings>("""{"layout":"CARDS"}""").migrated()
        assertEquals(DayLabel.SIDE, old.dayLabel)
        assertEquals(EventLook.SOLID, old.eventLook)
        assertEquals(TimePosition.SECOND_LINE, old.timePosition)
        assertNull(old.layout)
    }

    @Test
    fun oldListLayoutBecomesTheSamsungPreset() {
        val old = json.decodeFromString<AgendaSettings>("""{"layout":"LIST","textScale":1.2}""").migrated()
        assertEquals(DayLabel.HEADING, old.dayLabel)
        assertEquals(EventLook.STRIPE, old.eventLook)
        assertEquals(TimePosition.LEFT, old.timePosition)
        assertEquals(true, old.dayDividers)
        assertEquals(1.2f, old.textScale)
    }

    @Test
    fun settingsSavedBeforeTheSplitKeepTheCardsLook() {
        // The old default layout (CARDS) was never written, so such settings have no layout key at all.
        val old = json.decodeFromString<AgendaSettings>("""{"daysAhead":5}""").migrated()
        assertEquals(AgendaSettings().dayLabel, old.dayLabel)
        assertEquals(DayLabel.SIDE, old.dayLabel)
        assertEquals(5, old.daysAhead)
    }

    @Test
    fun migratedSettingsSaveWithoutTheOldKey() {
        val saved = json.encodeToString(AgendaSettings(layout = WidgetLayout.LIST).migrated())
        assertEquals(false, saved.contains("\"layout\""))
    }

    @Test
    fun aPresetOnlyChangesLookSettings() {
        val base = AgendaSettings(daysAhead = 3, hideDeclined = false)
        val out = Preset.SAMSUNG.apply(base)
        assertEquals(3, out.daysAhead)
        assertEquals(false, out.hideDeclined)
        assertEquals(DayLabel.HEADING, out.dayLabel)
    }
}
