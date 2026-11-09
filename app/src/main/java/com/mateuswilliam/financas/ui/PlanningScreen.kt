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
    onBack: () -> Unit, onBudget: (MonthlyBudget, () -> Unit) -> Unit, onDeleteBudget: (Category) -> Unit,
    commitments: CommitmentSetup?, commitmentReport: Result<CommitmentReport>?, onIncome: (MonthlyIncomePlan, () -> Unit) -> Unit,
    projection: PeriodProjection? = null, forecastsLoaded: Boolean = true,
) {
    var budgetEditor by rememberSaveable { mutableStateOf(false) }
    var budgetCategory by rememberSaveable { mutableStateOf<Category?>(null) }
    var removingBudget by rememberSaveable(month.toString()) { mutableStateOf<Category?>(null) }
    var incomeEditor by rememberSaveable(month.toString()) { mutableStateOf(false) }
    var otherExpanded by rememberSaveable(month.toString()) { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val realized = projection?.realized?.records ?: state.records.filter { it.occurredAt <= System.currentTimeMillis() }
    val usage = remember(realized, state.budgets, month, zone) { Budgets.usage(realized, state.budgets, month, zone) }
    val hidden = state.settings.hideAmounts
    LazyColumn(Modifier.testTag("planning-list"), contentPadding = PaddingValues(FinanceSpacing.page, 16.dp, FinanceSpacing.page, FinanceSpacing.listBottom), verticalArrangement = Arrangement.spacedBy(FinanceSpacing.cardGap)) {
        item {
            TextButton(onClick = onBack) { AppIcon(R.drawable.ic_chevron_left); Text("Voltar") }
            SectionTitle("Planejamento", "Renda prevista, compromissos e limites do mês")
        }
        item { MonthPicker(month, onMonth) }
        projection?.let { item { PeriodResultCard(it, hidden, forecastsLoaded) } }
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
            ChoiceField("Categoria do orçamento", category, categoriesFor(Movement.EXPENSE), { it.label }) { category = it }
            PrivateValueGate(hidden, "Mostrar valor para edição") { MoneyAmountField(amount, attempted, enabled = !busy, onInvalidInput = { invalid = it }) }
            if (attempted && error != null) Text(error, color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { FilledTonalButton(enabled = !busy, onClick = { attempted = true; if (error == null) onSave(budget) }) { Text("Salvar limite") } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } })
}

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
