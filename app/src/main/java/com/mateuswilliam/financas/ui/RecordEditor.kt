package com.mateuswilliam.financas.ui

import android.app.DatePickerDialog
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
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.core.*
import com.mateuswilliam.financas.data.DraftEntity
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
) {
    val originalAt = existing?.occurredAt ?: draft?.occurredAt ?: System.currentTimeMillis()
    val originalDate = Instant.ofEpochMilli(originalAt).atZone(ZoneId.systemDefault())
    val id = rememberSaveable { existing?.id ?: UUID.randomUUID().toString() }
    val amount = rememberTextFieldState(initialText = (existing?.amountCents ?: draft?.amountCents)?.let(MoneyInput::fromCents).orEmpty())
    var bank by rememberSaveable { mutableStateOf(existing?.bank ?: draft?.bank ?: "Carteira") }
    var description by rememberSaveable { mutableStateOf(existing?.description ?: draft?.sourceBody?.take(200).orEmpty()) }
    var movement by rememberSaveable { mutableStateOf(existing?.movement ?: draft?.movement ?: Movement.EXPENSE) }
    var method by rememberSaveable { mutableStateOf(existing?.method ?: draft?.method ?: PaymentMethod.UNKNOWN) }
    var category by rememberSaveable { mutableStateOf(existing?.category ?: draft?.category ?: Category.OTHER) }
    var destination by rememberSaveable { mutableStateOf(existing?.destination.orEmpty()) }
    var date by rememberSaveable { mutableStateOf(originalDate.toLocalDate().toString()) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val dateDialogTheme = if (MaterialTheme.colorScheme.background.luminance() < 0.5f)
        android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert
    val maximumHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } * 0.84f
    val cents = MoneyInput.cents(amount.text.toString())
    val record = Record(id, cents ?: 0, movement, method, category, bank, description,
        LocalDate.parse(date).atTime(originalDate.toLocalTime()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), destination)
    val error = RecordValidation.error(record)

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = maximumHeight).imePadding()) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (draft != null) "Conferir movimentação" else if (existing != null) "Editar lançamento" else "Novo lançamento",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(if (draft != null) "A notificação só vira lançamento após sua confirmação." else "Seu controle, do seu jeito.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (draft != null) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${draft.bank} · ${shortDate(draft.occurredAt)}", style = MaterialTheme.typography.labelMedium)
                            Text(draft.sourceTitle, style = MaterialTheme.typography.titleSmall)
                            Text(draft.sourceBody, style = MaterialTheme.typography.bodySmall)
                            Text(draft.explanation, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                MoneyAmountField(amount, attempted, enabled = !busy)
                ChoiceField("Movimentação", movement, Movement.entries.toList(), { it.label }) { movement = it }
                ChoiceField("Meio de pagamento", method, PaymentMethod.entries.toList(), { it.label }) { method = it }
                ChoiceField(if (draft != null) "Categoria sugerida · editável" else "Categoria", category, Category.entries.toList(), { it.label }) { category = it }
                OutlinedTextField(bank, { bank = it.take(80) }, label = { Text("Conta ou banco de origem") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (movement == Movement.TRANSFER) OutlinedTextField(destination, { destination = it.take(80) }, label = { Text("Conta de destino") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(description, { description = it.take(200) }, label = { Text("Descrição") }, modifier = Modifier.fillMaxWidth(), minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                OutlinedButton(onClick = {
                    val selected = LocalDate.parse(date)
                    DatePickerDialog(context, dateDialogTheme, { _, year, month, day -> date = LocalDate.of(year, month + 1, day).toString() },
                        selected.year, selected.monthValue - 1, selected.dayOfMonth).show()
                }, modifier = Modifier.fillMaxWidth()) { Text("Data: ${LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}") }
                if (attempted && error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                if (onDelete != null) TextButton(onClick = { deleteDialog = true }, enabled = !busy) { Text("Excluir lançamento", color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(8.dp))
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { if (onIgnore != null) onIgnore() else onDismiss() }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text(if (onIgnore != null) "Não é transação" else "Cancelar")
                }
                Button(onClick = { attempted = true; if (cents != null && error == null) onSave(record) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text(if (busy) "Salvando…" else "Confirmar")
                }
            }
        }
    }
    if (deleteDialog) AlertDialog(onDismissRequest = { deleteDialog = false }, title = { Text("Excluir lançamento?") },
        text = { Text("O lançamento será removido do histórico e dos totais. Esta ação não pode ser desfeita.") },
        confirmButton = { TextButton(onClick = { deleteDialog = false; onDelete?.invoke(); onDismiss() }) { Text("Excluir") } },
        dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("Cancelar") } })
}

@Composable
fun <T> ChoiceField(label: String, value: T, options: List<T>, text: (T) -> String, onChange: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small, contentPadding = PaddingValues(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(text(value)); Text("⌄") }
            }
            DropdownMenu(expanded, { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
                options.forEach { option -> DropdownMenuItem(text = { Text(text(option)) }, onClick = { onChange(option); expanded = false }) }
            }
        }
    }
}
