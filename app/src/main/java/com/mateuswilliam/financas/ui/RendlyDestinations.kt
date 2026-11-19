package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.core.*

internal enum class RendlyDestination(val id: Int) {
    MONTH(0), GRAPHS(1), FIXED(2), MORE(3), REVIEW(5), SETTINGS(6), PLANNER(7), RESERVE(8),
    COMMITMENT(10), PROJECTION(11);

    val primaryTab get() = when (this) {
        MONTH, GRAPHS, FIXED, MORE -> this
        else -> MORE
    }
    val parent get() = when (this) {
        REVIEW -> SETTINGS
        PROJECTION -> PLANNER
        SETTINGS, PLANNER, RESERVE, COMMITMENT -> MORE
        else -> MONTH
    }
}

@Composable
internal fun MoreScreen(onOpen: (RendlyDestination) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("more-list"), contentPadding = PaddingValues(FinanceSpacing.page), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SectionTitle("Mais", "Planejamento e preferências") }
        listOf(RendlyDestination.PLANNER to "Planejador", RendlyDestination.RESERVE to "Reserva de emergência",
            RendlyDestination.COMMITMENT to "Comprometimento", RendlyDestination.SETTINGS to "Ajustes").forEach { (destination, label) ->
            item { OutlinedButton({ onOpen(destination) }, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("more-${destination.id}")) { Text(label) } }
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
