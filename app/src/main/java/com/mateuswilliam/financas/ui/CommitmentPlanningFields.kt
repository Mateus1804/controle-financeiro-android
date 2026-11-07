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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.core.CommitmentForms
import com.mateuswilliam.financas.core.Money
import com.mateuswilliam.financas.core.MoneyInput
import com.mateuswilliam.financas.core.MonthlyIncomePlan
import com.mateuswilliam.financas.data.InstallmentDue
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

internal val commitmentMonthFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/yyyy")

@Composable
internal fun MonthlyIncomeCard(plan: MonthlyIncomePlan?, loaded: Boolean, busy: Boolean, hidden: Boolean, onEdit: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.fillMaxWidth().padding(FinanceSpacing.section), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Renda prevista e limite", style = MaterialTheme.typography.titleMedium)
            when {
                !loaded -> Text("Carregando a configuração do mês…")
                plan == null -> Text("Renda ainda não informada")
                hidden -> Text("Renda e limite ocultos")
                else -> {
                    Text("Renda líquida prevista: ${Money.format(plan.incomeCents)}")
                    Text("Limite de comprometimento: ${CommitmentForms.formatPercent(plan.healthyLimitBasisPoints)}%")
                }
            }
            OutlinedButton(onClick = onEdit, enabled = loaded && !busy, modifier = Modifier.fillMaxWidth().testTag("edit-monthly-income")) {
                Text(if (plan == null) "Informar renda e limite" else "Editar renda e limite")
            }
        }
    }
}

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

@Composable
internal fun InstallmentDueCard(item: InstallmentDue, busy: Boolean, hidden: Boolean, onConfirm: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth().testTag("installment-${item.id}")) {
        Column(Modifier.padding(FinanceSpacing.section), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(privateDisplayText(item.title, hidden), style = MaterialTheme.typography.titleMedium)
            Text("Parcela ${item.number} de ${item.installmentCount} · ${LocalDate.parse(item.dueDate).format(commitmentDateFormat)}",
                style = MaterialTheme.typography.bodyMedium)
            Text(privateDisplayText(item.bank, hidden), style = MaterialTheme.typography.bodySmall)
            Amount(item.amountCents, hidden)
            if (item.recordId == null) {
                OutlinedButton(onClick = onConfirm, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("confirm-installment-${item.id}")) { Text("Conferir e registrar parcela") }
            } else Text("Registrada no extrato", style = MaterialTheme.typography.labelMedium)
        }
    }
}
