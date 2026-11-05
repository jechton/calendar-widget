package app.agenda.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.agenda.domain.EventFilter

/**
 * A list of text entries shown as removable chips, with a text field and an Add button underneath.
 *
 * Both duplicate editors below are this widget with different wording. The list itself lives in the settings
 * ([items]); this function only reports the new list through [onChange].
 *
 * @param isInvalid entries to mark as broken (they stay in the list so the user can remove them)
 * @param canAdd whether the current draft text may be added
 */
@Composable
private fun StringListEditor(
    title: String,
    hint: String,
    fieldLabel: String,
    items: List<String>,
    onChange: (List<String>) -> Unit,
    isInvalid: (String) -> Boolean = { false },
    canAdd: (String) -> Boolean = { it.isNotBlank() },
) {
    // `remember` keeps the half-typed text across redraws of this function.
    var draft by remember { mutableStateOf("") }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Text(hint, style = MaterialTheme.typography.bodySmall)
        // Chips default to a 48dp touch target, which makes them far apart. Turn that off for this block.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items.forEach { item ->
                    val invalid = isInvalid(item)
                    InputChip(
                        selected = invalid,
                        onClick = { onChange(items - item) },
                        label = { Text(if (invalid) "$item  (invalid, ignored)" else item) },
                        trailingIcon = {
                            Icon(Icons.Default.Close, "Remove", Modifier.size(InputChipDefaults.IconSize))
                        },
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text(fieldLabel) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                enabled = canAdd(draft) && draft.trim() !in items,
                onClick = {
                    onChange(items + draft.trim())
                    draft = ""
                },
            ) { Text("Add") }
        }
    }
}

/** Patterns that are cut out of titles before two events are compared. */
@Composable
internal fun RegexEditor(
    patterns: List<String>,
    onChange: (List<String>) -> Unit,
) {
    val invalid = EventFilter.invalidPatterns(patterns)
    StringListEditor(
        title = "Ignore text when comparing titles (regex, case-insensitive)",
        hint = "Example: \\(.*?\\) makes \"Standup (Room 4)\" match \"Standup\".",
        fieldLabel = "New pattern",
        items = patterns,
        onChange = onChange,
        isInvalid = { it in invalid },
    )
}

/** Lines like "Team meeting = Offsite sync": different titles that count as the same event. */
@Composable
internal fun SynonymEditor(
    synonyms: List<String>,
    onChange: (List<String>) -> Unit,
) {
    StringListEditor(
        title = "Synonyms: titles that count as the same event",
        hint = "Separate titles with =. Start and end times must still match. Ignore patterns apply first.",
        fieldLabel = "Title = other title",
        items = synonyms,
        onChange = onChange,
        canAdd = { it.contains('=') },
    )
}
