package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.core.*
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Extrato com o resultado registrado e as previsões identificadas por dia. */
@Composable
internal fun MonthLedgerScreen(records: List<Record>, month: YearMonth, onMonth: (YearMonth) -> Unit,
    hidden: Boolean, category: Category?, onCategory: (Category?) -> Unit, accounts: List<AccountOption>,
    accountKey: String?, onAccount: (String?) -> Unit, onEdit: (Record) -> Unit,
    expenseFilter: ExpenseFilter? = null, onClearExpenseFilter: () -> Unit = {},
    accountTotal: AccountTotal? = null, onAccounts: () -> Unit = {},
    period: BudgetPeriod? = null, accountBalances: List<AccountBalance>? = null,
    balanceReferences: List<AccountBalanceReference> = emptyList(), onBalanceReference: (AccountBalanceReference) -> Unit = {},
    forecastLines: List<ForecastLine> = emptyList(), onForecast: (ForecastLine) -> Unit = {}, onCycle: () -> Unit = {},
    onIgnoreForecast: (ForecastLine) -> Unit = {}, onStopForecast: (ForecastLine) -> Unit = {},
    onEditFutureForecast: (ForecastLine) -> Unit = {}, busy: Boolean = false, scheduledRecords: List<Record> = emptyList(),
    reserveNeedsReference: Boolean = false) {
    val zone = ZoneId.systemDefault()
    val selected = remember(records, month, period, category, accountKey, expenseFilter, zone) {
        val filtered = records.filter { record ->
            (category == null || record.category == category) && (expenseFilter == null || expenseFilter.matches(record)) &&
                (accountKey == null || AccountNames.key(record.bank) == accountKey ||
                    (record.movement == Movement.TRANSFER && AccountNames.key(record.destination) == accountKey))
        }
        if (period == null) Ledger.month(filtered, month, zone) else Ledger.period(filtered, period, zone)
    }
    val groups = remember(selected.records, zone) { selected.records.groupBy { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() } }
    val shownPeriod = period ?: BudgetPeriod(month.atDay(1), month.plusMonths(1).atDay(1))
    val referenceGroups = remember(balanceReferences, shownPeriod, accountKey, category, expenseFilter, zone) {
        balanceReferences.filter { reference ->
            category == null && expenseFilter == null && (accountKey == null || reference.accountKey == accountKey) &&
                Instant.ofEpochMilli(reference.effectiveAt).atZone(zone).toLocalDate() in shownPeriod
        }.groupBy { Instant.ofEpochMilli(it.effectiveAt).atZone(zone).toLocalDate() }
    }
    val forecastGroups = remember(forecastLines, shownPeriod, accountKey, category, expenseFilter) {
        forecastLines.filter { line ->
            line.date in shownPeriod && (category == null || line.category == category) &&
                (accountKey == null || AccountNames.key(line.rule.account) == accountKey) && expenseFilter == null
        }.groupBy { it.date }
    }
    val scheduledGroups = scheduledRecords.filter { row ->
        (category == null || row.category == category) && (expenseFilter == null || expenseFilter.matches(row)) &&
            (accountKey == null || AccountNames.key(row.bank) == accountKey || (row.movement == Movement.TRANSFER && AccountNames.key(row.destination) == accountKey))
    }.groupBy { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() }
    val days = (groups.keys + referenceGroups.keys + forecastGroups.keys + scheduledGroups.keys).sortedDescending()
    val accountOptions = (listOf<String?>(null) + accounts.map { it.key } + listOfNotNull(accountKey)).distinct()
    // Reservar a área do botão na viewport evita que scrollTo pare com uma linha atrás do +.
    LazyColumn(Modifier.fillMaxSize().padding(bottom = 80.dp).testTag("records-list"), contentPadding = PaddingValues(FinanceSpacing.page, 8.dp, FinanceSpacing.page, FinanceSpacing.listBottom),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { MonthPicker(month, onMonth) }
        period?.let { chosen -> item {
            Column {
                FinancialPeriodLabel(chosen)
                TextButton(onCycle, Modifier.testTag("month-cycle-settings")) { Text("Início do mês financeiro") }
            }
        } }
        accountTotal?.let { total -> item { AccountTotalCard(total, hidden, onAccounts, accountBalances, period, reserveNeedsReference) } }
        item {
            Column(Modifier.testTag("month-totals"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (category != null || accountKey != null || expenseFilter != null) Text("Totais dos lançamentos filtrados", style = MaterialTheme.typography.labelMedium)
                if (LocalDensity.current.fontScale > 1.25f) {
                    MonthFigure("Receitas", selected.income, hidden, "month-income", LocalFinanceColors.current.income)
                    MonthFigure("Despesas", selected.expenses, hidden, "month-expenses", LocalFinanceColors.current.expense)
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.weight(1f)) { MonthFigure("Receitas", selected.income, hidden, "month-income", LocalFinanceColors.current.income) }
                        Box(Modifier.weight(1f)) { MonthFigure("Despesas", selected.expenses, hidden, "month-expenses", LocalFinanceColors.current.expense) }
                    }
                }
                MonthFigure("Resultado do período", selected.result, hidden, "month-net",
                    if (selected.result < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, prominent = true)
                Text("Receitas menos despesas registradas. Transferências próprias não mudam o resultado.", style = MaterialTheme.typography.bodySmall)
            }
        }
        item { FinanceDisclosure("Filtrar categoria e conta", "records-filters") {
            ChoiceField("Filtrar categoria", category, listOf(null) + Category.entries, { it?.label ?: "Todas" }, onChange = onCategory)
            ChoiceField("Filtrar conta", accountKey, accountOptions, { key ->
                if (key == null) "Todas" else privateDisplayText(accounts.firstOrNull { it.key == key }?.label ?: "Conta arquivada", hidden)
            }, onChange = onAccount)
            if (category != null || accountKey != null || expenseFilter != null) TextButton({ onCategory(null); onAccount(null); onClearExpenseFilter() }) { Text("Limpar filtros") }
        } }
        if (accountKey != null) item { InputChip(selected = true, onClick = { onAccount(null) },
            label = { Text("Conta: ${privateDisplayText(accounts.firstOrNull { it.key == accountKey }?.label ?: "Conta arquivada", hidden)} · Limpar filtro") }) }
        if (category != null) item { InputChip(selected = true, onClick = { onCategory(null) }, label = { Text("${category.label} · Limpar filtro") }) }
        if (expenseFilter != null) item { InputChip(selected = true, onClick = onClearExpenseFilter,
            label = { Text("${privateDisplayText(expenseFilter.label, hidden)} · Limpar filtro") }) }
        if (days.isEmpty()) item { Text("Nenhum lançamento neste período. Use + para acrescentar.") }
        days.forEach { day ->
            item(key = "day:$day") { Text(day.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall) }
            items(groups[day].orEmpty(), key = { "record:${it.id}" }) { row ->
                Box(Modifier.clip(MaterialTheme.shapes.large)) { RecordRow(row, hidden) { onEdit(row) } }
            }
            items(referenceGroups[day].orEmpty(), key = { "balance:${it.id}" }) { reference ->
                val account = accounts.firstOrNull { it.key == reference.accountKey }?.label ?: "Conta arquivada"
                LedgerSupportingRow("Saldo inicial / referência", privateDisplayText(account, hidden),
                    reference.balanceCents, hidden, "balance-reference-${reference.id}",
                    "Valor de referência da conta; não compõe receitas ou despesas.") { onBalanceReference(reference) }
            }
            items(scheduledGroups[day].orEmpty(), key = { "scheduled:${it.id}" }) { row ->
                LedgerSupportingRow("Agendado · ${row.movement.label}", privateDisplayText(row.description, hidden), row.amountCents,
                    hidden, "scheduled-${row.id}", "Data futura; ainda fora do resultado registrado e do saldo disponível.") { onEdit(row) }
            }
            forecastLineItems(forecastGroups[day].orEmpty(), hidden, busy, onForecast, onIgnoreForecast, onStopForecast, onEditFutureForecast)

        }
    }
}

@Composable
private fun MonthFigure(label: String, cents: Long, hidden: Boolean, tag: String, accent: Color,
    prominent: Boolean = false) {
    Surface(shape = MaterialTheme.shapes.large,
        color = if (prominent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(FinanceSpacing.card).semantics(mergeDescendants = true) {}.testTag(tag),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge,
                color = if (prominent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            CompositionLocalProvider(LocalContentColor provides accent) {
                Amount(cents, hidden, size = if (prominent) 26 else 22, maxLines = 1)
            }
        }
    }
}

@Composable
internal fun FinancialPeriodLabel(period: BudgetPeriod) {
    val format = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    Text("${period.start.format(format)} a ${period.endExclusive.minusDays(1).format(format)}",
        Modifier.testTag("financial-period"), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun AccountTotalCard(total: AccountTotal, hidden: Boolean, onAccounts: () -> Unit,
    balances: List<AccountBalance>?, period: BudgetPeriod?, reserveNeedsReference: Boolean) {
    Surface(onClick = onAccounts, modifier = Modifier.fillMaxWidth().testTag("account-total"), shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(FinanceSpacing.card), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (balances == null) "Conta total" else "Patrimônio total", style = MaterialTheme.typography.titleSmall)
            if (total.hasBalance) Amount(total.cents, hidden, Modifier.testTag("account-total-amount"), size = 28)
            balances?.let { values ->
                val funds = remember(values) { AccountFunds.of(values) }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AccountFundLine("Contas de uso", funds.operating, hidden, "operating-balance")
                    AccountFundLine("Reserva de emergência", funds.reserve, hidden, "reserve-balance")
                }
            }
            Text(when {
                !total.hasBalance -> "Informe o saldo de uma conta para ver o total dos seus bancos."
                total.partial -> "Saldo parcial: faltam contas sem saldo informado. Toque para ajustar."
                else -> "Soma de todas as contas. Toque para ajustar os saldos."
            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (reserveNeedsReference) Text("Configure o saldo da reserva em Mais para incluí-la no patrimônio.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (period != null) Text("Acumulado até ${minOf(period.endExclusive.minusDays(1), java.time.LocalDate.now()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AccountFundLine(label: String, total: AccountTotal, hidden: Boolean, tag: String) {
    Column(Modifier.semantics(mergeDescendants = true) {}.testTag(tag)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (total.hasBalance) Amount(total.cents, hidden, size = 18)
        else Text("Saldo não informado", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LedgerSupportingRow(label: String, title: String, cents: Long, hidden: Boolean,
    tag: String, detail: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag(tag),
        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(FinanceSpacing.card), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleSmall)
            Amount(cents, hidden, size = 18)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
