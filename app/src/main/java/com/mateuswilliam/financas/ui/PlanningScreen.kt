package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import com.mateuswilliam.financas.R
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
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
import com.mateuswilliam.financas.data.CommitmentSetup
import com.mateuswilliam.financas.data.InstallmentDue
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanningScreen(state: FinanceState, month: YearMonth, onMonth: (YearMonth) -> Unit, busy: Boolean,
    canAlert: Boolean, onBack: () -> Unit, onBudget: (MonthlyBudget, () -> Unit) -> Unit,
    onDeleteBudget: (Category) -> Unit, onRecurring: (RecurringPlan, () -> Unit) -> Unit,
    recurringMonths: List<RecurringMonth>?,
    onRecurringMonth: (RecurringMonth, RecurringPlan, () -> Unit) -> Unit,
    onRecurringFromMonth: (RecurringPlan, YearMonth, () -> Unit) -> Unit,
    onRecurringActiveFromMonth: (String, YearMonth, Boolean) -> Unit,
    onRecurringConfiguration: suspend (String, YearMonth) -> RecurringPlan?,
    onConfirmRecurring: (RecurringMonth) -> Unit, onSkip: (RecurringMonth) -> Unit,
    onExcludeRecurring: (RecurringPlan, () -> Unit) -> Unit,
    onReminders: (Boolean) -> Unit, onAlerts: () -> Unit, onAddAccount: suspend (String) -> String,
    commitments: CommitmentSetup?, commitmentReport: Result<CommitmentReport>?, onIncome: (MonthlyIncomePlan, () -> Unit) -> Unit,
    onFixedRecurring: (String, YearMonth, Boolean) -> Unit, onConfirmInstallment: (InstallmentDue) -> Unit,
    onEditPurchase: (String) -> Unit, onExcludeInstallment: (String) -> Unit, onCancelPurchase: (String) -> Unit,
) {
    var budgetEditor by rememberSaveable { mutableStateOf(false) }
    var budgetCategory by rememberSaveable { mutableStateOf<Category?>(null) }
    var removingBudget by rememberSaveable(month.toString()) { mutableStateOf<Category?>(null) }
    var recurringEditor by rememberSaveable { mutableStateOf(false) }
    var recurringId by rememberSaveable { mutableStateOf<String?>(null) }
    var monthlyEditor by rememberSaveable(stateSaver = RecurringMonthStateSaver) { mutableStateOf<RecurringMonth?>(null) }
    var skipping by rememberSaveable(stateSaver = RecurringMonthStateSaver) { mutableStateOf<RecurringMonth?>(null) }
    var excludingRecurring by rememberSaveable { mutableStateOf<String?>(null) }
    var incomeEditor by rememberSaveable(month.toString()) { mutableStateOf(false) }
    var otherExpanded by rememberSaveable(month.toString()) { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val usage = remember(state.records, state.budgets, month, zone) { Budgets.usage(state.records, state.budgets, month, zone) }
    val hidden = state.settings.hideAmounts
    LazyColumn(Modifier.testTag("planning-list"), contentPadding = PaddingValues(FinanceSpacing.page, 16.dp, FinanceSpacing.page, FinanceSpacing.listBottom), verticalArrangement = Arrangement.spacedBy(FinanceSpacing.cardGap)) {
        item {
            TextButton(onClick = onBack) { AppIcon(R.drawable.ic_chevron_left); Text("Voltar") }
            SectionTitle("Planejamento", "Renda prevista, compromissos e limites do mês")
        }
        item { MonthPicker(month, onMonth) }
        item(key = "commitment-card") {
            MonthlyCommitmentCard(commitmentReport, commitments?.income, commitments != null, busy, hidden) { incomeEditor = true }
        }
        if (!hidden && commitments != null) commitmentReport?.getOrNull()?.let { report ->
            commitmentLegend(report, otherExpanded) { otherExpanded = !otherExpanded }
        }
        item {
            Column(Modifier.padding(top = 8.dp)) {
                SectionTitle("Orçamentos por categoria", "Despesas líquidas confirmadas no mês")
                FinanceDisclosure("Como os limites são calculados", "budget-explanation") {
                    Text("Os estornos são descontados. Receitas e transferências não consomem o limite.", style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { budgetCategory = null; budgetEditor = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = FinanceSpacing.touch)) { Text("Adicionar limite mensal") }
            }
        }
        if (usage.isEmpty()) item { EmptyCard("Sem limites neste mês", "O cadastro de um limite organiza o acompanhamento e não modifica os lançamentos.") }
        items(usage, key = { "budget:${it.budget.month}:${it.budget.category}" }) { item ->
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Column(Modifier.fillMaxWidth().padding(FinanceSpacing.card), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBadge(categoryIcon(item.budget.category))
                        Text(item.budget.category.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        ContextualActions(item.budget.category.label, "actions-budget-${item.budget.category.name}", !busy,
                            listOf(
                                FinanceAction("Editar limite", { budgetCategory = item.budget.category; budgetEditor = true }),
                                FinanceAction("Remover limite", { removingBudget = item.budget.category }, destructive = true),
                            ))
                    }
                    Text(if (hidden) "Gasto e limite ocultos" else "${Money.format(item.spentCents)} de ${Money.format(item.budget.limitCents)}",
                        style = MaterialTheme.typography.bodyMedium)
                    if (!hidden) {
                        LinearProgressIndicator(progress = { (item.spentCents.toDouble() / item.budget.limitCents).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { })
                        Text(if (item.remainingCents >= 0) "Disponível: ${Money.format(item.remainingCents)}" else "Limite excedido em ${Money.format(-item.remainingCents)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.remainingCents < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Box(Modifier.padding(top = 8.dp)) { SectionTitle("Parcelas do mês", "Os valores só entram no extrato ao registrar cada parcela.") }
        }
        commitments?.let { setup ->
            if (setup.installments.isEmpty()) item { EmptyCard("Sem parcelas neste mês", "Para planejar parcelas, ative Compra parcelada ao registrar uma despesa.") }
            items(setup.installments, key = { "due:${it.id}" }) { installment ->
                InstallmentDueCard(installment, busy, hidden, onConfirm = { onConfirmInstallment(installment) },
                    onEdit = { onEditPurchase(installment.id) }, onExclude = { onExcludeInstallment(installment.id) },
                    onCancel = { onCancelPurchase(installment.id) })
            }
        }
        item {
            Column(Modifier.padding(top = 8.dp)) {
                SectionTitle("Contas e receitas recorrentes", "Vencimentos de ${month.format(commitmentMonthFormat)}")
                Button(onClick = { recurringId = null; recurringEditor = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = FinanceSpacing.touch)) { Text("Adicionar recorrência") }
            }
        }
        if (recurringMonths == null) item {
            Text("Carregando os vencimentos do mês…", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (recurringMonths != null && recurringMonths.isEmpty()) item {
            EmptyCard(if (state.recurring.isEmpty()) "Nenhuma recorrência cadastrada" else "Sem recorrências neste mês",
                "O vencimento pode ser lembrado sem criar pagamentos ou receitas automaticamente.")
        }
        items(recurringMonths.orEmpty(), key = { "recurring:${it.recurringId}:${it.month}" }) { occurrence ->
            val plan = occurrence.plan
            val template = state.recurring.find { it.id == plan.id }
            val pending = occurrence.status == RecurringMonthStatus.PENDING
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Column(Modifier.fillMaxWidth().padding(FinanceSpacing.card), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(privateDisplayText(plan.title, hidden), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("${plan.movement.label} · ${formatDate(plan.nextDate)}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${privateDisplayText(plan.bank, hidden)} · ${plan.category.label}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val actions = buildList {
                            if (pending) {
                                add(FinanceAction("Editar este mês", { monthlyEditor = occurrence }, "edit-month-${plan.id}"))
                                add(FinanceAction("Pular este vencimento", { skipping = occurrence }, "skip-${plan.id}"))
                            }
                            if (template != null) {
                                add(FinanceAction("Editar configuração recorrente", { recurringId = plan.id; recurringEditor = true }, "edit-recurring-${plan.id}"))
                                if (pending) add(FinanceAction(if (plan.active) "Pausar a partir deste mês" else "Retomar a partir deste mês",
                                    { onRecurringActiveFromMonth(plan.id, month, !plan.active) }, "active-recurring-${plan.id}"))
                                if (pending && (plan.movement == Movement.EXPENSE || occurrence.fixedExpense)) add(FinanceAction(
                                    if (!occurrence.fixedExpense) "Incluir como despesa fixa" else "Deixar de incluir como fixa",
                                    { onFixedRecurring(plan.id, month, !occurrence.fixedExpense) },
                                    "fixed-${plan.id}", enabled = commitments != null))
                                add(FinanceAction("Excluir previsão", { excludingRecurring = plan.id }, "exclude-recurring-${plan.id}", destructive = true))
                            }
                        }
                        if (actions.isNotEmpty()) ContextualActions(privateDisplayText(plan.title, hidden), "actions-recurring-${plan.id}", !busy, actions)
                    }
                    Amount(plan.amountCents, hidden, size = 18)
                    val status = when (occurrence.status) {
                        RecurringMonthStatus.RECORDED -> "Registrada no extrato"
                        RecurringMonthStatus.SKIPPED -> "Vencimento pulado neste mês"
                        RecurringMonthStatus.PENDING -> if (plan.active) "Pendente" else "Pausada"
                    }
                    Text(status + if (occurrence.fixedExpense) " · Despesa fixa" else "", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (pending) OutlinedButton(onClick = { onConfirmRecurring(occurrence) }, enabled = !busy && plan.active,
                        modifier = Modifier.fillMaxWidth().heightIn(min = FinanceSpacing.touch).testTag("confirm-recurring-${plan.id}")) { Text("Conferir e registrar") }
                }
            }
        }
        item {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(FinanceSpacing.card), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Lembretes de vencimentos", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Switch(state.settings.remindersEnabled, onReminders, modifier = Modifier.semantics { contentDescription = "Lembretes de vencimentos" })
                    }
                    FinanceDisclosure("Como os lembretes funcionam", "reminder-explanation") {
                        Text("A verificação ocorre ao abrir o aplicativo e periodicamente em segundo plano, sem horário exato. Restrições de bateria, reinício e parada forçada podem atrasar os alertas. Cada vencimento é avisado uma vez quando o envio é permitido.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (!canAlert) {
                        Text("O envio de alertas está indisponível. O planejamento e a captura autorizada continuam disponíveis.")
                        OutlinedButton(onClick = onAlerts, modifier = Modifier.fillMaxWidth()) { Text("Permitir alertas de revisão e lembretes") }
                    }
                }
            }
        }
    }
    if (incomeEditor && commitments != null) key(month) {
        MonthlyIncomeEditor(month, commitments.income, busy, hidden, onDismiss = { incomeEditor = false },
            onSave = { onIncome(it) { incomeEditor = false } })
    }
    if (budgetEditor) key(budgetCategory, month) {
        BudgetEditor(state.budgets.find { it.month == month.toString() && it.category == budgetCategory }, month, busy, hidden,
            onDismiss = { budgetEditor = false }, onSave = { onBudget(it) { budgetEditor = false } })
    }
    removingBudget?.let { category ->
        AlertDialog(onDismissRequest = { if (!busy) removingBudget = null },
            title = { DialogSystemBars(); Text("Remover limite mensal?") },
            text = { Text("O limite de ${category.label} em ${month.format(commitmentMonthFormat)} será removido. Os lançamentos e os outros meses serão mantidos.") },
            confirmButton = { TextButton(enabled = !busy, modifier = Modifier.testTag("confirm-remove-budget"),
                onClick = { onDeleteBudget(category); removingBudget = null }) { Text("Remover limite", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(enabled = !busy, modifier = Modifier.testTag("keep-budget"),
                onClick = { removingBudget = null }) { Text("Cancelar") } })
    }
    if (recurringEditor) key(recurringId, month) {
        val id = recurringId
        val configuration by produceState<Result<RecurringPlan?>?>(null, id, month) {
            if (id != null) value = try {
                Result.success(onRecurringConfiguration(id, month))
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Result.failure(exception)
            }
        }
        val current = configuration?.getOrNull()
        if (id == null || current != null) {
            RecurringEditor(current, state.accounts, state.settings.lastOrigin, busy, hidden,
                onDismiss = { recurringEditor = false }, onSave = {
                    if (id == null) onRecurring(it) { recurringEditor = false }
                    else onRecurringFromMonth(it, month) { recurringEditor = false }
                }, onAddAccount = onAddAccount, effectiveMonth = if (id != null) month else null,
                defaultDate = month.atDay(minOf(LocalDate.now().dayOfMonth, month.lengthOfMonth())))
        } else AlertDialog(onDismissRequest = { recurringEditor = false },
            title = { DialogSystemBars(); Text("Configuração recorrente") },
            text = { Text(if (configuration == null) "Carregando a configuração deste mês…"
                else "Não foi possível carregar a configuração deste mês. Volte ao Planejamento e tente novamente.") },
            confirmButton = { TextButton(onClick = { recurringEditor = false }) { Text("Voltar") } })
    }
    monthlyEditor?.let { expected -> key(expected.recurringId, expected.month, expected.revision) {
        RecurringEditor(expected.plan, state.accounts, state.settings.lastOrigin, busy, hidden,
            onDismiss = { monthlyEditor = null },
            onSave = { onRecurringMonth(expected, it) { monthlyEditor = null } }, onAddAccount = onAddAccount,
            monthScope = expected.month)
    } }
    skipping?.let { expected ->
        AlertDialog(onDismissRequest = { if (!busy) skipping = null }, title = { DialogSystemBars(); Text("Pular vencimento?") },
            text = { Text("A previsão de ${formatDate(expected.plan.nextDate)} será marcada como pulada em ${expected.month.format(commitmentMonthFormat)}. Nenhum lançamento será criado. Os outros meses serão mantidos.") },
            confirmButton = { TextButton(enabled = !busy, onClick = { onSkip(expected); skipping = null }) { Text("Pular vencimento") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { skipping = null }) { Text("Cancelar") } })
    }
    state.recurring.find { it.id == excludingRecurring }?.let { current ->
        AlertDialog(onDismissRequest = { if (!busy) excludingRecurring = null },
            title = { DialogSystemBars(); Text("Excluir previsão mensal?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(privateDisplayText(current.title, hidden), style = MaterialTheme.typography.titleMedium)
                    Text("Esta previsão deixa de gerar vencimentos e lembretes. Os lançamentos já registrados continuam no extrato.")
                }
            },
            confirmButton = {
                TextButton(onClick = { onExcludeRecurring(current) { excludingRecurring = null } }, enabled = !busy,
                    modifier = Modifier.testTag("confirm-exclude-recurring")) {
                    Text("Excluir previsão", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { excludingRecurring = null }, enabled = !busy) { Text("Voltar") } })
    }
}

@Composable
private fun BudgetEditor(existing: MonthlyBudget?, month: YearMonth, busy: Boolean, hidden: Boolean, onDismiss: () -> Unit, onSave: (MonthlyBudget) -> Unit) {
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
            PrivateValueGate(hidden, "Mostrar valor para edição") { MoneyAmountField(amount, attempted, enabled = !busy, onInvalidInput = { invalid = it }) }
            if (attempted && error != null) Text(error, color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { FilledTonalButton(enabled = !busy, onClick = { attempted = true; if (error == null) onSave(budget) }) { Text("Salvar limite") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
private fun RecurringEditor(existing: RecurringPlan?, accounts: List<AccountOption>, lastOrigin: String, busy: Boolean, hidden: Boolean,
    onDismiss: () -> Unit, onSave: (RecurringPlan) -> Unit, onAddAccount: suspend (String) -> String,
    monthScope: YearMonth? = null, effectiveMonth: YearMonth? = null, defaultDate: LocalDate = LocalDate.now()) {
    val id = rememberSaveable { existing?.id ?: UUID.randomUUID().toString() }
    var title by rememberSaveable { mutableStateOf(existing?.title.orEmpty()) }
    val amount = rememberTextFieldState(initialText = existing?.amountCents?.let(MoneyInput::fromCents).orEmpty())
    var invalid by rememberSaveable { mutableStateOf(false) }
    var movement by rememberSaveable { mutableStateOf(existing?.movement ?: Movement.EXPENSE) }
    var method by rememberSaveable { mutableStateOf(existing?.method ?: PaymentMethod.UNKNOWN) }
    var category by rememberSaveable { mutableStateOf(existing?.category ?: Category.HOME) }
    var bank by rememberSaveable { mutableStateOf(existing?.bank ?: lastOrigin.ifBlank { "Carteira" }) }
    var date by rememberSaveable { mutableStateOf(existing?.nextDate ?: defaultDate.toString()) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val day = if (existing != null && (monthScope != null || existing.nextDate == date)) existing.dayOfMonth else LocalDate.parse(date).dayOfMonth
    val plan = RecurringPlan(id, title, if (invalid) 0 else MoneyInput.cents(amount.text.toString()) ?: 0,
        movement, method, category, bank, date, day, existing?.active ?: true)
    val error = if (monthScope != null && YearMonth.from(LocalDate.parse(date)) != monthScope)
        "Escolha um vencimento em ${monthScope.format(commitmentMonthFormat)}. Para outro mês, volte ao Planejamento."
        else Recurrences.error(plan)
    val context = LocalContext.current
    val theme = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = {
        DialogSystemBars()
        Text(when {
            monthScope != null -> "Editar este mês · ${monthScope.format(commitmentMonthFormat)}"
            effectiveMonth != null -> "Configuração recorrente"
            else -> "Previsão mensal"
        })
    },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(when {
                monthScope != null -> "Somente ${monthScope.format(commitmentMonthFormat)} será alterado. Os outros meses mantêm seus dados."
                effectiveMonth != null -> "As previsões pendentes a partir de ${effectiveMonth.format(commitmentMonthFormat)} serão atualizadas. Meses anteriores e itens registrados ou pulados serão mantidos."
                else -> "Cada vencimento depende de confirmação. A previsão não modifica os lançamentos nem o resultado mensal."
            }, style = MaterialTheme.typography.bodySmall)
            PrivateValueGate(hidden && privateDisplayText(title, true) != title, "Mostrar descrição para edição") { OutlinedTextField(title, { title = it.take(200) }, label = { Text("Descrição da previsão") }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("recurring-title"), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)) }
            PrivateValueGate(hidden, "Mostrar valor para edição") { MoneyAmountField(amount, attempted, enabled = !busy, onInvalidInput = { invalid = it }) }
            ChoiceField("Tipo previsto", movement, listOf(Movement.EXPENSE, Movement.INCOME), { it.label }) { if (!busy) movement = it }
            ChoiceField("Meio previsto", method, PaymentMethod.entries.toList(), { it.label }, icon = R.drawable.ic_card) { if (!busy) method = it }
            ChoiceField("Categoria prevista", category, Category.entries.toList(), { it.label }) { if (!busy) category = it }
            AccountPickerField("Conta da previsão", bank, accounts, { bank = it }, onAddAccount, hidden = hidden)
            OutlinedButton(onClick = {
                val selected = LocalDate.parse(date)
                showFinanceDatePicker(context, theme, selected) { date = it.toString() }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = FinanceSpacing.touch).testTag("recurring-due-date")) {
                Text("${if (monthScope != null) "Vencimento" else "Próximo vencimento"}: ${formatDate(date)}")
            }
            if (monthScope == null) FinanceDisclosure("Sobre a repetição mensal", "recurring-repetition") {
                Text("Repetição mensal no dia $day. Meses mais curtos usam o último dia, mantendo o dia original para o próximo mês.", style = MaterialTheme.typography.bodySmall)
            }
            if (attempted && error != null) Text(error, color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { FilledTonalButton(enabled = !busy, onClick = { attempted = true; if (error == null) onSave(plan) }) {
            Text(if (monthScope != null) "Salvar este mês" else if (effectiveMonth != null) "Salvar configuração" else "Salvar previsão")
        } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } })
}

private fun formatDate(value: String) = LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

// O snapshot esperado é salvo junto ao formulário para manter a proteção de revisão após recriação.
internal val RecurringMonthStateSaver = listSaver<RecurringMonth?, Any>(
    save = { item -> if (item == null) emptyList() else item.plan.let { plan -> listOf(
        item.recurringId, item.month.toString(), item.status.name, item.recordId.orEmpty(), item.fixedExpense, item.revision,
        plan.id, plan.title, plan.amountCents, plan.movement.name, plan.method.name, plan.category.name,
        plan.bank, plan.nextDate, plan.dayOfMonth, plan.active, plan.lastRemindedDate,
    ) } },
    restore = { values -> if (values.isEmpty()) null else RecurringMonth(
        values[0] as String, YearMonth.parse(values[1] as String),
        RecurringPlan(values[6] as String, values[7] as String, values[8] as Long, Movement.valueOf(values[9] as String),
            PaymentMethod.valueOf(values[10] as String), Category.valueOf(values[11] as String), values[12] as String,
            values[13] as String, values[14] as Int, values[15] as Boolean, values[16] as String),
        RecurringMonthStatus.valueOf(values[2] as String), (values[3] as String).ifEmpty { null }, values[4] as Boolean, values[5] as Long,
    ) },
)
