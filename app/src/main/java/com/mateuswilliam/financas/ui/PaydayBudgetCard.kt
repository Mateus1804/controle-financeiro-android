package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.core.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val paydayFormat = DateTimeFormatter.ofPattern("dd/MM")

/** Quanto ainda pode ser gasto até o próximo pagamento; estado nulo pede o dado que falta. */
@Composable
internal fun PaydayBudgetCard(budget: PaydayBudget?, hasBalance: Boolean, hidden: Boolean, partialBalance: Boolean = false, onAccounts: () -> Unit = {}, onSalary: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.fillMaxWidth().testTag("payday-budget").padding(FinanceSpacing.card), verticalArrangement = Arrangement.spacedBy(FinanceSpacing.medium)) {
            SectionTitle("Até o próximo salário", budget?.let { "Cálculo até ${it.payday.format(paydayFormat)} (próximo salário previsto)" })
            if (partialBalance) Text("Saldo parcial · uma ou mais contas ativas estão sem saldo informado.",
                Modifier.testTag("payday-partial"), style = MaterialTheme.typography.bodySmall)
            when {
                !hasBalance -> {
                    Text("Cadastre o saldo de uma conta para calcular quanto você pode gastar até o pagamento.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton(onAccounts, Modifier.heightIn(min = FinanceSpacing.touch).testTag("payday-accounts")) { Text("Configurar saldo das contas") }
                }
                budget == null -> {
                    Text("Informe a data do próximo pagamento do salário para calcular o período.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton(onSalary, Modifier.heightIn(min = FinanceSpacing.touch).testTag("payday-missing")) { Text("Informar data do salário") }
                }
                else -> {
                    val deficit = budget.deficitCents > 0
                    val accent = if (deficit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    Text(if (deficit) "Déficit até o pagamento" else "Pode gastar até o pagamento", style = MaterialTheme.typography.labelLarge, color = accent)
                    CompositionLocalProvider(LocalContentColor provides accent) {
                        Amount(if (deficit) budget.deficitCents else budget.totalCents, hidden, Modifier.testTag("payday-total"), size = 32)
                    }
                    if (!deficit) budget.weeklyCents?.let { PaydayLine("Média por semana", it, hidden, Modifier.testTag("payday-weekly")) }
                    PaydayLine("Disponível hoje · contas ativas", budget.availableCents, hidden)
                    PaydayLine("Compromissos previstos", budget.pendingCommitmentsCents, hidden)
                    PaydayLine("Se os gastos variáveis previstos acontecerem", budget.withVariablesCents, hidden, Modifier.testTag("payday-with-variables"))
                    Text("A reserva não é descontada. Gastos variáveis previstos aparecem apenas no cenário separado.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Estimativa de planejamento; não altera saldos nem lançamentos.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (hasBalance) TextButton(onAccounts, Modifier.heightIn(min = FinanceSpacing.touch).testTag("payday-accounts")) { Text("Gerenciar contas") }
        }
    }
}

@Composable
private fun PaydayLine(label: String, cents: Long, hidden: Boolean, modifier: Modifier = Modifier) {
    if (label.length > 40 || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.25f) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Amount(cents, hidden, size = 16)
        }
    } else Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Amount(cents, hidden, Modifier.widthIn(max = 160.dp), size = 16)
    }
}
