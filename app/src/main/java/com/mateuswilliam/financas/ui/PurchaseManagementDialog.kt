package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.FinanceViewModel
import com.mateuswilliam.financas.core.*
import com.mateuswilliam.financas.data.PurchaseManagement
import java.time.LocalDate
import kotlinx.coroutines.CancellationException

internal enum class PurchaseManagementAction { EDIT, EXCLUDE, CANCEL_REMAINING }

@Composable
internal fun PurchaseManagementDialog(
    model: FinanceViewModel, installmentId: String, action: PurchaseManagementAction, busy: Boolean,
    hidden: Boolean, accounts: List<AccountOption>, onDismiss: () -> Unit,
) {
    val result by produceState<Result<PurchaseManagement?>?>(null, installmentId) {
        value = try {
            Result.success(model.purchaseManagement(installmentId))
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }
    val current = result?.getOrNull()
    if (current == null) {
        AlertDialog(onDismissRequest = onDismiss, title = { DialogSystemBars(); Text("Compra parcelada") },
            text = {
                Text(when {
                    result == null -> "Carregando as parcelas…"
                    result?.isFailure == true -> "Não foi possível carregar a compra. Tente novamente."
                    else -> "Esta parcela mudou ou saiu das movimentações. Confira a lista atual."
                })
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Voltar") } })
        return
    }

    key(current.installmentId, current.revision) {
        when {
            action == PurchaseManagementAction.CANCEL_REMAINING -> CancelOpenInstallmentsDialog(current, busy, hidden, onDismiss) {
                model.cancelOpenInstallments(current, onDismiss)
            }
            action == PurchaseManagementAction.EXCLUDE -> ExcludeInstallmentDialog(current, busy, hidden, onDismiss) {
                model.excludeInstallmentForecast(current, onDismiss)
            }
            current.canReplacePurchase -> EditPurchaseDialog(current.purchase, busy, hidden, accounts, model::addAccount, onDismiss) {
                model.editPurchase(current, it, onDismiss)
            }
            else -> EditOpenInstallmentDialog(current, busy, hidden, onDismiss) {
                model.adjustInstallment(current, it, onDismiss)
            }
        }
    }
}

@Composable
private fun ExcludeInstallmentDialog(
    current: PurchaseManagement, busy: Boolean, hidden: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit,
) {
    val part = current.installment
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { DialogSystemBars(); Text("Excluir esta parcela prevista?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(privateDisplayText(current.purchase.title, hidden), style = MaterialTheme.typography.titleMedium)
                Text("Parcela ${part.number} de ${current.purchase.installmentCount} · ${part.dueDate.format(commitmentDateFormat)}")
                Text("Só esta parcela sai das movimentações. As demais parcelas e os lançamentos já registrados serão mantidos.")
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) { Text("Excluir parcela", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.testTag("keep-installment-forecast")) { Text("Voltar") }
        })
}

@Composable
private fun EditPurchaseDialog(
    purchase: InstallmentPurchase, busy: Boolean, hidden: Boolean, accounts: List<AccountOption>,
    onAddAccount: suspend (String) -> String, onDismiss: () -> Unit, onSave: (InstallmentPurchase) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(purchase.title) }
    var bank by rememberSaveable { mutableStateOf(purchase.bank) }
    var category by rememberSaveable { mutableStateOf(purchase.category) }
    var terms by rememberSaveable(stateSaver = PurchaseTerms.Saver) {
        mutableStateOf(PurchaseTerms(purchase.nextDueDate, purchase.dayOfMonth, count = purchase.installmentCount.toString(),
            paid = purchase.paidInstallments.toString()))
    }
    val total = rememberTextFieldState(initialText = MoneyInput.fromCents(purchase.totalCents))
    val perInstallment = rememberTextFieldState()
    var invalidTotal by rememberSaveable { mutableStateOf(false) }
    var invalidPart by rememberSaveable { mutableStateOf(false) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val byTotal = terms.basis == PurchaseAmountBasis.TOTAL
    val amount = if (byTotal) total else perInstallment
    val invalid = if (byTotal) invalidTotal else invalidPart
    val cents = if (invalid) null else MoneyInput.cents(amount.text.toString())
    val preview = remember(cents, terms) {
        runCatching { CommitmentForms.schedule(cents, terms.basis, terms.count.toIntOrNull(), terms.paid.toIntOrNull(), terms.due, terms.billingDay) }
    }

    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { DialogSystemBars(); Text("Editar compra parcelada") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("A correção recalcula as previsões desta compra. Nenhum lançamento será criado.")
                PrivateValueGate(hidden && privateDisplayText(title, true) != title, "Mostrar descrição para edição") {
                    OutlinedTextField(title, { title = it.take(200); error = null }, label = { Text("Descrição da compra") },
                        modifier = Modifier.fillMaxWidth().testTag("purchase-edit-title"), singleLine = true, enabled = !busy)
                }
                AccountPickerField("Conta de origem", bank, accounts, { bank = it; error = null }, onAddAccount, hidden = hidden)
                ChoiceField("Categoria", category, Category.entries.toList(), { it.label }) { if (!busy) category = it }
                InstallmentFields(terms, { terms = it; error = null }, amount,
                    { if (byTotal) invalidTotal = it else invalidPart = it; error = null },
                    preview.getOrNull(), hidden, attempted, busy)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            FilledTonalButton(enabled = !busy, onClick = {
                attempted = true
                val result = runCatching {
                    val schedule = preview.getOrThrow()
                    purchase.copy(title = title.trim(), bank = bank.trim(), category = category, totalCents = schedule.totalCents,
                        installmentCount = schedule.installmentCount, paidInstallments = schedule.paidInstallments,
                        nextDueDate = terms.due, dayOfMonth = schedule.dayOfMonth).also { CommitmentPlanning.schedule(it) }
                }
                error = result.exceptionOrNull()?.message
                result.getOrNull()?.let(onSave)
            }) { Text("Salvar alterações da compra") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Voltar") } })
}

@Composable
private fun EditOpenInstallmentDialog(
    current: PurchaseManagement, busy: Boolean, hidden: Boolean,
    onDismiss: () -> Unit, onSave: (InstallmentAdjustment) -> Unit,
) {
    val part = current.installment
    val amount = rememberTextFieldState(initialText = MoneyInput.fromCents(part.amountCents))
    var date by rememberSaveable { mutableStateOf(part.dueDate.toString()) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val due = LocalDate.parse(date)
    val cents = if (invalid) null else MoneyInput.cents(amount.text.toString())
    val result = remember(cents, due) {
        runCatching { InstallmentAdjustment(requireNotNull(cents) { "Informe um valor válido para a parcela." }, due) }
    }
    val context = LocalContext.current
    val theme = if (MaterialTheme.colorScheme.background.luminance() < 0.5f)
        android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert

    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { DialogSystemBars(); Text("Editar parcela em aberto") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(privateDisplayText(current.purchase.title, hidden), style = MaterialTheme.typography.titleMedium)
                Text("Parcela ${part.number} de ${current.purchase.installmentCount}. Os lançamentos confirmados e as demais parcelas mantêm seus dados.")
                PrivateValueGate(hidden, "Mostrar valor da parcela para edição") {
                    MoneyAmountField(amount, attempted, enabled = !busy, label = "Valor da parcela (R$)",
                        tag = "adjust-installment-amount", onInvalidInput = { invalid = it })
                }
                OutlinedButton(onClick = { showFinanceDatePicker(context, theme, due) { date = it.toString() } },
                    enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Vencimento: ${due.format(commitmentDateFormat)}") }
                if (attempted) result.exceptionOrNull()?.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = { attempted = true; result.getOrNull()?.let(onSave) }, enabled = !busy) { Text("Salvar parcela") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Voltar") } })
}

@Composable
private fun CancelOpenInstallmentsDialog(
    current: PurchaseManagement, busy: Boolean, hidden: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit,
) {
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { DialogSystemBars(); Text("Cancelar parcelas em aberto?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(privateDisplayText(current.purchase.title, hidden), style = MaterialTheme.typography.titleMedium)
                Text(if (current.openCount == 1) "Uma parcela sairá das movimentações."
                    else "${current.openCount} parcelas sairão das movimentações, incluindo as de outros meses.")
                Text("Os lançamentos confirmados ficam no extrato. O cancelamento vale apenas para o Planejamento deste app.")
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !busy) { Text("Cancelar parcelas", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.testTag("keep-open-installments")) { Text("Voltar") } })
}
