package com.mateuswilliam.financas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.core.*
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseChart(records: List<Record>, month: YearMonth, hidden: Boolean, onGroup: (ExpenseFilter) -> Unit) {
    var dimension by rememberSaveable { mutableStateOf(ExpenseDimension.CATEGORY) }
    var allGroups by rememberSaveable { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val report = remember(records, month, zone, dimension) { ExpenseReports.month(records, month, zone, dimension) }
    val palette = LocalFinanceColors.current.chart
    fun groupColor(key: String) = palette[Math.floorMod(key.hashCode(), palette.size)]
    val legend = remember(report) { ExpenseLegend.of(report) }
    val visible = legend.visible
    val chartTrack = MaterialTheme.colorScheme.surfaceContainerHigh
    val chartAlpha by androidx.compose.animation.core.animateFloatAsState(if (report.total > 0L) 1f else 0f, label = "Entrada do gráfico")
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().padding(FinanceSpacing.card), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Mapa de gastos", "Despesas confirmadas antes dos estornos")
            ChoiceField("Agrupar por", dimension, ExpenseDimension.entries.toList(), { it.label }) { dimension = it }
            if (report.total == 0L) Text("Nenhuma despesa confirmada neste período.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                val summary = if (hidden) "Mapa de gastos oculto. Valores e percentuais não são lidos em voz alta."
                else "Despesas brutas ${Money.format(report.total)}. " + visible.take(3).joinToString(". ") {
                    "${it.label}: ${Money.format(it.cents)}, ${ExpenseLegendText.percentage(it.basisPoints)} do total" }
                // Em tela estreita ou fonte grande o total sai do anel e fica logo abaixo, sem espremer o texto.
                val centered = LocalDensity.current.fontScale <= 1.25f && LocalConfiguration.current.screenWidthDp >= 360
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(192.dp).graphicsLayer { alpha = chartAlpha }.clearAndSetSemantics { contentDescription = summary }) {
                        val stroke = 18.dp.toPx()
                        var start = -90f
                        if (hidden) drawCircle(chartTrack, radius = (size.minDimension - stroke) / 2, style = Stroke(stroke))
                        else visible.forEach { group ->
                            val sweep = (group.cents.toDouble() / report.total * 360).toFloat()
                            drawArc(groupColor(dimension.name + group.key), start + if (visible.size > 1) 0.8f else 0f,
                                (sweep - if (visible.size > 1) minOf(sweep, 1.6f) else 0f).coerceAtLeast(0f), false,
                                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                                size = size.copy(width = size.width - stroke, height = size.height - stroke), style = Stroke(stroke))
                            start += sweep
                        }
                    }
                    if (centered) ExpenseTotal(report.total, hidden, Modifier.width(148.dp))
                }
                if (!centered) ExpenseTotal(report.total, hidden, Modifier.fillMaxWidth())
                }
                visible.forEach { group ->
                    ExpenseLegendRow(group, hidden, groupColor(dimension.name + group.key)) {
                        if (group.key == ExpenseLegend.OTHERS_KEY) allGroups = true
                        else onGroup(ExpenseFilter(dimension, group.key, group.label))
                    }
                }
                Text("Percentuais calculados sobre o total gasto no período.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (legend.hasOthers) TextButton(onClick = { allGroups = true }, Modifier.heightIn(min = FinanceSpacing.touch)) { Text("Ver todos os ${report.groups.size} grupos") }
            }
            FinanceDisclosure("Sobre os estornos do período", "expense-refunds") {
                Text("Estornos do período: ${if (hidden) "••••" else Money.format(report.refunds)}. Eles reduzem as despesas líquidas do resumo.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (allGroups) ModalBottomSheet(onDismissRequest = { allGroups = false }) {
        DialogSystemBars()
        Text("Todos os grupos", Modifier.padding(20.dp), style = MaterialTheme.typography.titleLarge)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp).padding(horizontal = 20.dp).navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(report.groups, key = { it.key }) { group ->
                ExpenseLegendRow(group, hidden, groupColor(dimension.name + group.key)) {
                    allGroups = false; onGroup(ExpenseFilter(dimension, group.key, group.label))
                }
            }
        }
    }
}

@Composable
private fun ExpenseLegendRow(group: ExpenseGroup, hidden: Boolean, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().testTag("expense-group-${group.key}").heightIn(min = 48.dp).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(12.dp).background(color, CircleShape))
        Text(privateDisplayText(group.label, hidden), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Column(horizontalAlignment = Alignment.End) {
            Amount(group.cents, hidden, Modifier.widthIn(max = 144.dp), size = 16)
            Text(if (hidden) "Percentual oculto" else "${ExpenseLegendText.percentage(group.basisPoints)} do total", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExpenseTotal(total: Long, hidden: Boolean, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Despesas brutas", style = MaterialTheme.typography.labelMedium)
        Amount(total, hidden, size = 20)
    }
}
