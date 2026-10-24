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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.core.*
import java.time.YearMonth
import java.time.ZoneId

private val chartColors = listOf(Color(0xFF6879EF), Color(0xFF31A58C), Color(0xFFE39E43),
    Color(0xFFD6729C), Color(0xFF8D77D8), Color(0xFF48A6C3))
private fun groupColor(key: String) = chartColors[Math.floorMod(key.hashCode(), chartColors.size)]
private fun percentage(points: Int) = "${points / 100},${(points % 100).toString().padStart(2, '0')}%"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseChart(records: List<Record>, month: YearMonth, hidden: Boolean, onGroup: (ExpenseFilter) -> Unit) {
    var dimension by rememberSaveable { mutableStateOf(ExpenseDimension.CATEGORY) }
    var allGroups by rememberSaveable { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val report = remember(records, month, zone, dimension) { ExpenseReports.month(records, month, zone, dimension) }
    val visible = report.groups.take(5).toMutableList()
    if (report.groups.size > 5) visible += ExpenseGroup("__remaining__", "Outros grupos",
        report.groups.drop(5).sumOf { it.cents }, report.groups.drop(5).sumOf { it.basisPoints })
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitle("Meu mapa de gastos", "Despesas confirmadas antes dos estornos")
            ChoiceField("Agrupar por", dimension, ExpenseDimension.entries.toList(), { it.label }) { dimension = it }
            if (report.total == 0L) Text("Nenhuma despesa confirmada neste período.")
            else {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(216.dp).clearAndSetSemantics { }) {
                        val stroke = 28.dp.toPx()
                        var start = -90f
                        visible.forEach { group ->
                            val sweep = (group.cents.toDouble() / report.total * 360).toFloat()
                            drawArc(groupColor(dimension.name + group.key), start, sweep, false,
                                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                                size = size.copy(width = size.width - stroke, height = size.height - stroke), style = Stroke(stroke))
                            start += sweep
                        }
                    }
                    Column(Modifier.width(148.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Despesas brutas", style = MaterialTheme.typography.labelMedium)
                        Text(if (hidden) "••••" else Money.format(report.total), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    }
                }
                visible.forEachIndexed { index, group ->
                    ExpenseLegend(group, hidden, groupColor(dimension.name + group.key)) {
                        if (index == 5 && report.groups.size > 5) allGroups = true
                        else onGroup(ExpenseFilter(dimension, group.key, group.label))
                    }
                }
                if (report.groups.size > 5) TextButton(onClick = { allGroups = true }) { Text("Ver todos os ${report.groups.size} grupos") }
            }
            Text("Estornos do período: ${if (hidden) "••••" else Money.format(report.refunds)}. Eles reduzem as despesas líquidas do resumo.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (allGroups) ModalBottomSheet(onDismissRequest = { allGroups = false }) {
        DialogSystemBars()
        Text("Todos os grupos", Modifier.padding(20.dp), style = MaterialTheme.typography.titleLarge)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp).padding(horizontal = 20.dp)) {
            items(report.groups, key = { it.key }) { group ->
                ExpenseLegend(group, hidden, groupColor(dimension.name + group.key)) {
                    allGroups = false; onGroup(ExpenseFilter(dimension, group.key, group.label))
                }
            }
        }
    }
}

@Composable
private fun ExpenseLegend(group: ExpenseGroup, hidden: Boolean, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().testTag("expense-group-${group.key}").heightIn(min = 48.dp).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(group.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Column(horizontalAlignment = Alignment.End) {
            Text(if (hidden) "••••" else Money.format(group.cents), style = MaterialTheme.typography.labelLarge)
            Text(if (hidden) "Percentual oculto" else percentage(group.basisPoints), style = MaterialTheme.typography.labelSmall)
        }
    }
}
