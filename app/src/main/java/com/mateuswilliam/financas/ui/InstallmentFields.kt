package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.R
import com.mateuswilliam.financas.core.InstallmentSchedule
import com.mateuswilliam.financas.core.Money
import com.mateuswilliam.financas.core.PurchaseAmountBasis
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal val commitmentDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

internal data class PurchaseTerms(
    val due: LocalDate,
    val billingDay: Int = due.dayOfMonth,
    val basis: PurchaseAmountBasis = PurchaseAmountBasis.TOTAL,
    val count: String = "",
    val paid: String = "0",
) {
    companion object {
        val Saver = listSaver<PurchaseTerms, Any>(
            save = { listOf(it.due.toString(), it.billingDay, it.basis.name, it.count, it.paid) },
            restore = { PurchaseTerms(LocalDate.parse(it[0] as String), it[1] as Int,
                PurchaseAmountBasis.valueOf(it[2] as String), it[3] as String, it[4] as String) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InstallmentFields(
    terms: PurchaseTerms, onTerms: (PurchaseTerms) -> Unit,
    amount: TextFieldState, onInvalid: (Boolean) -> Unit, schedule: InstallmentSchedule?,
    hidden: Boolean, attempted: Boolean, busy: Boolean,
) {
    val byTotal = terms.basis == PurchaseAmountBasis.TOTAL
    val context = LocalContext.current
    val theme = if (MaterialTheme.colorScheme.background.luminance() < 0.5f)
        android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Calcular parcelas a partir de", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = byTotal, onClick = { onTerms(terms.copy(basis = PurchaseAmountBasis.TOTAL)) },
                enabled = !busy, label = { Text("Valor total") }, modifier = Modifier.testTag("installment-basis-total"))
            FilterChip(selected = !byTotal, onClick = { onTerms(terms.copy(basis = PurchaseAmountBasis.INSTALLMENT)) },
                enabled = !busy, label = { Text("Valor de cada parcela") }, modifier = Modifier.testTag("installment-basis-part"))
        }
        InstallmentCountField(terms.count, "Quantidade total de parcelas", "installment-count", busy) {
            onTerms(terms.copy(count = it))
        }
        InstallmentCountField(terms.paid, "Parcelas já pagas", "installments-paid", busy) {
            onTerms(terms.copy(paid = it))
        }
        PrivateValueGate(hidden, "Mostrar valores da compra para edição") {
            // A rejeição de texto e a seleção do cursor pertencem ao campo ativo.
            androidx.compose.runtime.key(terms.basis) {
                MoneyAmountField(amount, attempted, enabled = !busy,
                    label = if (byTotal) "Valor total da compra (R$)" else "Valor de cada parcela (R$)",
                    tag = if (byTotal) "purchase-total" else "purchase-installment", onInvalidInput = onInvalid)
            }
            if (schedule != null) {
                val smallest = schedule.pending.minOf { it.amountCents }
                val largest = schedule.pending.maxOf { it.amountCents }
                val installmentValue = if (smallest == largest) Money.format(smallest)
                    else "${Money.format(smallest)} a ${Money.format(largest)}"
                Text("Total da compra: ${Money.format(schedule.totalCents)}", Modifier.testTag("purchase-preview-total"))
                Text("Valor por parcela em aberto: $installmentValue", style = MaterialTheme.typography.bodyMedium)
                Text("Total em aberto: ${Money.format(schedule.remainingCents)}", style = MaterialTheme.typography.bodyMedium)
                if (schedule.totalCents % schedule.installmentCount != 0L) {
                    Text("Os centavos que sobram na divisão ficam nas primeiras parcelas da compra.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        OutlinedButton(
            onClick = {
                showFinanceDatePicker(context, theme, terms.due) { chosen ->
                    val day = terms.billingDay.takeIf { minOf(it, chosen.lengthOfMonth()) == chosen.dayOfMonth }
                        ?: chosen.dayOfMonth
                    onTerms(terms.copy(due = chosen, billingDay = day))
                }
            },
            enabled = !busy, modifier = Modifier.fillMaxWidth(),
        ) {
            AppIcon(R.drawable.ic_calendar)
            Spacer(Modifier.width(8.dp))
            Text("Próximo vencimento: ${terms.due.format(commitmentDateFormat)}")
        }
        val day = terms.due.dayOfMonth
        if (day == terms.due.lengthOfMonth() && day < 31) {
            ChoiceField("Dia habitual de vencimento", terms.billingDay, (day..31).toList(), { "Dia $it" }) {
                if (!busy) onTerms(terms.copy(billingDay = it))
            }
        }
        if (schedule != null) {
            val first = schedule.pending.first()
            val last = schedule.pending.last()
            val period = if (first == last) first.dueDate.format(commitmentDateFormat)
                else "${first.dueDate.format(commitmentDateFormat)} a ${last.dueDate.format(commitmentDateFormat)}"
            Text("${schedule.pending.size} em aberto · $period", style = MaterialTheme.typography.bodyMedium)
        }
        Text("Confira o próximo vencimento. Só as parcelas em aberto serão planejadas; nenhum lançamento será criado agora.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InstallmentCountField(value: String, label: String, tag: String, busy: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = {
        if (it.length <= 3 && it.all { digit -> digit in '0'..'9' }) onChange(it)
    }, label = { Text(label) }, singleLine = true, enabled = !busy,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth().testTag(tag))
}
