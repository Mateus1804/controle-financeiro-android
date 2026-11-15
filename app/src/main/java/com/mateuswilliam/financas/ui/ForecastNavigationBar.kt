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
internal fun ForecastNavigationBar(tab: Int, onSelect: (Int) -> Unit) {
    val destinations = listOf("Mês" to R.drawable.ic_records, "Gráficos" to R.drawable.ic_overview,
        "Fixas e parcelas" to R.drawable.ic_calendar, "Mais" to R.drawable.ic_more)
    if (LocalDensity.current.fontScale > 1.25f) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).navigationBarsPadding().selectableGroup()) {
            destinations.forEachIndexed { index, (label, icon) ->
                Surface(color = if (tab == index) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Row(Modifier.selectable(selected = tab == index, role = Role.Tab, onClick = { onSelect(index) })
                        .heightIn(min = 48.dp).padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppIcon(selectedIcon(icon, tab == index))
                        Text(label, style = MaterialTheme.typography.labelLarge)
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
