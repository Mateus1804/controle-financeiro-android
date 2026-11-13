package com.mateuswilliam.financas.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.core.*

private const val VISIBLE_ROWS = 5

@Composable
internal fun MonthComparisonCard(comparison: MonthComparison, hidden: Boolean, onCategory: (Category) -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.fillMaxWidth().testTag("month-comparison").padding(FinanceSpacing.card), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionTitle("Comparação com o mês anterior", comparison.throughDay?.let { "Do dia 1 ao dia $it de cada mês · gastos líquidos" } ?: "Gastos líquidos por categoria")
            if (comparison.differences.isEmpty()) Text("Sem gastos para comparar nestes períodos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            comparison.differences.take(VISIBLE_ROWS).forEach { diff ->
                val worse = diff.deltaCents > 0
                Row(Modifier.fillMaxWidth().heightIn(min = FinanceSpacing.touch).clickable { onCategory(diff.category) }
                    .testTag("comparison-${diff.category.name}").padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(diff.category.label, style = MaterialTheme.typography.bodyMedium)
                        Text(if (hidden) "Valores ocultos" else "${Money.format(diff.previousCents)} → ${Money.format(diff.currentCents)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(if (hidden) "••••" else MonthComparisons.signed(diff.deltaCents),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (hidden || diff.deltaCents == 0L) MaterialTheme.colorScheme.onSurface
                            else if (worse) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
            }
            if (comparison.differences.isNotEmpty() && !hidden) Text(
                "Total: ${Money.format(comparison.previousTotalCents)} → ${Money.format(comparison.currentTotalCents)} (${MonthComparisons.signed(comparison.currentTotalCents - comparison.previousTotalCents)})",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
