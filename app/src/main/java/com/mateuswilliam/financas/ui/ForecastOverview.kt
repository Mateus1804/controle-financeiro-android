package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.R
import com.mateuswilliam.financas.core.*
import java.time.format.DateTimeFormatter

private val forecastDate = DateTimeFormatter.ofPattern("dd/MM/yyyy")
@Composable
internal fun ForecastCyclePicker(period: BudgetPeriod, onPrevious: () -> Unit, onNext: () -> Unit,
    onToday: () -> Unit, onConfigure: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp).testTag("forecast-cycle-previous")) {
                AppIcon(R.drawable.ic_chevron_left, description = "Ciclo anterior")
            }
            Text("${period.start.format(forecastDate)} a ${period.endExclusive.minusDays(1).format(forecastDate)}",
                Modifier.weight(1f).testTag("forecast-cycle-dates"), style = MaterialTheme.typography.titleSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(onClick = onNext, modifier = Modifier.size(48.dp).testTag("forecast-cycle-next")) {
                AppIcon(R.drawable.ic_chevron_right, description = "Próximo ciclo")
            }
        }
        Row {
            TextButton(onClick = onToday, modifier = Modifier.heightIn(min = 48.dp).testTag("forecast-cycle-today")) { Text("Ciclo atual") }
            TextButton(onClick = onConfigure, modifier = Modifier.heightIn(min = 48.dp).testTag("forecast-cycle-configure")) { Text("Configurar ciclo") }
        }
    }
}

internal fun LazyListScope.forecastLineItems(lines: List<ForecastLine>, hidden: Boolean, busy: Boolean,
    onEdit: (ForecastLine) -> Unit, onIgnore: (ForecastLine) -> Unit, onStop: (ForecastLine) -> Unit) {
    items(lines, key = { "${it.itemId}:${it.occurrenceKey}" }) { line ->
        val group = when {
            line.kind == ForecastKind.INCOME -> "Renda"
            line.group == ForecastGroup.VARIABLE -> "Gasto · Consumo"
            else -> "Gasto · Compromisso"
        }
        val status = when {
            line.ignored -> "Ignorada neste ciclo"
            !line.included -> "Já incluído na fatura"
            else -> null
        }
        Card(onClick = { onEdit(line) }, enabled = !busy,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        ListItem(colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.testTag("forecast-line:${line.itemId}:${line.occurrenceKey}"),
            headlineContent = { Text(privateDisplayText(line.title, hidden), style = MaterialTheme.typography.titleMedium) },
            supportingContent = {
                Column {
                    Text("$group · ${if (line.rule.cycleAnchored) "Consumo do ciclo" else line.date.format(forecastDate)}${if (line.estimated) " · Estimado" else ""}")
                    status?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Amount(line.amountCents, hidden, size = 14)
                    ContextualActions(privateDisplayText(line.title, hidden), "forecast-actions:${line.itemId}:${line.occurrenceKey}", !busy,
                        buildList {
                            add(FinanceAction("Editar", { onEdit(line) }))
                            add(FinanceAction(if (line.ignored) "Restaurar somente ocorrência" else "Ignorar somente ocorrência", { onIgnore(line) }))
                            if (line.frequency != ForecastFrequency.ONCE)
                                add(FinanceAction("Interromper futuras", { onStop(line) }, destructive = true))
                        })
                }
            })
        }
    }
}
