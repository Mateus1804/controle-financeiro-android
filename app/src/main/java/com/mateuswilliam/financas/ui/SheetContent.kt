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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.R
import com.mateuswilliam.financas.core.*
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayHeading = DateTimeFormatter.ofPattern("d 'de' MMMM", Locale.forLanguageTag("pt-BR"))
private fun Long.date(zone: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

@Composable
internal fun SheetOverview(
    summary: SheetSummary, month: YearMonth, period: BudgetPeriod, zone: ZoneId, hidden: Boolean,
    onMonth: (YearMonth) -> Unit, onSaveToReserve: () -> Unit, onCategory: (Category) -> Unit,
    onToggleArchive: (String, Boolean) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().testTag("sheet-summary"), contentPadding = sheetPadding(),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { SheetPeriodPicker(month, period, onMonth) }
        item {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SheetMetric("Resultado do período", summary.ledger.result, hidden, "sheet-result", size = 32)
                    if (LocalDensity.current.fontScale > 1.3f || LocalConfiguration.current.screenWidthDp < 340) {
                        SheetMetric("Receitas", summary.ledger.income, hidden, "sheet-income")
                        SheetMetric("Despesas", summary.ledger.expenses, hidden, "sheet-expense")
                    } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        SheetMetric("Receitas", summary.ledger.income, hidden, "sheet-income", Modifier.weight(1f))
                        SheetMetric("Despesas", summary.ledger.expenses, hidden, "sheet-expense", Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            OutlinedCard(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.background)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SheetMetric("Patrimônio total", summary.funds.wealth.cents, hidden, "sheet-wealth", size = 26)
                    Text("Saldos acumulados até ${summary.balanceAsOf.date(zone).format(financeDateFormat)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    CompactMetric("Contas de uso", summary.funds.operating.cents, hidden, "sheet-operating")
                    CompactMetric(AccountFunds.RESERVE_NAME, summary.funds.reserve.cents, hidden, "sheet-reserve")
                    if (summary.savedToReserve > 0 || summary.withdrawnFromReserve > 0) {
                        if (summary.savedToReserve > 0) CompactMetric("Guardado no período", summary.savedToReserve, hidden, "sheet-saved")
                        if (summary.withdrawnFromReserve > 0) CompactMetric("Resgatado no período", summary.withdrawnFromReserve, hidden, "sheet-withdrawn")
                    }
                    if (summary.balances.isNotEmpty()) FinanceDisclosure("Saldos por conta", "sheet-account-list") {
                        for (row in summary.balances) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                val label = privateDisplayText(row.account.label, hidden)
                                Column(Modifier.weight(1f)) {
                                    CompactMetric(label, row.balanceCents ?: 0L, hidden, "sheet-account-${row.account.key}")
                                    if (row.archived) Text("Arquivada", style = MaterialTheme.typography.bodySmall)
                                }
                                ContextualActions(label, "sheet-account-actions-${row.account.key}", true,
                                    listOf(FinanceAction(if (row.archived) "Reativar conta" else "Arquivar conta", {
                                        onToggleArchive(row.account.key, !row.archived)
                                    })))
                            }
                        }
                    }
                    if (summary.balances.any { !it.archived && !AccountFunds.isReserve(it.account.label) } &&
                        summary.balances.none { it.archived && AccountFunds.isReserve(it.account.label) }) {
                        TextButton(onSaveToReserve, Modifier.heightIn(min = 48.dp)) { Text("Guardar na reserva") }
                    }
                }
            }
        }
        item { SheetCategoryChart(summary.spending, hidden, onCategory) }
    }
}

@Composable
private fun SheetMetric(label: String, cents: Long, hidden: Boolean, tag: String, modifier: Modifier = Modifier, size: Int = 20) {
    Column(modifier.testTag(tag).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Amount(cents, hidden, size = size)
    }
}

@Composable
private fun CompactMetric(label: String, cents: Long, hidden: Boolean, tag: String) {
    val modifier = Modifier.fillMaxWidth().testTag(tag).semantics(mergeDescendants = true) {}
    if (LocalDensity.current.fontScale > 1.25f) Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Amount(cents, hidden, size = 16)
    } else Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Amount(cents, hidden, Modifier.widthIn(max = 140.dp), size = 16)
    }
}

@Composable
private fun SheetCategoryChart(report: ExpenseReport, hidden: Boolean, onCategory: (Category) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("sheet-chart"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Gastos por categoria")
        if (report.groups.isEmpty()) Text("Sem despesas neste período.", style = MaterialTheme.typography.bodyMedium)
        else {
            val palette = LocalFinanceColors.current.chart
            val track = MaterialTheme.colorScheme.surfaceContainerHigh
            val angles = remember(report.groups) { expenseSliceAngles(report.groups.map { it.cents }) }
            val stacked = LocalDensity.current.fontScale > 1.25f || LocalConfiguration.current.screenWidthDp < 340
            Canvas(Modifier.align(Alignment.CenterHorizontally).size(168.dp).clearAndSetSemantics {}) {
                val width = 20.dp.toPx()
                if (hidden) {
                    drawCircle(track, radius = (size.minDimension - width) / 2, style = Stroke(width))
                } else {
                    var start = -90f
                    angles.forEachIndexed { index, angle ->
                        val gap = if (angles.size == 1) 0f else minOf(1.4f, angle / 3)
                        drawArc(palette[index % palette.size], start + gap / 2, angle - gap, useCenter = false,
                            topLeft = Offset(width / 2, width / 2), size = Size(size.width - width, size.height - width), style = Stroke(width))
                        start += angle
                    }
                }
            }
            report.groups.forEachIndexed { index, group ->
                val legendRow = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = "Ver lançamentos") {
                    onCategory(Category.valueOf(group.key))
                }.padding(vertical = 4.dp)
                val dot = if (hidden) track else palette[index % palette.size]
                if (stacked) Column(legendRow, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(dot, CircleShape))
                        Text(group.label, style = MaterialTheme.typography.bodyMedium)
                    }
                    SheetLegendValues(group, hidden, Alignment.Start)
                } else Row(legendRow, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(dot, CircleShape))
                    Text(group.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    SheetLegendValues(group, hidden, Alignment.End, Modifier.widthIn(max = 140.dp))
                }
            }
            if (!hidden && report.groups.any { it.cents.toDouble() / report.total * 360 < MIN_EXPENSE_SLICE_DEGREES }) {
                Text("Fatias pequenas foram ampliadas. A legenda mostra os percentuais reais.", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (report.refunds > 0) Text("O gráfico mostra as despesas antes dos estornos.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SheetLegendValues(group: ExpenseGroup, hidden: Boolean, alignment: Alignment.Horizontal, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = alignment) {
        Amount(group.cents, hidden, size = 16)
        if (!hidden) Text(ExpenseLegendText.percentage(group.basisPoints), style = MaterialTheme.typography.labelMedium)
    }
}

private data class SheetLine(val at: Long, val record: Record? = null, val reference: AccountBalanceReference? = null) {
    val key: String get() = record?.let { "record:${it.id}" } ?: "balance:${reference?.id}"
}

@Composable
internal fun SheetEntries(
    summary: SheetSummary, records: List<Record>, references: List<AccountBalanceReference>, month: YearMonth,
    period: BudgetPeriod, asOf: Long, zone: ZoneId, hidden: Boolean, category: Category?, account: String?,
    onCategory: (Category?) -> Unit, onAccount: (String?) -> Unit, onMonth: (YearMonth) -> Unit,
    onEdit: (Record) -> Unit, onBalance: (AccountBalanceReference) -> Unit,
) {
    val rows = remember(records, references, category, account, period, zone) {
        val movements = records.asSequence().filter { row ->
            row.occurredAt.date(zone) in period && (category == null || row.category == category && row.movement != Movement.TRANSFER) &&
                (account == null || AccountNames.key(row.bank) == account || AccountNames.key(row.destination) == account)
        }.map { SheetLine(it.occurredAt, record = it) }
        val openings = references.asSequence().filter {
            category == null && (account == null || it.accountKey == account) && it.effectiveAt.date(zone) in period
        }.groupBy { it.accountKey to it.effectiveAt }.values.map { sameDate ->
            val reference = sameDate.maxWith(compareBy<AccountBalanceReference> { it.createdAt }.thenBy { it.id })
            SheetLine(reference.effectiveAt, reference = reference)
        }
        (movements + openings).sortedByDescending { it.at }.groupBy { it.at.date(zone) }
    }
    val accounts = remember(summary.balances) { summary.balances.associateBy { it.account.key } }
    val accountOptions = listOf<String?>(null) + accounts.keys
    fun label(key: String?) = if (key == null) "Todas" else privateDisplayText(accounts[key]?.account?.label ?: key, hidden)
    LazyColumn(Modifier.fillMaxSize().testTag("records-list"), contentPadding = sheetPadding(),
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        item { SheetPeriodPicker(month, period, onMonth) }
        item {
            FinanceDisclosure(if (category != null || account != null) "Filtros ativos" else "Filtrar lançamentos", "sheet-filters",
                initiallyExpanded = category != null || account != null) {
                ChoiceField("Categoria", category, listOf<Category?>(null) + Category.entries,
                    { it?.label ?: "Todas" }, onChange = onCategory)
                ChoiceField("Conta", account, accountOptions, ::label, onChange = onAccount)
            }
        }
        if (rows.isEmpty()) item {
            Text(if (category != null || account != null) "Nenhum lançamento para esses filtros." else "Nenhum lançamento neste período.",
                Modifier.padding(top = 14.dp), style = MaterialTheme.typography.bodyMedium)
        }
        for ((date, lines) in rows) {
            item(key = "day:$date") {
                Text(date.format(dayHeading), Modifier.padding(top = 12.dp).semantics { heading() }, style = MaterialTheme.typography.titleSmall)
            }
            items(lines, key = { it.key }) { line ->
                val record = line.record
                if (record != null) {
                    if (line.at > asOf) Text("Agendado · entra nos totais na data registrada",
                        Modifier.padding(top = 6.dp, start = 12.dp), style = MaterialTheme.typography.labelSmall)
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        RecordRow(record, hidden) { onEdit(record) }
                    }
                } else line.reference?.let { reference ->
                    Surface(onClick = { onBalance(reference) }, shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.padding(14.dp)) {
                            CompactMetric("Saldo inicial · ${label(reference.accountKey)}", reference.balanceCents, hidden, "sheet-reference-${reference.id}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetPeriodPicker(month: YearMonth, period: BudgetPeriod, onMonth: (YearMonth) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MonthPicker(month, onMonth)
        FinancialPeriodLabel(period)
    }
}

private fun sheetPadding() = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 88.dp)
