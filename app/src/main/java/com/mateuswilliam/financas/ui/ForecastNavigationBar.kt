package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
internal fun ForecastNavigationBar(tab: RendlyDestination, onSelect: (RendlyDestination) -> Unit) {
    val destinations = listOf(
        Triple(RendlyDestination.MONTH, "Mês", R.drawable.ic_records),
        Triple(RendlyDestination.GRAPHS, "Gráficos", R.drawable.ic_overview),
        Triple(RendlyDestination.FIXED, "Fixas e parcelas", R.drawable.ic_calendar),
        Triple(RendlyDestination.MORE, "Mais", R.drawable.ic_more),
    )
    if (LocalDensity.current.fontScale > 1.25f) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).navigationBarsPadding().selectableGroup()) {
            destinations.forEach { (destination, label, icon) ->
                Surface(color = if (tab == destination) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Row(Modifier.selectable(selected = tab == destination, role = Role.Tab, onClick = { onSelect(destination) })
                        .heightIn(min = 48.dp).padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppIcon(selectedIcon(icon, tab == destination))
                        Text(label, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    } else {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
            destinations.forEach { (destination, label, icon) ->
                NavigationBarItem(selected = tab == destination, onClick = { onSelect(destination) },
                    icon = { AppIcon(selectedIcon(icon, tab == destination)) }, label = { Text(label) })
            }
        }
    }
}
