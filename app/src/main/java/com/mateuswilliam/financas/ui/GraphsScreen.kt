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
internal fun GraphsScreen(records: List<Record>, month: YearMonth, onMonth: (YearMonth) -> Unit, comparison: MonthComparison,
    hidden: Boolean, onFilter: (ExpenseFilter) -> Unit, onCategory: (Category) -> Unit, period: BudgetPeriod? = null) {
    LazyColumn(Modifier.testTag("graphs-screen"), contentPadding = PaddingValues(FinanceSpacing.page), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionTitle("Gráficos", "Gastos registrados no período selecionado") }
        item { MonthPicker(month, onMonth) }
        period?.let { chosen -> item { FinancialPeriodLabel(chosen) } }
        item { Text("A rosca mostra gastos brutos; estornos reduzem os valores líquidos da comparação e da tela Mês. Toque na legenda para ver os lançamentos.", style = MaterialTheme.typography.bodySmall) }
        item { ExpenseChart(records, month, hidden, period, onFilter) }
        item { MonthComparisonCard(comparison, hidden, period, onCategory) }
    }
}

