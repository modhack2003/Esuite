package dev.esuite.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T : Enum<T>> WorkspaceTabs(
    options: List<T>,
    selected: T,
    group: String,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    FlowRow(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { option ->
            val active = option == selected
            Box(
                modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp)
                    .testTag("${group}_tab_${option.name.lowercase()}")
                    .background(if (active) colors.secondaryContainer else Color.Transparent, shape)
                    .border(1.dp, if (active) Color.Transparent else colors.outlineVariant, shape)
                    .selectable(active, role = Role.Tab, onClick = { onSelect(option) })
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) colors.onSecondaryContainer else colors.onSurfaceVariant,
                )
            }
        }
    }
}
