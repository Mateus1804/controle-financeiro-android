package com.mateuswilliam.financas.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.FinanceState
import com.mateuswilliam.financas.core.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
fun PlanningScreen(state: FinanceState, month: YearMonth, onMonth: (YearMonth) -> Unit, busy: Boolean,
    canAlert: Boolean, onBack: () -> Unit, onBudget: (MonthlyBudget, () -> Unit) -> Unit,
    onDeleteBudget: (Category) -> Unit, onRecurring: (RecurringPlan, () -> Unit) -> Unit,
    onConfirmRecurring: (RecurringPlan) -> Unit, onSkip: (RecurringPlan) -> Unit,
    onReminders: (Boolean) -> Unit, onAlerts: () -> Unit, onAddAccount: suspend (String) -> String) {
    var budgetEditor by rememberSaveable { mutableStateOf(false) }
    var budgetCategory by rememberSaveable { mutableStateOf<Category?>(null) }
    var recurringEditor by rememberSaveable { mutableStateOf(false) }
    var recurringId by rememberSaveable { mutableStateOf<String?>(null) }
    var skipping by rememberSaveable { mutableStateOf<String?>(null) }
    val zone = ZoneId.systemDefault()
    val usage = remember(state.records, state.budgets, month, zone) { Budgets.usage(state.records, state.budgets, month, zone) }
    val hidden = state.settings.hideAmounts
    LazyColumn(Modifier.testTag("planning-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            TextButton(onClick = onBack) { Text("Voltar") }
            SectionTitle("Planejamento", "Previsões e limites; os totais incluem apenas lançamentos confirmados")
        }
        item { MonthPicker(month, onMonth) }
        item {
            SectionTitle("Orçamentos por categoria", "Despesas líquidas confirmadas, com estornos descontados. Receitas e transferências não consomem o limite.")
            Button(onClick = { budgetCategory = null; budgetEditor = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Adicionar limite mensal") }
        }
        if (usage.isEmpty()) item { EmptyCard("Sem limites neste mês", "O cadastro de um limite organiza o acompanhamento e não modifica os lançamentos.") }
        items(usage, key = { "budget:${it.budget.month}:${it.budget.category}" }) { item ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(item.budget.category.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (hidden) "Gasto e limite ocultos" else "${Money.format(item.spentCents)} de ${Money.format(item.budget.limitCents)}")
                    if (!hidden) {
                        LinearProgressIndicator(progress = { (item.spentCents.toDouble() / item.budget.limitCents).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { })
                        Text(if (item.remainingCents >= 0) "Disponível: ${Money.format(item.remainingCents)}" else "Limite excedido em ${Money.format(-item.remainingCents)}",
                            color = if (item.remainingCents < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { budgetCategory = item.budget.category; budgetEditor = true }, enabled = !busy) { Text("Editar limite") }
                        TextButton(onClick = { onDeleteBudget(item.budget.category) }, enabled = !busy) { Text("Remover limite") }
                    }
                }
            }
        }
        item {
            SectionTitle("Contas e receitas recorrentes", "Previsão mensal com confirmação manual de cada vencimento")
            Button(onClick = { recurringId = null; recurringEditor = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Adicionar recorrência") }
        }
        if (state.recurring.isEmpty()) item { EmptyCard("Nenhuma recorrência cadastrada", "O vencimento pode ser lembrado sem criar pagamentos ou receitas automaticamente.") }
        items(state.recurring, key = { "recurring:${it.id}" }) { plan ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(plan.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${plan.movement.label} prevista · ${formatDate(plan.nextDate)}${if (!plan.active) " · pausada" else ""}")
                    Text("${plan.bank} · ${plan.category.label}", style = MaterialTheme.typography.bodySmall)
                    Amount(plan.amountCents, hidden)
                    OutlinedButton(onClick = { onConfirmRecurring(plan) }, enabled = !busy && plan.active, modifier = Modifier.fillMaxWidth()) { Text("Conferir e registrar") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { recurringId = plan.id; recurringEditor = true }, enabled = !busy) { Text("Editar previsão") }
                        TextButton(onClick = { onRecurring(plan.copy(active = !plan.active), {}) }, enabled = !busy) { Text(if (plan.active) "Pausar" else "Retomar") }
                    }
                    if (plan.active) TextButton(onClick = { skipping = plan.id }, enabled = !busy) { Text("Pular este vencimento") }
                }
            }
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Lembretes de vencimentos", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Switch(state.settings.remindersEnabled, onReminders, modifier = Modifier.semantics { contentDescription = "Lembretes de vencimentos" })
                    }
                    Text("A verificação ocorre ao abrir o aplicativo e periodicamente em segundo plano, sem horário exato. Restrições de bateria, reinício e parada forçada podem atrasar os alertas. Cada vencimento é avisado uma vez quando o envio é permitido.", style = MaterialTheme.typography.bodySmall)
                    if (!canAlert) {
                        Text("O envio de alertas está indisponível. O planejamento e a captura autorizada continuam disponíveis.")
                        OutlinedButton(onClick = onAlerts, modifier = Modifier.fillMaxWidth()) { Text("Permitir alertas de revisão e lembretes") }
                    }
                }
            }
        }
    }
    if (budgetEditor) key(budgetCategory, month) {
        BudgetEditor(state.budgets.find { it.month == month.toString() && it.category == budgetCategory }, month, busy,
            onDismiss = { budgetEditor = false }, onSave = { onBudget(it) { budgetEditor = false } })
    }
    if (recurringEditor) key(recurringId) {
        RecurringEditor(state.recurring.find { it.id == recurringId }, state.accounts, state.settings.lastOrigin, busy,
            onDismiss = { recurringEditor = false }, onSave = { onRecurring(it) { recurringEditor = false } }, onAddAccount = onAddAccount)
    }
    val plan = state.recurring.find { it.id == skipping }
    if (plan != null) AlertDialog(onDismissRequest = { skipping = null }, title = { DialogSystemBars(); Text("Pular vencimento?") },
        text = { Text("A previsão de ${formatDate(plan.nextDate)} será marcada como pulada. Nenhum lançamento será criado, e a próxima previsão mensal ficará disponível.") },
        confirmButton = { TextButton(enabled = !busy, onClick = { onSkip(plan); skipping = null }) { Text("Pular vencimento") } },
        dismissButton = { TextButton(onClick = { skipping = null }) { Text("Cancelar") } })
}

@Composable
private fun BudgetEditor(existing: MonthlyBudget?, month: YearMonth, busy: Boolean, onDismiss: () -> Unit, onSave: (MonthlyBudget) -> Unit) {
    var category by rememberSaveable { mutableStateOf(existing?.category ?: Category.FOOD) }
    val amount = rememberTextFieldState(initialText = existing?.limitCents?.let(MoneyInput::fromCents).orEmpty())
    var invalid by rememberSaveable { mutableStateOf(false) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val budget = MonthlyBudget(month.toString(), category, if (invalid) 0 else MoneyInput.cents(amount.text.toString()) ?: 0)
    val error = Budgets.error(budget)
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { DialogSystemBars(); Text("Limite mensal") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("O limite vale para ${month.format(DateTimeFormatter.ofPattern("MM/yyyy"))}. Salvar uma categoria já cadastrada atualiza o limite desse mês.")
            ChoiceField("Categoria do orçamento", category, Category.entries.toList(), { it.label }) { category = it }
            MoneyAmountField(amount, attempted, enabled = !busy, onInvalidInput = { invalid = it })
            if (attempted && error != null) Text(error, color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(enabled = !busy, onClick = { attempted = true; if (error == null) onSave(budget) }) { Text("Salvar limite") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun RecurringEditor(existing: RecurringPlan?, accounts: List<AccountOption>, lastOrigin: String, busy: Boolean,
    onDismiss: () -> Unit, onSave: (RecurringPlan) -> Unit, onAddAccount: suspend (String) -> String) {
    val id = rememberSaveable { existing?.id ?: UUID.randomUUID().toString() }
    var title by rememberSaveable { mutableStateOf(existing?.title.orEmpty()) }
    val amount = rememberTextFieldState(initialText = existing?.amountCents?.let(MoneyInput::fromCents).orEmpty())
    var invalid by rememberSaveable { mutableStateOf(false) }
    var movement by rememberSaveable { mutableStateOf(existing?.movement ?: Movement.EXPENSE) }
    var method by rememberSaveable { mutableStateOf(existing?.method ?: PaymentMethod.UNKNOWN) }
    var category by rememberSaveable { mutableStateOf(existing?.category ?: Category.HOME) }
    var bank by rememberSaveable { mutableStateOf(existing?.bank ?: lastOrigin.ifBlank { "Carteira" }) }
    var date by rememberSaveable { mutableStateOf(existing?.nextDate ?: LocalDate.now().toString()) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val day = if (existing != null && existing.nextDate == date) existing.dayOfMonth else LocalDate.parse(date).dayOfMonth
    val plan = RecurringPlan(id, title, if (invalid) 0 else MoneyInput.cents(amount.text.toString()) ?: 0,
        movement, method, category, bank, date, day, existing?.active ?: true)
    val error = Recurrences.error(plan)
    val context = LocalContext.current
    val theme = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { DialogSystemBars(); Text("Previsão mensal") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Cada vencimento depende de confirmação. A previsão não modifica os lançamentos nem o resultado mensal.")
            OutlinedTextField(title, { title = it.take(200) }, label = { Text("Descrição da previsão") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
            MoneyAmountField(amount, attempted, enabled = !busy, onInvalidInput = { invalid = it })
            ChoiceField("Tipo previsto", movement, listOf(Movement.EXPENSE, Movement.INCOME), { it.label }) { movement = it }
            ChoiceField("Meio previsto", method, PaymentMethod.entries.toList(), { it.label }) { method = it }
            ChoiceField("Categoria prevista", category, Category.entries.toList(), { it.label }) { category = it }
            AccountPickerField("Conta da previsão", bank, accounts, { bank = it }, onAddAccount)
            OutlinedButton(onClick = {
                val selected = LocalDate.parse(date)
                DatePickerDialog(context, theme, { _, y, m, d -> date = LocalDate.of(y, m + 1, d).toString() }, selected.year, selected.monthValue - 1, selected.dayOfMonth).show()
            }, modifier = Modifier.fillMaxWidth()) { Text("Próximo vencimento: ${formatDate(date)}") }
            Text("Repetição mensal no dia $day. Meses mais curtos usam o último dia, mantendo o dia original para o próximo mês.", style = MaterialTheme.typography.bodySmall)
            if (attempted && error != null) Text(error, color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(enabled = !busy, onClick = { attempted = true; if (error == null) onSave(plan) }) { Text("Salvar previsão") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } })
}

private fun formatDate(value: String) = LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
