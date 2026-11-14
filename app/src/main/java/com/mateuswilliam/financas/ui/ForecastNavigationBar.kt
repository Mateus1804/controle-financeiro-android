package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.R

/** Textos ampliados usam a largura da tela, sem reduzir a fonte escolhida no Android. */
@Composable
internal fun ForecastNavigationBar(tab: Int, onSelect: (Int) -> Unit) {
    val destinations = listOf("Orçamento" to R.drawable.ic_overview,
        "Projeções" to R.drawable.ic_chevron_right, "Reserva" to R.drawable.ic_wallet)
    if (LocalDensity.current.fontScale > 1.25f) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().selectableGroup()) {
            destinations.forEachIndexed { index, (label, icon) ->
                Surface(color = if (tab == index) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Row(Modifier.fillMaxWidth().selectable(selected = tab == index, role = Role.Tab, onClick = { onSelect(index) })
                        .heightIn(min = 48.dp).padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppIcon(selectedIcon(icon, tab == index))
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    } else {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
            destinations.forEachIndexed { index, (label, icon) ->
                NavigationBarItem(selected = tab == index, onClick = { onSelect(index) },
                    icon = { AppIcon(selectedIcon(icon, tab == index)) }, label = { Text(label) })
            }
        }
    }
}
