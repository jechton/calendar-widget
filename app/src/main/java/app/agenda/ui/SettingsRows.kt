package app.agenda.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A titled group of settings on a tonal surface. */
@Composable
internal fun Section(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = MaterialTheme.shapes.extraLarge,
        ) { Column(Modifier.padding(vertical = 8.dp), content = content) }
    }
}

@Composable
internal fun SwitchRow(
    label: String,
    checked: Boolean,
    supporting: String? = null,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = { AgendaSwitch(checked, onChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
internal fun AgendaSwitch(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        thumbContent =
            if (checked) {
                (
                    {
                        Icon(
                            Icons.Default.Check,
                            null,
                            Modifier.size(SwitchDefaults.IconSize),
                        )
                    }
                )
            } else {
                null
            },
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label)
        val interaction = remember { MutableInteractionSource() }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            interactionSource = interaction,
            thumb = { SliderDefaults.Thumb(interaction, thumbSize = DpSize(4.dp, 24.dp)) },
            track = {
                SliderDefaults.Track(it, drawStopIndicator = null, drawTick = {
                        _,
                        _,
                    ->
                }, thumbTrackGapSize = 4.dp)
            },
        )
    }
}

@Composable
internal inline fun <reified T : Enum<T>> EnumChoice(
    label: String,
    selected: T,
    crossinline onChange: (T) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            enumValues<T>().forEachIndexed { i, mode ->
                SegmentedButton(
                    selected = mode == selected,
                    onClick = { onChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(i, enumValues<T>().size),
                    // The check icon eats the width long labels like "Comfortable" need.
                    icon = {},
                ) { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 13.sp, maxLines = 1) }
            }
        }
    }
}
