package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
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

/** Extrato realizado: as previsões mantêm seus próprios destinos e não entram duas vezes nos totais. */
@Composable
internal fun MonthLedgerScreen(records: List<Record>, month: YearMonth, onMonth: (YearMonth) -> Unit,
    hidden: Boolean, category: Category?, onCategory: (Category?) -> Unit, accounts: List<AccountOption>,
    accountKey: String?, onAccount: (String?) -> Unit, onEdit: (Record) -> Unit,
    expenseFilter: ExpenseFilter? = null, onClearExpenseFilter: () -> Unit = {}) {
    val zone = ZoneId.systemDefault()
    val selected = remember(records, month, category, accountKey, expenseFilter, zone) {
        Ledger.month(records.filter { record ->
            (category == null || record.category == category) && (expenseFilter == null || expenseFilter.matches(record)) &&
                (accountKey == null || AccountNames.key(record.bank) == accountKey ||
                    (record.movement == Movement.TRANSFER && AccountNames.key(record.destination) == accountKey))
        }, month, zone)
    }
    val groups = remember(selected.records, zone) { selected.records.groupBy { Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() } }
    val accountOptions = (listOf<String?>(null) + accounts.map { it.key } + listOfNotNull(accountKey)).distinct()
    // Reservar a área do botão na viewport evita que scrollTo pare com uma linha atrás do +.
    LazyColumn(Modifier.fillMaxSize().padding(bottom = 80.dp).testTag("records-list"), contentPadding = PaddingValues(FinanceSpacing.page, 8.dp, FinanceSpacing.page, FinanceSpacing.listBottom),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { MonthPicker(month, onMonth) }
        item {
            Column(Modifier.testTag("month-totals"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (category != null || accountKey != null || expenseFilter != null) Text("Totais dos lançamentos filtrados", style = MaterialTheme.typography.labelMedium)
                if (LocalDensity.current.fontScale > 1.25f) {
                    MonthFigure("Receitas", selected.income, hidden, "month-income")
                    MonthFigure("Despesas", selected.expenses, hidden, "month-expenses")
                    MonthFigure("Saldo do mês", selected.result, hidden, "month-net")
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.weight(1f)) { MonthFigure("Receitas", selected.income, hidden, "month-income") }
                        Box(Modifier.weight(1f)) { MonthFigure("Despesas", selected.expenses, hidden, "month-expenses") }
                    }
                    MonthFigure("Saldo do mês", selected.result, hidden, "month-net")
                }
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
        if (groups.isEmpty()) item { Text("Nenhum lançamento neste mês. Use + para acrescentar.") }
        groups.forEach { (day, rows) ->
            item(key = "day:$day") { Text(day.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall) }
            items(rows, key = { it.id }) { row -> RecordRow(row, hidden) { onEdit(row) } }
        }
    }
}

@Composable
private fun MonthFigure(label: String, cents: Long, hidden: Boolean, tag: String) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.testTag(tag)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Amount(cents, hidden, size = 22, maxLines = 1)
    }
}
