package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.core.*
import com.mateuswilliam.financas.data.DraftEntity
import com.mateuswilliam.financas.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private enum class MovementFrequency(val label: String) { SINGLE("Única"), RECURRING("Recorrente"), INSTALLMENTS("Parcelada") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordEditor(
    draft: DraftEntity?,
    existing: Record?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (Record) -> Unit,
    onIgnore: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    accounts: List<AccountOption>,
    lastOrigin: String,
    onAddAccount: suspend (String) -> String,
    preset: Record? = null, initialMovement: Movement = Movement.EXPENSE,
    duplicateIndex: DuplicateCandidates? = null,
    hideAmounts: Boolean = false,
    reviewProgress: String? = null,
    newPendingCount: Int = 0,
    notificationChanged: Boolean = false,
    onAcceptUpdate: () -> Unit = {},
    onSavePurchase: ((InstallmentPurchase) -> Unit)? = null,
    installmentConfirmation: Boolean = false,
    recurring: RecurringPlan? = null,
    onSaveRecurring: ((RecurringPlan, Boolean) -> Unit)? = null,
    recurrenceScope: java.time.YearMonth? = null,
    recurrenceOnlyMonth: Boolean = false,
    recurringFixed: Boolean = true,
    initialDate: LocalDate? = null,
    balances: List<AccountBalance> = emptyList(),
    history: List<Record> = emptyList(),
    initialDescription: String = "",
    initialCategory: Category? = null,
    initialRecurring: Boolean = false,
) {
    val initial = existing ?: preset ?: recurring?.proposedRecord(recurring.id, ZoneId.systemDefault())
    val originalAt = rememberSaveable { initial?.occurredAt ?: draft?.occurredAt ?: initialDate?.atTime(java.time.LocalTime.now())?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: System.currentTimeMillis() }
    val originalDate = Instant.ofEpochMilli(originalAt).atZone(ZoneId.systemDefault())
    val id = rememberSaveable { initial?.id ?: UUID.randomUUID().toString() }
    val amount = rememberTextFieldState(initialText = (initial?.amountCents ?: draft?.amountCents)?.let(MoneyInput::fromCents).orEmpty())
    var invalidMoneyInput by rememberSaveable { mutableStateOf(false) }
    val installmentAmount = rememberTextFieldState()
    var invalidInstallmentAmount by rememberSaveable { mutableStateOf(false) }
    var frequency by rememberSaveable { mutableStateOf(if (recurring != null || initialRecurring) MovementFrequency.RECURRING else MovementFrequency.SINGLE) }
    var fixedExpense by rememberSaveable { mutableStateOf(recurringFixed) }
    var terms by rememberSaveable(stateSaver = PurchaseTerms.Saver) { mutableStateOf(PurchaseTerms(LocalDate.now())) }
    val activeAccounts = accounts.filter { account -> balances.none { it.account.key == account.key && it.archived } }
    val defaultBank = lastOrigin.takeIf { name -> name.isNotBlank() && balances.none { it.archived && it.account.key == AccountNames.key(name) } }
        ?: activeAccounts.firstOrNull()?.label.orEmpty()
    var bank by rememberSaveable { mutableStateOf(initial?.bank ?: draft?.bank ?: defaultBank) }
    var description by rememberSaveable { mutableStateOf(initial?.description ?: draft?.sourceBody?.take(200) ?: initialDescription) }
    var movement by rememberSaveable { mutableStateOf(initial?.movement ?: draft?.movement ?: initialMovement) }
    var method by rememberSaveable { mutableStateOf(initial?.method ?: draft?.method ?: PaymentMethod.UNKNOWN) }
    var category by rememberSaveable { mutableStateOf(initial?.category ?: draft?.let { source -> source.category.takeIf { it in categoriesFor(source.movement) } }
        ?: initialCategory ?: if ((draft?.movement ?: initialMovement) == Movement.INCOME) Category.OTHER_INCOME else Category.OTHER) }
    var categoryEdited by rememberSaveable { mutableStateOf(false) }
    var accountEdited by rememberSaveable { mutableStateOf(false) }
    var originalRecordId by rememberSaveable { mutableStateOf(initial?.originalRecordId) }
    var reversalMovement by rememberSaveable { mutableStateOf(initial?.reversalMovement ?: Movement.EXPENSE) }
    var destination by rememberSaveable { mutableStateOf(initial?.destination.orEmpty()) }
    var date by rememberSaveable { mutableStateOf(originalDate.toLocalDate().toString()) }
    var recurringDay by rememberSaveable { mutableIntStateOf(recurring?.dayOfMonth ?: LocalDate.parse(date).dayOfMonth) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val dateDialogTheme = if (MaterialTheme.colorScheme.background.luminance() < 0.5f)
        android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    // Em fontes ampliadas, o cabeçalho acompanha a rolagem para não consumir a área do teclado.
    val compactHeight = windowHeight < 480.dp || LocalDensity.current.fontScale >= 1.5f
    val maximumHeight = windowHeight * if (compactHeight) 0.96f else 0.84f
    val compactScroll = rememberScrollState()
    val fieldsScroll = rememberScrollState()
    val cents = if (invalidMoneyInput) null else MoneyInput.cents(amount.text.toString())
    val canInstall = initial == null && onSavePurchase != null && movement == Movement.EXPENSE
    val purchaseMode = canInstall && frequency == MovementFrequency.INSTALLMENTS
    val recurringMode = frequency == MovementFrequency.RECURRING && movement in setOf(Movement.INCOME, Movement.EXPENSE)
    val canRecur = onSaveRecurring != null && existing == null && preset == null && draft == null
    val byTotal = terms.basis == PurchaseAmountBasis.TOTAL
    val purchaseCents = if (byTotal) cents else if (invalidInstallmentAmount) null else MoneyInput.cents(installmentAmount.text.toString())
    val scheduleResult = if (purchaseMode) remember(purchaseCents, terms) {
        runCatching { CommitmentForms.schedule(purchaseCents, terms.basis, terms.count.toIntOrNull(), terms.paid.toIntOrNull(), terms.due, terms.billingDay) }
    } else null
    val schedule = scheduleResult?.getOrNull()
    val record = Record(id, (if (purchaseMode) schedule?.totalCents else cents) ?: 0, movement,
        if (purchaseMode) PaymentMethod.CARD else method, category, bank, description,
        LocalDate.parse(date).atTime(originalDate.toLocalTime()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        if (movement == Movement.TRANSFER) destination else "",
        originalRecordId = if (movement == Movement.REFUND) originalRecordId else null, reversalMovement = reversalMovement)
    val recurringValue = RecurringPlan(id, description.trim(), cents ?: 0, movement, method, category,
        bank, date, recurringDay, recurring?.active ?: true)
    val error = when {
        recurringMode && recurrenceScope != null && java.time.YearMonth.from(LocalDate.parse(date)) != recurrenceScope -> "Escolha um vencimento dentro desta competência."
        recurringMode -> Recurrences.error(recurringValue)
        else -> scheduleResult?.exceptionOrNull()?.message ?: RecordValidation.error(record)
    }
    val duplicates = remember(duplicateIndex, record, draft?.id) { if (draft != null) duplicateIndex?.find(record).orEmpty() else emptyList() }

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        DialogSystemBars()
        val scroll = if (compactHeight) Modifier.verticalScroll(compactScroll) else Modifier
        Column(Modifier.fillMaxWidth().heightIn(max = maximumHeight).imePadding().then(scroll)) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(when {
                    recurring != null -> if (recurrenceOnlyMonth) "Editar só este mês" else "Editar desta e futuras"
                    draft != null -> "Conferir movimentação"
                    installmentConfirmation -> "Conferir parcela"
                    preset != null -> "Conferir previsão"
                    existing != null -> "Editar lançamento"
                    else -> "Novo lançamento"
                }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(when {
                    draft != null && purchaseMode -> "Confira os dados da notificação e das parcelas antes de salvar."
                    draft != null -> "A notificação só vira lançamento após revisão e confirmação."
                    preset != null -> "Confira valor, conta e data efetiva antes de transformar a previsão em lançamento."
                    else -> "Valores, contas e categorias podem ser ajustados antes de salvar."
                },
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                reviewProgress?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
            }
            val fields = if (compactHeight) Modifier else Modifier.weight(1f, fill = false).verticalScroll(fieldsScroll)
            Column(fields.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (draft != null) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${privateDisplayText(draft.bank, hideAmounts)} · ${shortDate(draft.occurredAt)}", style = MaterialTheme.typography.labelMedium)
                            Text(privateDisplayText(draft.sourceTitle, hideAmounts), style = MaterialTheme.typography.titleSmall)
                            Text(privateDisplayText(draft.sourceBody, hideAmounts), style = MaterialTheme.typography.bodySmall)
                            Text(privateDisplayText(draft.explanation, hideAmounts), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (newPendingCount > 0) Text("$newPendingCount nova(s) pendência(s) disponível(is). Os campos desta revisão foram mantidos.", style = MaterialTheme.typography.bodySmall)
                if (notificationChanged) {
                    Text("A notificação foi atualizada. O texto de origem acima está atualizado; os campos editados foram preservados. Confira a atualização antes de confirmar.", color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = onAcceptUpdate, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Usar esta versão e manter a edição") }
                }
                if (duplicates.isNotEmpty()) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Possível duplicidade", fontWeight = FontWeight.SemiBold)
                            Text("Há lançamentos manuais semelhantes. A sugestão não confirma duplicidade e não une ou remove registros.", style = MaterialTheme.typography.bodySmall)
                            duplicates.forEach { candidate ->
                                Text(privateDisplayText(candidate.record.description, hideAmounts), style = MaterialTheme.typography.titleSmall)
                                Text("${privateDisplayText(candidate.record.bank, hideAmounts)} · ${shortDate(candidate.record.occurredAt)}", style = MaterialTheme.typography.bodySmall)
                                Amount(candidate.record.amountCents, hideAmounts, size = 16)
                                Text(candidate.reasons.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                            }
                            if (onIgnore != null) OutlinedButton(onClick = onIgnore, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Ignorar sugestão já registrada") }
                        }
                    }
                }
                if (recurring == null && initial == null && (canInstall || canRecur) && movement in setOf(Movement.INCOME, Movement.EXPENSE)) {
                    val frequencies = buildList {
                        add(MovementFrequency.SINGLE)
                        if (canRecur) add(MovementFrequency.RECURRING)
                        if (canInstall) add(MovementFrequency.INSTALLMENTS)
                    }
                    ChoiceField("Frequência", frequency, frequencies, { it.label }) { if (!busy) frequency = it }
                }
                if (recurringMode) {
                    Text(if (recurrenceOnlyMonth) "Só esta competência será alterada; os outros meses serão preservados." else if (recurring != null) "Desta competência em diante; pagamentos realizados e meses anteriores preservados." else "Mensal · o valor pode ser ajustado ou ignorado em cada mês.", style = MaterialTheme.typography.bodySmall)
                    if (!recurrenceOnlyMonth && movement == Movement.EXPENSE) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Despesa fixa", Modifier.weight(1f))
                        Switch(fixedExpense, { fixedExpense = it }, enabled = !busy, modifier = Modifier.semantics { contentDescription = "Despesa fixa" })
                    }
                }
                if (purchaseMode) {
                    InstallmentFields(terms, { terms = it }, if (byTotal) amount else installmentAmount,
                        { if (byTotal) invalidMoneyInput = it else invalidInstallmentAmount = it },
                        schedule, hideAmounts, attempted, busy)
                } else PrivateValueGate(hideAmounts, "Mostrar valor para edição") {
                    MoneyAmountField(amount, attempted, enabled = !busy, onInvalidInput = { invalidMoneyInput = it })
                }
                if (recurring == null && preset == null && !installmentConfirmation) ChoiceField("Movimentação", movement,
                    Movement.entries.toList(), { it.label }) {
                    if (movement != it) {
                        category = if (it == Movement.INCOME) Category.OTHER_INCOME else Category.OTHER
                        categoryEdited = false
                        destination = ""
                        originalRecordId = null
                        reversalMovement = Movement.EXPENSE
                    }
                    movement = it
                    if (it !in setOf(Movement.INCOME, Movement.EXPENSE) || (it != Movement.EXPENSE && frequency == MovementFrequency.INSTALLMENTS)) frequency = MovementFrequency.SINGLE
                }
                if (movement == Movement.REFUND) {
                    val originals = history.filter { it.id != id && it.movement in setOf(Movement.EXPENSE, Movement.INCOME) }
                        .sortedByDescending { it.occurredAt }
                    ChoiceField("Lançamento original (opcional)", originalRecordId,
                        (listOf<String?>(null) + originals.map { it.id } + listOfNotNull(originalRecordId)).distinct(),
                        { selected -> if (selected == null) "Sem vínculo" else originals.firstOrNull { it.id == selected }?.let {
                            "${privateDisplayText(it.description, hideAmounts)} · ${shortDate(it.occurredAt)}"
                        } ?: "Lançamento original indisponível" }) { selected ->
                        originalRecordId = selected
                        originals.firstOrNull { it.id == selected }?.let {
                            reversalMovement = it.movement
                            category = it.category
                        }
                    }
                    if (originalRecordId == null) ChoiceField("Natureza do estorno", reversalMovement,
                        listOf(Movement.EXPENSE, Movement.INCOME),
                        { if (it == Movement.INCOME) "Devolução de receita" else "Reembolso de despesa" }) {
                        reversalMovement = it
                        category = if (it == Movement.INCOME) Category.OTHER_INCOME else Category.OTHER
                    }
                }
                if (movement != Movement.TRANSFER) {
                    val categoryOptions = (categoriesFor(movement, reversalMovement) + category).distinct()
                    if (movement == Movement.REFUND && originalRecordId != null) {
                        Text("Categoria do lançamento original: ${category.label}", style = MaterialTheme.typography.bodyMedium)
                        Text("Remova o vínculo para editar a categoria.", style = MaterialTheme.typography.bodySmall)
                    } else ChoiceField(if (draft != null) "Categoria sugerida · editável" else "Categoria", category,
                        categoryOptions, { it.label }) { category = it; categoryEdited = true }
                } else Text("Transferência entre minhas contas", style = MaterialTheme.typography.titleSmall)
                AccountPickerField("Conta ou banco de origem", bank, activeAccounts, { bank = it; accountEdited = true }, onAddAccount, hidden = hideAmounts)
                if (movement == Movement.TRANSFER) {
                    AccountPickerField("Conta de destino", destination, activeAccounts.filter { AccountNames.key(it.label) != AccountNames.key(bank) }, { destination = it }, onAddAccount, hidden = hideAmounts)
                    TransferPreview(record, existing, balances, hideAmounts)
                }
                if (!purchaseMode) FinanceDisclosure("Mais detalhes · meio de pagamento", "record-payment-details") {
                    ChoiceField("Meio de pagamento", method, PaymentMethod.entries.toList(), { it.label }, icon = R.drawable.ic_card) { method = it }
                }
                PrivateValueGate(hideAmounts && privateDisplayText(description, true) != description, "Mostrar descrição para edição") {
                OutlinedTextField(description, { value ->
                    description = value.take(200)
                    if (existing == null && preset == null && draft == null && movement != Movement.TRANSFER && originalRecordId == null) {
                        history.filter { it.movement == movement && it.description.trim().equals(description.trim(), ignoreCase = true) }
                            .maxByOrNull { it.occurredAt }?.let { match ->
                                if (!categoryEdited) category = match.category
                                if (!accountEdited && activeAccounts.any { AccountNames.key(it.label) == AccountNames.key(match.bank) }) bank = match.bank
                            }
                    }
                }, label = { Text("Descrição") }, modifier = Modifier.fillMaxWidth().testTag(if (recurring != null) "recurring-title" else "record-description"), minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done))
                }
                if (!purchaseMode) OutlinedButton(onClick = {
                    val selected = LocalDate.parse(date)
                    showFinanceDatePicker(context, dateDialogTheme, selected) {
                        date = it.toString()
                        if (!recurrenceOnlyMonth) recurringDay = if (recurring != null && it.dayOfMonth == LocalDate.parse(recurring.nextDate).dayOfMonth) recurring.dayOfMonth else it.dayOfMonth
                    }
                }, modifier = Modifier.fillMaxWidth().testTag(if (recurringMode) "recurring-due-date" else "record-date")) { AppIcon(R.drawable.ic_calendar); Spacer(Modifier.width(8.dp)); Text("${if (recurringMode) if (recurrenceOnlyMonth) "Vencimento deste mês" else "Início / primeiro vencimento" else "Data"}: ${LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}") }
                if (attempted && error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                if (onDelete != null) TextButton(onClick = { deleteDialog = true }, enabled = !busy) { Text("Excluir lançamento", color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(8.dp))
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { if (onIgnore != null) onIgnore() else onDismiss() }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text(if (onIgnore != null) "Não é transação" else "Cancelar")
                }
                Button(onClick = {
                    attempted = true
                    if (error == null && !notificationChanged) {
                        if (recurringMode) onSaveRecurring?.invoke(recurringValue, fixedExpense)
                        else if (!purchaseMode) onSave(record)
                        else schedule?.let {
                            onSavePurchase?.invoke(InstallmentPurchase(id, description.trim(), bank.trim(), category,
                                it.totalCents, it.installmentCount, terms.due, it.paidInstallments, it.dayOfMonth))
                        }
                    }
                }, enabled = !busy && !notificationChanged, modifier = Modifier.weight(1f)) {
                    Text(if (busy) "Salvando…" else if (recurringMode) if (recurrenceOnlyMonth) "Salvar só este mês" else "Salvar recorrência" else if (purchaseMode) "Salvar compra parcelada" else "Confirmar")
                }
            }
        }
    }
    if (deleteDialog) AlertDialog(onDismissRequest = { deleteDialog = false }, title = { DialogSystemBars(); Text("Excluir lançamento?") },
        text = { Text("O lançamento será removido do histórico e dos totais. Esta ação não pode ser desfeita.") },
        confirmButton = { TextButton(onClick = { deleteDialog = false; onDelete?.invoke(); onDismiss() }) { Text("Excluir") } },
        dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("Cancelar") } })
}

@Composable
fun <T> ChoiceField(label: String, value: T, options: List<T>, text: (T) -> String, @androidx.annotation.DrawableRes icon: Int? = null, onChange: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = label }, shape = MaterialTheme.shapes.small, border = null,
                colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, contentColor = MaterialTheme.colorScheme.onSurface), contentPadding = PaddingValues(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    if (icon != null) AppIcon(icon)
                    Text(text(value), Modifier.weight(1f)); AppIcon(R.drawable.ic_chevron_down)
                }
            }
            DropdownMenu(expanded, { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                options.forEach { option ->
                    val isSelected = option == value
                    DropdownMenuItem(text = { Text(text(option)) },
                        leadingIcon = if (icon != null) { { AppIcon(selectedIcon(icon, isSelected)) } } else null,
                        modifier = Modifier.semantics { selected = isSelected },
                        onClick = { onChange(option); expanded = false })
                }
            }
        }
    }
}
