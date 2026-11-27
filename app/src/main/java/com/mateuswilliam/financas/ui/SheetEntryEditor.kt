package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.core.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

internal enum class SheetEntryKind(val title: String, val movement: Movement?) {
    EXPENSE("Despesa", Movement.EXPENSE),
    INCOME("Receita", Movement.INCOME),
    TRANSFER("Transferência entre minhas contas", Movement.TRANSFER),
    OPENING("Saldo inicial", null),
    REFUND("Estorno", Movement.REFUND);

    companion object {
        fun of(movement: Movement) = entries.firstOrNull { it.movement == movement } ?: EXPENSE
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SheetEntryEditor(
    initialKind: SheetEntryKind, existing: Record?, reference: AccountBalanceReference?,
    historicalAccounts: List<AccountOption>, accounts: List<AccountOption>, today: LocalDate, zone: ZoneId,
    lastAccount: String, toReserve: Boolean, hidden: Boolean, busy: Boolean, writeError: String?,
    onDismiss: () -> Unit, onAddAccount: suspend (String) -> String, onRecord: (Record) -> Unit,
    onBalance: (String, Long, Long, String) -> Unit, onDelete: (String) -> Unit,
) {
    val defaultAccount = accounts.firstOrNull { it.key == AccountNames.key(lastAccount) && it.key != AccountFunds.RESERVE_KEY }
        ?: accounts.firstOrNull { it.key != AccountFunds.RESERVE_KEY }
    val accountFromReference = reference?.let { ref -> historicalAccounts.firstOrNull { it.key == ref.accountKey }?.label }
    var kind by rememberSaveable { mutableStateOf(initialKind) }
    val newEntry = existing == null && reference == null
    val id = rememberSaveable { existing?.id ?: UUID.randomUUID().toString() }
    var bank by rememberSaveable {
        mutableStateOf(existing?.bank ?: accountFromReference ?: defaultAccount?.label ?:
            if (initialKind == SheetEntryKind.TRANSFER) "" else "Carteira")
    }
    var destination by rememberSaveable {
        mutableStateOf(existing?.destination.orEmpty().ifBlank { if (toReserve) AccountFunds.RESERVE_NAME else "" })
    }
    var category by rememberSaveable {
        mutableStateOf(existing?.category ?: if (initialKind == SheetEntryKind.INCOME) Category.SALARY else Category.OTHER)
    }
    var description by rememberSaveable { mutableStateOf(existing?.description ?: reference?.reason.orEmpty()) }
    val originalTime = (existing?.occurredAt ?: reference?.effectiveAt)?.let { Instant.ofEpochMilli(it).atZone(zone) }
    var dateText by rememberSaveable { mutableStateOf((originalTime?.toLocalDate() ?: today).toString()) }
    val amount = rememberTextFieldState(initialText = (existing?.amountCents ?: reference?.balanceCents)?.let {
        MoneyInput.fromCents(kotlin.math.abs(it))
    }.orEmpty())
    var negative by rememberSaveable { mutableStateOf((reference?.balanceCents ?: 0L) < 0) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var invalidAmount by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val opening = kind == SheetEntryKind.OPENING
    val transfer = kind == SheetEntryKind.TRANSFER
    val date = LocalDate.parse(dateText)
    val cents = MoneyInput.cents(amount.text.toString())?.takeUnless { invalidAmount }
    val accountText = AccountNames.clean(bank)
    val destinationText = AccountNames.clean(destination)
    val memo = description.trim().ifEmpty {
        when {
            opening -> "Saldo inicial"
            transfer -> "Transferência entre minhas contas"
            else -> ""
        }
    }
    fun candidate(at: Long) = Record(id, cents ?: 0L, kind.movement ?: Movement.UNKNOWN,
        existing?.method ?: PaymentMethod.UNKNOWN, if (transfer) Category.OTHER else category, accountText, memo, at,
        destination = if (transfer) destinationText else "", originalRecordId = existing?.originalRecordId,
        reversalMovement = existing?.reversalMovement ?: Movement.EXPENSE, forecastItemId = existing?.forecastItemId,
        forecastOccurrenceKey = existing?.forecastOccurrenceKey)

    val error = when {
        cents == null || (!opening && cents == 0L) -> if (opening) "Informe um saldo, inclusive zero." else "Informe um valor maior que zero."
        date > today && date != originalTime?.toLocalDate() -> "Escolha uma data até hoje."
        accountText.isBlank() -> "Escolha uma conta."
        opening && AccountFunds.isReserve(accountText) && negative -> "A reserva não pode ter saldo inicial negativo."
        transfer && destinationText.isBlank() -> "Escolha a conta de destino."
        transfer && AccountNames.key(accountText) == AccountNames.key(destinationText) -> "Escolha duas contas diferentes."
        !opening -> RecordValidation.error(candidate(date.atStartOfDay(zone).toInstant().toEpochMilli()))
        else -> null
    }
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val calendarTheme = if (MaterialTheme.colorScheme.background.luminance() < 0.5f)
        android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert
    val currentAccountOptions = if (newEntry) accounts else (accounts + historicalAccounts.filter {
        it.key == AccountNames.key(bank) || it.key == AccountNames.key(destination)
    }).distinctBy { it.key }

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        DialogSystemBars()
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(if (existing != null) "Editar lançamento" else if (reference != null) "Corrigir saldo" else "Novo lançamento")
            if (newEntry) ChoiceField("Tipo de lançamento", kind, SheetEntryKind.entries.filterNot { it == SheetEntryKind.REFUND }, { it.title }) {
                if (!busy && it != kind) {
                    kind = it
                    category = if (it == SheetEntryKind.INCOME) Category.SALARY else Category.OTHER
                    if (it != SheetEntryKind.OPENING) negative = false
                    if (it == SheetEntryKind.TRANSFER && accounts.none { option -> option.key == AccountNames.key(bank) }) bank = ""
                    if (it != SheetEntryKind.TRANSFER && bank.isBlank()) bank = defaultAccount?.label ?: "Carteira"
                }
            } else Text(kind.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PrivateValueGate(hidden, "Mostrar valor para edição") {
                MoneyAmountField(amount, attempted, !busy, label = if (opening) "Saldo (R$)" else "Valor (R$)",
                    allowZero = opening) { invalidAmount = it }
            }
            if (opening && !AccountFunds.isReserve(bank)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(negative, enabled = !busy,
                    role = Role.Checkbox, onValueChange = { negative = it }), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(negative, onCheckedChange = null, enabled = !busy)
                    Text("Saldo negativo")
                }
            }
            if (!transfer && !opening && existing?.originalRecordId == null) {
                val allowed = (categoriesFor(kind.movement ?: Movement.EXPENSE, existing?.reversalMovement ?: Movement.EXPENSE) + category).distinct()
                ChoiceField("Categoria", category, allowed, { it.label }) { if (!busy) category = it }
            }
            OutlinedButton(onClick = {
                keyboard?.hide()
                showFinanceDatePicker(context, calendarTheme, date) { dateText = it.toString() }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("sheet-entry-date")) {
                Text("Data · ${date.format(financeDateFormat)}")
            }
            PrivateValueGate(hidden && privateDisplayText(description, true) != description, "Mostrar descrição para edição") {
                OutlinedTextField(description, { description = it.take(200) },
                    label = { Text(if (opening || transfer) "Descrição opcional" else "Descrição") }, singleLine = true,
                    enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("sheet-description"),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
            }
            if (reference == null && existing?.originalRecordId == null) {
                AccountPickerField(if (transfer) "Origem" else "Conta", bank, currentAccountOptions, {
                    bank = it
                    if (AccountFunds.isReserve(it)) negative = false
                    if (transfer && AccountNames.key(it) == AccountNames.key(destination)) destination = ""
                }, onAddAccount, hidden)
            } else Text(privateDisplayText(bank, hidden), style = MaterialTheme.typography.bodyMedium)
            if (transfer) AccountPickerField("Destino", destination,
                currentAccountOptions.filterNot { it.key == AccountNames.key(bank) }, { destination = it }, onAddAccount, hidden)
            if (opening) Text("Informe o saldo no início da data. Movimentações anteriores já incluídas nesse valor ficam no histórico.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (attempted && error != null) Text(error, color = MaterialTheme.colorScheme.error)
            if (attempted && !busy && writeError != null) Text(writeError, color = MaterialTheme.colorScheme.error)
            Button(onClick = {
                attempted = true
                if (cents != null && error == null) {
                    keyboard?.hide()
                    val startOfDay = date.atStartOfDay(zone).toInstant().toEpochMilli()
                    if (opening) onBalance(accountText, if (negative) -cents else cents, startOfDay, memo)
                    else {
                        val at = when {
                            originalTime?.toLocalDate() == date -> originalTime.toInstant().toEpochMilli()
                            date == today -> System.currentTimeMillis()
                            else -> startOfDay
                        }
                        onRecord(candidate(at))
                    }
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("sheet-entry-save")) {
                Text(if (busy) "Salvando…" else if (opening) "Salvar saldo" else "Salvar lançamento")
            }
            if (existing != null) TextButton({ deleting = true }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Excluir lançamento", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (deleting && existing != null) AlertDialog(onDismissRequest = { deleting = false },
        title = { DialogSystemBars(); Text("Excluir lançamento?") },
        text = { Text("Os saldos e o resultado do período serão recalculados.") },
        confirmButton = {
            TextButton({
                deleting = false
                attempted = true
                onDelete(existing.id)
            }, enabled = !busy) { Text("Excluir") }
        },
        dismissButton = { TextButton({ deleting = false }) { Text("Cancelar") } })
}
