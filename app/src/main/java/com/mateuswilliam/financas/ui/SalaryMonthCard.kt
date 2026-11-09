package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.core.*
import java.time.YearMonth

@Composable
internal fun SalaryMonthCard(summary: SalarySummary, hidden: Boolean, onSalary: () -> Unit) {
    val salaryCents = summary.salaryCents
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.fillMaxWidth().testTag("salary-month-summary").padding(FinanceSpacing.card),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("Meu salário no mês", "Gastos realizados e sobra estimada")
            if (salaryCents == null) {
                Text("Informe o salário líquido do mês para calcular a sobra e o percentual utilizado.")
            } else {
                SalaryMetric(if (summary.isEstimatedSalary) "Salário do mês · referência" else "Salário do mês · registrado",
                    salaryCents, hidden)
                summary.usedPercentage?.let {
                    Text(if (hidden) "Salário utilizado: ••••" else "Salário utilizado: ${it.toPlainString().replace('.', ',')}%")
                }
            }
            SalaryMetric("Gastos realizados (líquidos)", summary.spentCents, hidden)
            summary.remainingCents?.let { SalaryMetric(if (it < 0) "Gastos acima do salário" else "Sobra até agora", if (it < 0) -it else it, hidden) }
            summary.projectedRemainingCents?.let { SalaryMetric(
                if (it < 0) "Falta prevista no fim do mês · estimativa" else "Sobra prevista no fim do mês · estimativa",
                if (it < 0) -it else it, hidden) }
            TextButton(onSalary, Modifier.heightIn(min = 48.dp)) { Text("Informar salário líquido") }
        }
    }
}

@Composable
private fun SalaryMetric(label: String, cents: Long, hidden: Boolean) {
    Text(label, style = MaterialTheme.typography.bodyMedium)
    Amount(cents, hidden)
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
            Text("Referência de $month. Se houver salário já registrado neste mês, o lançamento será usado no resumo.")
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
