package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.core.CommitmentForms
import com.mateuswilliam.financas.core.MoneyInput
import com.mateuswilliam.financas.core.MonthlyIncomePlan
import com.mateuswilliam.financas.data.InstallmentDue
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

internal val commitmentMonthFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/yyyy")

@Composable
internal fun MonthlyIncomeEditor(
    month: YearMonth, existing: MonthlyIncomePlan?, busy: Boolean, hidden: Boolean,
    onDismiss: () -> Unit, onSave: (MonthlyIncomePlan) -> Unit,
) {
    val amount = rememberTextFieldState(initialText = existing?.incomeCents?.let(MoneyInput::fromCents).orEmpty())
    val defaultLimit = existing?.healthyLimitBasisPoints ?: MonthlyIncomePlan(month, 0).healthyLimitBasisPoints
    var limit by rememberSaveable { mutableStateOf(CommitmentForms.formatPercent(defaultLimit)) }
    var invalidMoney by rememberSaveable { mutableStateOf(false) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val cents = if (invalidMoney) null else MoneyInput.cents(amount.text.toString())
    val result = remember(month, cents, limit) { runCatching { CommitmentForms.monthlyPlan(month, cents, limit) } }

    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { DialogSystemBars(); Text("Renda e limite · ${month.format(commitmentMonthFormat)}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Informe o valor que espera receber após os descontos. Ele será usado no planejamento, sem criar um lançamento de receita.")
                PrivateValueGate(hidden, "Mostrar renda e limite para edição") {
                    MoneyAmountField(amount, attempted, enabled = !busy, label = "Renda líquida prevista (R$)",
                        tag = "monthly-income", allowZero = true, onInvalidInput = { invalidMoney = it })
                    OutlinedTextField(limit, { if (it.length <= 16) limit = it }, label = { Text("Limite de comprometimento (%)") },
                        modifier = Modifier.fillMaxWidth().testTag("monthly-commitment-limit"), singleLine = true,
                        enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done))
                }
                Text("30% é uma referência inicial. Ajuste o limite à sua realidade.", style = MaterialTheme.typography.bodySmall)
                if (attempted) result.exceptionOrNull()?.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = { attempted = true; result.getOrNull()?.let(onSave) }, enabled = !busy) {
                Text("Salvar renda e limite")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InstallmentDueCard(
    item: InstallmentDue, busy: Boolean, hidden: Boolean,
    onConfirm: () -> Unit, onEdit: () -> Unit, onExclude: () -> Unit, onCancel: () -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth().testTag("installment-${item.id}")) {
        Column(Modifier.padding(FinanceSpacing.card), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(privateDisplayText(item.title, hidden), style = MaterialTheme.typography.titleMedium)
                    Text("Parcela ${item.number} de ${item.installmentCount} · ${LocalDate.parse(item.dueDate).format(commitmentDateFormat)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(privateDisplayText(item.bank, hidden), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (item.recordId == null) ContextualActions(privateDisplayText(item.title, hidden), "actions-${item.id}", !busy,
                    listOf(
                        FinanceAction("Editar", onEdit, "edit-${item.id}"),
                        FinanceAction("Excluir só esta parcela", onExclude, "exclude-${item.id}", destructive = true),
                        FinanceAction("Cancelar parcelas em aberto", onCancel, "cancel-${item.id}", destructive = true),
                    ))
            }
            Amount(item.amountCents, hidden, size = 18)
            if (item.recordId == null) {
                OutlinedButton(onClick = onConfirm, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = FinanceSpacing.touch)
                        .testTag("confirm-installment-${item.id}")) { Text("Conferir e registrar parcela") }
            } else Text("Registrada no extrato", style = MaterialTheme.typography.labelMedium)
        }
    }
}
