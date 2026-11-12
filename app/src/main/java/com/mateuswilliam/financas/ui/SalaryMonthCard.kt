package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.core.*
import java.time.YearMonth

@Composable
internal fun MonthlyResultCard(summary: SalarySummary, hidden: Boolean, onSalary: () -> Unit) {
    val salaryCents = summary.salaryCents
    val remaining = summary.remainingCents
    val exceeded = remaining != null && remaining < 0
    val accent = if (exceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.fillMaxWidth().testTag("salary-month-summary").padding(FinanceSpacing.card),
            verticalArrangement = Arrangement.spacedBy(FinanceSpacing.medium)) {
            SectionTitle("Resultado do mês", "Gastos realizados e previsão até o fim do mês")
            if (salaryCents == null || remaining == null) {
                Text("Gastos realizados (líquidos)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Amount(summary.spentCents, hidden, Modifier.testTag("salary-spent"), size = 32)
                summary.pendingExpensesCents?.takeIf { it != 0L }?.let {
                    SalaryLine("Despesas previstas pendentes", it, hidden)
                }
                FilledTonalButton(onSalary, Modifier.heightIn(min = FinanceSpacing.touch)) { Text("Informar salário líquido") }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (exceeded) "Gastos acima do salário" else "Sobra até agora",
                        style = MaterialTheme.typography.labelLarge, color = accent)
                    CompositionLocalProvider(LocalContentColor provides accent) {
                        Amount(if (exceeded) -remaining else remaining, hidden, Modifier.testTag("salary-remaining"), size = 32)
                    }
                }
                val percentage = summary.usedPercentage
                if (percentage != null && !hidden) {
                    // A barra limita o preenchimento a 100%, mas o texto mantém o percentual real.
                    LinearProgressIndicator(progress = { (percentage.toFloat() / 100f).coerceIn(0f, 1f) },
                        Modifier.fillMaxWidth().height(8.dp).clearAndSetSemantics { },
                        color = if (exceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh, gapSize = 0.dp, drawStopIndicator = {})
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SalaryLine(if (summary.isEstimatedSalary) "Salário líquido · referência" else "Salário líquido · registrado", salaryCents, hidden)
                    SalaryLine("Gastos realizados (líquidos)", summary.spentCents, hidden)
                    Text(if (hidden) "Salário utilizado: ••••" else "Salário utilizado: ${percentage?.toPlainString()?.replace('.', ',')}%",
                        style = MaterialTheme.typography.bodyMedium)
                }
                summary.projectedRemainingCents?.let { projected ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SalaryLine("Despesas previstas pendentes", summary.pendingExpensesCents ?: 0, hidden)
                        SalaryLine("Sobra estimada no fim do mês", projected, hidden, Modifier.testTag("salary-projected"))
                    }
                }
                if (summary.isEstimatedSalary) {
                    TextButton(onSalary, Modifier.heightIn(min = FinanceSpacing.touch)) { Text("Editar salário do mês") }
                }
            }
        }
    }
}

@Composable
private fun SalaryLine(label: String, cents: Long, hidden: Boolean, amountModifier: Modifier = Modifier) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Amount(cents, hidden, amountModifier.widthIn(max = 160.dp), size = 16)
    }
}

@Composable
internal fun SalaryMonthEditor(month: YearMonth, current: Long?, hidden: Boolean, busy: Boolean,
    onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    val amount = rememberTextFieldState(current?.let(MoneyInput::fromCents).orEmpty())
    var attempted by remember { mutableStateOf(false) }
    var invalid by remember { mutableStateOf(false) }
    val cents = if (invalid) null else MoneyInput.cents(amount.text.toString())
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { DialogSystemBars(); Text("Salário líquido do mês") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Valor líquido usado no resultado de $month.")
            PrivateValueGate(hidden, "Mostrar salário para edição") {
                MoneyAmountField(amount, attempted, !busy, label = "Salário líquido (R$)", tag = "salary-month-amount",
                    onInvalidInput = { invalid = it })
            }
            if (attempted && (cents == null || cents <= 0)) Text("Informe um valor maior que zero.", color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton({
            attempted = true
            cents?.takeIf { it > 0 }?.let(onSave)
        }, enabled = !busy) { Text("Salvar") } },
        dismissButton = { TextButton(onDismiss, enabled = !busy) { Text("Cancelar") } })
}
