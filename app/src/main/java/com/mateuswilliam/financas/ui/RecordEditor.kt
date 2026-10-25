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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
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
) {
    val initial = existing ?: preset
    val originalAt = rememberSaveable { initial?.occurredAt ?: draft?.occurredAt ?: System.currentTimeMillis() }
    val originalDate = Instant.ofEpochMilli(originalAt).atZone(ZoneId.systemDefault())
    val id = rememberSaveable { initial?.id ?: UUID.randomUUID().toString() }
    val amount = rememberTextFieldState(initialText = (initial?.amountCents ?: draft?.amountCents)?.let(MoneyInput::fromCents).orEmpty())
    var invalidMoneyInput by rememberSaveable { mutableStateOf(false) }
    var bank by rememberSaveable { mutableStateOf(initial?.bank ?: draft?.bank ?: lastOrigin.ifBlank { "Carteira" }) }
    var description by rememberSaveable { mutableStateOf(initial?.description ?: draft?.sourceBody?.take(200).orEmpty()) }
    var movement by rememberSaveable { mutableStateOf(initial?.movement ?: draft?.movement ?: initialMovement) }
    var method by rememberSaveable { mutableStateOf(initial?.method ?: draft?.method ?: PaymentMethod.UNKNOWN) }
    var category by rememberSaveable { mutableStateOf(initial?.category ?: draft?.category ?: Category.OTHER) }
    var destination by rememberSaveable { mutableStateOf(initial?.destination.orEmpty()) }
    var date by rememberSaveable { mutableStateOf(originalDate.toLocalDate().toString()) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val dateDialogTheme = if (MaterialTheme.colorScheme.background.luminance() < 0.5f)
        android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val compactHeight = windowHeight < 480.dp
    val maximumHeight = windowHeight * if (compactHeight) 0.96f else 0.84f
    val compactScroll = rememberScrollState()
    val fieldsScroll = rememberScrollState()
    val cents = if (invalidMoneyInput) null else MoneyInput.cents(amount.text.toString())
    val record = Record(id, cents ?: 0, movement, method, category, bank, description,
        LocalDate.parse(date).atTime(originalDate.toLocalTime()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), destination)
    val error = RecordValidation.error(record)
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
                Text(if (draft != null) "Conferir movimentação" else if (preset != null) "Conferir previsão" else if (existing != null) "Editar lançamento" else "Novo lançamento",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(if (draft != null) "A notificação só vira lançamento após revisão e confirmação." else if (preset != null) "Confira valor, conta e data efetiva antes de transformar a previsão em lançamento." else "Valores, contas e categorias podem ser ajustados antes de salvar.",
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
                PrivateValueGate(hideAmounts, "Mostrar valor para edição") { MoneyAmountField(amount, attempted, enabled = !busy, onInvalidInput = { invalidMoneyInput = it }) }
                ChoiceField("Movimentação", movement, if (preset != null) listOf(Movement.EXPENSE, Movement.INCOME) else Movement.entries.toList(), { it.label }) { movement = it }
                ChoiceField("Meio de pagamento", method, PaymentMethod.entries.toList(), { it.label }, icon = R.drawable.ic_card) { method = it }
                ChoiceField(if (draft != null) "Categoria sugerida · editável" else "Categoria", category, Category.entries.toList(), { it.label }) { category = it }
                AccountPickerField("Conta ou banco de origem", bank, accounts, { bank = it }, onAddAccount, hidden = hideAmounts)
                if (movement == Movement.TRANSFER) AccountPickerField("Conta de destino", destination, accounts, { destination = it }, onAddAccount, hidden = hideAmounts)
                PrivateValueGate(hideAmounts && privateDisplayText(description, true) != description, "Mostrar descrição para edição") {
                OutlinedTextField(description, { description = it.take(200) }, label = { Text("Descrição") }, modifier = Modifier.fillMaxWidth(), minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done))
                }
                OutlinedButton(onClick = {
                    val selected = LocalDate.parse(date)
                    showFinanceDatePicker(context, dateDialogTheme, selected) { date = it.toString() }
                }, modifier = Modifier.fillMaxWidth()) { AppIcon(R.drawable.ic_calendar); Spacer(Modifier.width(8.dp)); Text("Data: ${LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}") }
                if (attempted && error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                if (onDelete != null) TextButton(onClick = { deleteDialog = true }, enabled = !busy) { Text("Excluir lançamento", color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(8.dp))
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { if (onIgnore != null) onIgnore() else onDismiss() }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text(if (onIgnore != null) "Não é transação" else "Cancelar")
                }
                Button(onClick = { attempted = true; if (cents != null && error == null && !notificationChanged) onSave(record) }, enabled = !busy && !notificationChanged, modifier = Modifier.weight(1f)) {
                    Text(if (busy) "Salvando…" else "Confirmar")
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
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small, border = null,
                colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, contentColor = MaterialTheme.colorScheme.onSurface), contentPadding = PaddingValues(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    if (icon != null) AppIcon(icon)
                    Text(text(value), Modifier.weight(1f)); AppIcon(R.drawable.ic_chevron_down)
                }
            }
            DropdownMenu(expanded, { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                options.forEach { option -> DropdownMenuItem(text = { Text(text(option)) }, onClick = { onChange(option); expanded = false }) }
            }
        }
    }
}
