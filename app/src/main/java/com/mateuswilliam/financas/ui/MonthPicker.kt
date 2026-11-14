package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.R
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun MonthPicker(month: YearMonth, onMonth: (YearMonth) -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonth(month.minusMonths(1)) }, modifier = Modifier.size(48.dp)) { AppIcon(R.drawable.ic_chevron_left, description = "Mês anterior") }
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("pt-BR"))), Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(onClick = { onMonth(month.plusMonths(1)) }, modifier = Modifier.size(48.dp)) { AppIcon(R.drawable.ic_chevron_right, description = "Próximo mês") }
        }
    }
}

