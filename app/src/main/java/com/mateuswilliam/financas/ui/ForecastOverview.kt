package com.mateuswilliam.financas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mateuswilliam.financas.R
import com.mateuswilliam.financas.core.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.format.DateTimeFormatter

private val forecastDate = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private fun forecastPercent(value: BigDecimal): String = value.setScale(2, RoundingMode.HALF_UP)
    .toPlainString().replace('.', ',') + "%"

@Composable
internal fun ForecastSummary(report: ForecastReport, hidden: Boolean) {
    Card(Modifier.fillMaxWidth().testTag("forecast-summary"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Resultado do mês", "Previsão do ciclo · receitas menos despesas")
            Amount(report.marginCents, hidden, size = 32)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(Modifier.weight(1f), color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(12.dp)) { Text("Receitas", style = MaterialTheme.typography.labelLarge); Amount(report.incomeCents, hidden, size = 18) }
                }
                Surface(Modifier.weight(1f), color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(12.dp)) { Text("Despesas", style = MaterialTheme.typography.labelLarge); Amount(report.totalExpensesCents, hidden, size = 18) }
                }
            }
            BasicText(if (hidden) "••••" else report.expensePercentage?.let { "${forecastPercent(it)} da renda em gastos" }
                ?: "Renda não informada", style = MaterialTheme.typography.titleLarge.copy(
                    color = if (!hidden && (report.expensePercentage?.compareTo(BigDecimal(100)) ?: -1) > 0)
                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface),
                maxLines = 2, autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = 22.sp, stepSize = 1.sp))
            if (!hidden && report.expensePercentage != null && report.incomeCents > 0) ForecastExpenseBar(report)
            if (report.allocatedCents > 0) {
                ForecastValue("Destinado à reserva", report.allocatedCents, hidden)
                ForecastValue("Ainda não destinado", report.unallocatedCents, hidden)
            }
        }
    }
}

@Composable
private fun ForecastValue(label: String, cents: Long, hidden: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Amount(cents, hidden, size = 16)
    }
}

@Composable
private fun ForecastExpenseBar(report: ForecastReport) {
    val commitment = report.commitmentsCents.coerceAtLeast(0).toDouble()
    val variable = report.variableCents.coerceAtLeast(0).toDouble()
    // Scale expands in deficit cycles: the income marker remains the true 100% reference.
    val scale = maxOf(report.incomeCents.toDouble(), commitment + variable, 1.0)
    val commitmentColor = MaterialTheme.colorScheme.primary
    val variableColor = MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val marker = MaterialTheme.colorScheme.onSurface
    val commitmentPercent = forecastPercent(BigDecimal.valueOf(report.commitmentsCents).multiply(BigDecimal(100)).divide(BigDecimal.valueOf(report.incomeCents), 2, RoundingMode.HALF_UP))
    val variablePercent = forecastPercent(BigDecimal.valueOf(report.variableCents).multiply(BigDecimal(100)).divide(BigDecimal.valueOf(report.incomeCents), 2, RoundingMode.HALF_UP))
    val describe = "Compromissos $commitmentPercent; consumo $variablePercent da renda. Marca vertical: 100% da renda."
    Canvas(Modifier.fillMaxWidth().height(24.dp).semantics { contentDescription = describe }) {
        drawRect(track, topLeft = Offset(0f, 6.dp.toPx()), size = Size(size.width, 12.dp.toPx()))
        val first = (commitment / scale * size.width).toFloat()
        drawRect(commitmentColor, Offset(0f, 6.dp.toPx()), Size(first, 12.dp.toPx()))
        drawRect(variableColor, Offset(first, 6.dp.toPx()), Size((variable / scale * size.width).toFloat(), 12.dp.toPx()))
        val incomeX = (report.incomeCents / scale * size.width).toFloat().coerceIn(0f, size.width)
        drawLine(marker, Offset(incomeX, 0f), Offset(incomeX, size.height), 2.dp.toPx())
    }
    Text("Compromissos: $commitmentPercent da renda\nConsumo: $variablePercent da renda", style = MaterialTheme.typography.bodySmall)
    if (report.commitmentsCents < 0 || report.variableCents < 0)
        Text("Ajustes negativos estão no total; o gráfico mostra apenas valores positivos.", style = MaterialTheme.typography.bodySmall)
}

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
