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
        listOf(7 to "Planejador", 8 to "Reserva de emergência", 10 to "Comprometimento", 6 to "Ajustes").forEach { (destination, label) ->
            item { OutlinedButton({ onOpen(destination) }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("more-$destination")) { Text(label) } }
        }
    }
}

@Composable
internal fun CommitmentScreen(summary: CentralFinancialSummary, hidden: Boolean) {
    val report = summary.projectedReport
    LazyColumn(Modifier.testTag("commitment-screen"), contentPadding = PaddingValues(FinanceSpacing.page), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("Comprometimento", "Registrado e previsto no ciclo ${report.period.start} a ${report.period.endExclusive.minusDays(1)}") }
        item { Text("Receitas registradas e previstas"); Amount(report.incomeCents, hidden) }
        item { Text("Gastos fixos e compromissos"); Amount(report.commitmentsCents, hidden) }
        item { Text("Gastos registrados e previstos"); Amount(report.totalExpensesCents, hidden) }
        item { Text("Margem prevista"); Amount(report.marginCents, hidden) }
        item { Text(if (hidden) "Renda comprometida: ••••" else "Renda comprometida: ${report.expensePercentage?.toPlainString()?.replace('.', ',')?.plus("%") ?: "informe uma receita"}") }
        item { RendlyCategories(summary, hidden) }
    }
}
