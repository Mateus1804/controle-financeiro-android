package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.core.*
import java.time.YearMonth

@Composable
internal fun MoreScreen(onOpen: (Int) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("more-list"), contentPadding = PaddingValues(FinanceSpacing.page), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SectionTitle("Mais", "Planejamento e preferências") }
        listOf(9 to "Salário", 7 to "Planejador", 8 to "Reserva de emergência", 10 to "Comprometimento", 6 to "Ajustes").forEach { (destination, label) ->
            item { OutlinedButton({ onOpen(destination) }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("more-$destination")) { Text(label) } }
        }
    }
}

@Composable
internal fun SalaryScreen(month: YearMonth, onMonth: (YearMonth) -> Unit, summary: SalarySummary, payday: PaydayBudget?,
    accounts: List<AccountBalance>, hidden: Boolean, onEditSalary: () -> Unit, onAccounts: () -> Unit, onNewSalary: () -> Unit) {
    LazyColumn(Modifier.testTag("salary-screen"), contentPadding = PaddingValues(FinanceSpacing.page), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { MonthPicker(month, onMonth) }
        item { MonthlyResultCard(summary, hidden, onEditSalary) }
        item { PaydayBudgetCard(payday, accounts.any { !it.archived && it.balanceCents != null }, hidden,
            partialBalance = accounts.any { !it.archived && it.balanceCents == null }, onAccounts = onAccounts, onSalary = onNewSalary) }
    }
}

@Composable
internal fun FixedForecastScreen(data: ForecastDataset, month: YearMonth, onMonth: (YearMonth) -> Unit, hidden: Boolean, busy: Boolean,
    onEdit: (ForecastLine) -> Unit, onIgnore: (ForecastLine) -> Unit, onStop: (ForecastLine) -> Unit, onNew: () -> Unit, onTemplates: () -> Unit) {
    val lines = remember(data, month) { ForecastCalculator.report(data, BudgetPeriod(month.atDay(1), month.plusMonths(1).atDay(1))).lines
        .filter { it.kind == ForecastKind.EXPENSE && it.frequency != ForecastFrequency.ONCE } }
    LazyColumn(Modifier.testTag("fixed-screen"), contentPadding = PaddingValues(FinanceSpacing.page), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("Fixas e parcelas", "Previsões mensais. Não é necessário confirmar pagamentos.") }
        item { MonthPicker(month, onMonth) }
        item { Button(onNew, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Nova despesa fixa ou parcela") } }
        item { TextButton(onTemplates, Modifier.fillMaxWidth()) { Text("Usar modelo de gasto fixo") } }
        if (lines.isEmpty()) item { Text("Nenhuma despesa fixa ou parcela prevista para este mês.") }
        forecastLineItems(lines, hidden, busy, onEdit, onIgnore, onStop)
    }
}

@Composable
internal fun CommitmentScreen(report: ForecastReport, hidden: Boolean) {
    LazyColumn(Modifier.testTag("commitment-screen"), contentPadding = PaddingValues(FinanceSpacing.page), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("Comprometimento", "Previsões do ciclo ${report.period.start} a ${report.period.endExclusive.minusDays(1)}") }
        item { Text("Receitas previstas"); Amount(report.incomeCents, hidden) }
        item { Text("Gastos fixos e compromissos"); Amount(report.commitmentsCents, hidden) }
        item { Text("Gastos totais previstos"); Amount(report.totalExpensesCents, hidden) }
        item { Text("Margem prevista"); Amount(report.marginCents, hidden) }
        item { Text(if (hidden) "Renda comprometida: ••••" else "Renda comprometida: ${report.expensePercentage?.toPlainString()?.replace('.', ',')?.plus("%") ?: "informe uma receita"}") }
        item { RendlyCategories(report, hidden) }
    }
}
