package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.core.PeriodProjection

@Composable
internal fun PeriodResultCard(projection: PeriodProjection, hidden: Boolean, forecastsLoaded: Boolean) {
    Surface(Modifier.fillMaxWidth().testTag("period-summary"), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Column(Modifier.padding(FinanceSpacing.card), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Resultado realizado do período", style = MaterialTheme.typography.titleSmall)
            Amount(projection.realized.result, hidden, size = 36)
            Text("Receitas menos despesas do mês. Não é o saldo das contas.", style = MaterialTheme.typography.bodySmall)
            if (hidden) Text("Receitas e despesas ocultas", style = MaterialTheme.typography.bodySmall)
            else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FinanceSpacing.card)) {
                PeriodFigure("Receitas líquidas", projection.realized.income, Modifier.weight(1f))
                PeriodFigure("Despesas líquidas", projection.realized.expenses, Modifier.weight(1f))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
            Text("Resultado projetado do período", style = MaterialTheme.typography.titleSmall)
            if (forecastsLoaded) {
                Amount(projection.projectedResult, hidden, Modifier.testTag("projected-result"), size = 22)
                Text(when {
                    projection.pendingCount == 0 -> "Sem previsões pendentes neste mês."
                    projection.realized.records.none { it.movement in setOf(com.mateuswilliam.financas.core.Movement.INCOME, com.mateuswilliam.financas.core.Movement.EXPENSE, com.mateuswilliam.financas.core.Movement.REFUND) } -> "Ainda sem receitas ou despesas realizadas. Projeção com ${projection.pendingCount} previsão(ões) pendente(s)."
                    else -> "Realizado + ${projection.pendingCount} previsão(ões) ainda pendente(s)."
                }, style = MaterialTheme.typography.bodySmall)
            } else Text("Carregando previsões…", style = MaterialTheme.typography.bodySmall)
            FinanceDisclosure("Entenda estes valores", "period-summary-details") {
                Text("Resultado é receita menos despesa. A projeção inclui somente previsões ativas ainda não realizadas. Saldo inicial, transferências, limites de orçamento e renda de referência não alteram estes resultados. Previsões não alteram o saldo disponível.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PeriodFigure(label: String, cents: Long, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Amount(cents, false, size = 18)
    }
}
