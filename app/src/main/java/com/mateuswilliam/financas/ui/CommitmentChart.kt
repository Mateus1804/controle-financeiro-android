package com.mateuswilliam.financas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mateuswilliam.financas.core.CommitmentForms
import com.mateuswilliam.financas.core.CommitmentReport
import com.mateuswilliam.financas.core.CommitmentReports
import com.mateuswilliam.financas.core.CommitmentReportText
import com.mateuswilliam.financas.core.CommitmentSliceKind
import com.mateuswilliam.financas.core.CommitmentState
import com.mateuswilliam.financas.core.Money
import com.mateuswilliam.financas.core.MonthlyIncomePlan
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun MonthlyCommitmentCard(
    result: Result<CommitmentReport>?, plan: MonthlyIncomePlan?, loaded: Boolean,
    busy: Boolean, hidden: Boolean, onEdit: () -> Unit,
) {
    val report = result?.getOrNull()
    val percentage = report?.incomePercentage
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("commitment-card"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(Modifier.padding(FinanceSpacing.section), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Comprometimento da renda", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            Text("Parcelas e despesas fixas do mês, previstas ou já registradas.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            when {
                !loaded -> Text("Carregando a configuração do mês…")
                hidden -> Text("Renda e limite ocultos")
                result == null -> Text("Carregando o comprometimento do mês…")
                report == null -> Text("Não foi possível carregar o comprometimento. Reabra o Planejamento para tentar novamente.")
                percentage == null -> {
                    Text(if (plan == null) "Renda ainda não informada" else "Informe uma renda maior que zero")
                    CommitmentMetric("Valor comprometido", report.committedCents, Modifier.testTag("committed-value"))
                    Text("A renda líquida prevista é necessária para calcular o percentual e comparar com o limite.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    CommitmentStatus(report.state)
                    CommitmentDonut(report, CommitmentReportText.percentage(percentage, report.healthyLimitBasisPoints))
                    CommitmentMetric("Renda líquida prevista", report.incomeCents)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    if (report.exceedsIncome) CommitmentMetric("Valor acima da renda", report.committedCents - report.incomeCents)
                    else CommitmentMetric("Renda ainda não comprometida", report.freeCents)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(18.dp).height(3.dp).background(MaterialTheme.colorScheme.onSurface))
                        Text("Limite de comprometimento: ${CommitmentForms.formatPercent(report.healthyLimitBasisPoints)}%",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            OutlinedButton(onClick = onEdit, enabled = loaded && !busy, modifier = Modifier.fillMaxWidth().testTag("edit-monthly-income")) {
                Text(if (plan == null) "Informar renda e limite" else "Editar renda e limite", textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun CommitmentStatus(state: CommitmentState) {
    val colors = MaterialTheme.colorScheme
    val (container, label) = when (state) {
        CommitmentState.ABOVE_LIMIT -> colors.errorContainer to colors.onErrorContainer
        CommitmentState.ATTENTION -> colors.tertiaryContainer to colors.onTertiaryContainer
        else -> colors.secondaryContainer to colors.onSecondaryContainer
    }
    Surface(shape = MaterialTheme.shapes.small, color = container, contentColor = label) {
        Text(state.label, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun CommitmentMetric(label: String, cents: Long, modifier: Modifier = Modifier) {
    val layout = modifier.fillMaxWidth().semantics(mergeDescendants = true) { }
    if (LocalDensity.current.fontScale > 1.25f) {
        Column(layout, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Amount(cents, false, size = 16)
        }
    } else Row(layout, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Amount(cents, false, Modifier.widthIn(max = 146.dp), size = 16)
    }
}

@Composable
private fun CommitmentDonut(report: CommitmentReport, percentage: String) {
    val palette = LocalFinanceColors.current.chart
    val freeColor = MaterialTheme.colorScheme.outline
    val cardColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val markerColor = MaterialTheme.colorScheme.onSurface
    val description = "$percentage da renda comprometidos; " +
        (report.largestExpense?.let { "maior gasto: ${it.label}." } ?: "nenhum compromisso.")
    val scale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Números longos e fonte ampliada mantêm a leitura abaixo da rosca.
        val centerFits = maxWidth >= 220.dp && scale <= 1.25f && percentage.length <= 8 &&
            Money.format(report.committedCents).length <= 16
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.widthIn(max = 224.dp).fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                Canvas(Modifier.matchParentSize().testTag("commitment-donut").semantics { contentDescription = description }) {
                    val stroke = 18.dp.toPx()
                    val radius = (size.minDimension - stroke) / 2 - 8.dp.toPx()
                    if (radius <= 0f) return@Canvas
                    val bounds = Size(2 * radius, 2 * radius)
                    val topLeft = center - Offset(radius, radius)
                    report.slices.forEachIndexed { index, slice ->
                        if (slice.sweepMicroDegrees > 0) {
                            drawArc(sliceColor(index, slice.kind, palette, freeColor), degrees(slice.startMicroDegrees),
                                degrees(slice.sweepMicroDegrees), useCenter = false, topLeft = topLeft,
                                size = bounds, style = Stroke(stroke))
                        }
                    }
                    report.limitMarkerMicroDegrees?.let { marker ->
                        val radians = Math.toRadians(degrees(marker).toDouble())
                        val direction = Offset(cos(radians).toFloat(), sin(radians).toFloat())
                        val inset = stroke / 2 + 4.dp.toPx()
                        val start = center + direction * (radius - inset)
                        val end = center + direction * (radius + inset)
                        drawLine(cardColor, start, end, strokeWidth = 6.dp.toPx())
                        drawLine(markerColor, start, end, strokeWidth = 2.dp.toPx())
                    }
                }
                if (centerFits) CommitmentHeadline(percentage, report.committedCents, Modifier.fillMaxWidth(0.7f))
            }
            if (!centerFits) CommitmentHeadline(percentage, report.committedCents, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CommitmentHeadline(percentage: String, cents: Long, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Comprometido", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        BasicText(percentage, Modifier.fillMaxWidth().testTag("commitment-percentage"),
            style = MaterialTheme.typography.headlineSmall.copy(color = LocalContentColor.current, textAlign = TextAlign.Center),
            maxLines = 2, autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 24.sp, stepSize = 1.sp))
        Amount(cents, false, size = 16)
    }
}

private fun degrees(microDegrees: Int): Float = microDegrees.toFloat() / CommitmentReports.MICRODEGREES_PER_DEGREE

private fun sliceColor(index: Int, kind: CommitmentSliceKind, palette: List<Color>, free: Color): Color =
    if (kind == CommitmentSliceKind.FREE) free else palette[index % palette.size]

internal fun LazyListScope.commitmentLegend(report: CommitmentReport, otherExpanded: Boolean, onToggleOther: () -> Unit) {
    if (report.entries.isEmpty() && report.slices.isEmpty()) return
    item(key = "commitment-legend-heading") {
        val legendTitle = if (report.incomePercentage == null) "Compromissos do mês" else "Legenda do comprometimento"
        Text(legendTitle, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
    }
    if (report.incomePercentage == null) {
        items(report.entries, key = { "commitment-entry:${it.id}" }) { entry ->
            CommitmentLegendRow(entry.label, entry.amountCents, null, null, "commitment-entry-${entry.id}")
        }
        return
    }
    itemsIndexed(report.slices, key = { _, slice -> "commitment-slice:${slice.key}" }) { index, slice ->
        val color = sliceColor(index, slice.kind, LocalFinanceColors.current.chart, MaterialTheme.colorScheme.outline)
        CommitmentLegendRow(slice.label, slice.amountCents, CommitmentReportText.percentage(slice.incomePercentage), color,
            "commitment-legend-${slice.key}") {
            if (slice.kind == CommitmentSliceKind.OTHER) TextButton(onClick = onToggleOther,
                modifier = Modifier.testTag("commitment-other-details").semantics {
                    stateDescription = if (otherExpanded) "Expandido" else "Recolhido"
                }) { Text(if (otherExpanded) "Ocultar itens de Outros" else "Ver itens de Outros") }
        }
    }
    val otherIndex = report.slices.indexOfFirst { it.kind == CommitmentSliceKind.OTHER }
    if (otherExpanded && otherIndex >= 0) {
        val other = report.slices[otherIndex]
        val members = other.entryIds.toSet()
        item(key = "commitment-other-heading") {
            Text("Itens de Outros", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        }
        items(report.entries.filter { it.id in members }, key = { "commitment-other:${it.id}" }) { entry ->
            val color = sliceColor(otherIndex, other.kind, LocalFinanceColors.current.chart, MaterialTheme.colorScheme.outline)
            CommitmentLegendRow(entry.label, entry.amountCents, entry.incomePercentage?.let { CommitmentReportText.percentage(it) },
                color, "commitment-entry-${entry.id}")
        }
    }
}

@Composable
private fun CommitmentLegendRow(
    label: String, cents: Long, percentage: String?, color: Color?, tag: String,
    details: @Composable () -> Unit = {},
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(tag).semantics(mergeDescendants = true) { },
        shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (LocalDensity.current.fontScale > 1.25f) {
                LegendLabel(label, percentage, color)
                Amount(cents, false, size = 16)
            } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendLabel(label, percentage, color, Modifier.weight(1f))
                Amount(cents, false, Modifier.widthIn(max = 140.dp), size = 16)
            }
            details()
        }
    }
}

@Composable
private fun LegendLabel(label: String, percentage: String?, color: Color?, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (color != null) Box(Modifier.size(12.dp).background(color, CircleShape))
        Column {
            Text(label, style = MaterialTheme.typography.titleSmall)
            percentage?.let { Text("$it da renda", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
