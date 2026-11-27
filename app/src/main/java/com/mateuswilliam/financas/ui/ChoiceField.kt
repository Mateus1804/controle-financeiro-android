package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.R

@Composable
fun <T> ChoiceField(label: String, value: T, options: List<T>, text: (T) -> String, @androidx.annotation.DrawableRes icon: Int? = null, onChange: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = label }, shape = MaterialTheme.shapes.small, border = null,
                colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, contentColor = MaterialTheme.colorScheme.onSurface), contentPadding = PaddingValues(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    if (icon != null) AppIcon(icon)
                    Text(text(value), Modifier.weight(1f)); AppIcon(R.drawable.ic_chevron_down)
                }
            }
            DropdownMenu(expanded, { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                options.forEach { option ->
                    val isSelected = option == value
                    DropdownMenuItem(text = { Text(text(option)) },
                        leadingIcon = if (icon != null) { { AppIcon(selectedIcon(icon, isSelected)) } } else null,
                        modifier = Modifier.semantics { selected = isSelected },
                        onClick = { onChange(option); expanded = false })
                }
            }
        }
    }
}
