package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.core.CentralFinancialSummary

@Composable
internal fun CentralProjectionCard(summary: CentralFinancialSummary, hidden: Boolean) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.fillMaxWidth().padding(FinanceSpacing.card).testTag("central-projection"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Projeção do período", style = MaterialTheme.typography.titleMedium)
            Text("Resultado registrado", style = MaterialTheme.typography.labelMedium)
            Amount(summary.recorded.result, hidden, Modifier.testTag("projection-recorded"))
            Text("Receitas registradas e previstas", style = MaterialTheme.typography.bodySmall)
            Amount(summary.projectedIncomeCents, hidden, size = 20)
            Text("Despesas registradas e previstas", style = MaterialTheme.typography.bodySmall)
            Amount(summary.projectedExpensesCents, hidden, size = 20)
            HorizontalDivider()
            Text("Quanto pode sobrar", style = MaterialTheme.typography.titleSmall)
            Amount(summary.projectedResultCents, hidden, Modifier.testTag("projection-result"), size = 28)
            Text("Inclui as previsões que ainda não têm lançamento vinculado. Não altera o saldo das contas.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
