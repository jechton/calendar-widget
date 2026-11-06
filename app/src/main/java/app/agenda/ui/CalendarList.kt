package app.agenda.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.agenda.domain.AgendaSettings
import app.agenda.domain.CalInfo
import app.agenda.domain.withCalendarsShown

@Composable
internal fun AccountGroup(
    account: String,
    cals: List<CalInfo>,
    s: AgendaSettings,
    edit: ((AgendaSettings) -> AgendaSettings) -> Unit,
) {
    var open by rememberSaveable(account) { mutableStateOf(false) }
    val shown = cals.count { it.id !in s.hiddenCalendarIds }
    ListItem(
        modifier = Modifier.clickable { open = !open },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(account) },
        supportingContent = { Text("$shown of ${cals.size} shown") },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AgendaSwitch(shown > 0) { show ->
                    val ids = cals.map { it.id }.toSet()
                    edit { it.withCalendarsShown(ids, show) }
                }
                Icon(
                    if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    null,
                    Modifier.padding(start = 8.dp),
                )
            }
        },
    )
    if (open) cals.forEach { CalendarRow(it, s, edit) }
}

@Composable
internal fun CalendarRow(
    cal: CalInfo,
    s: AgendaSettings,
    edit: ((AgendaSettings) -> AgendaSettings) -> Unit,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Box(Modifier.size(14.dp).clip(CircleShape).background(Color(cal.color))) },
        headlineContent = { Text(cal.name) },
        trailingContent = {
            AgendaSwitch(cal.id !in s.hiddenCalendarIds) { show ->
                edit { it.withCalendarsShown(setOf(cal.id), show) }
            }
        },
    )
}
