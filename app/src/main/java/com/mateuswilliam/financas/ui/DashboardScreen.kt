package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.FinanceState
import com.mateuswilliam.financas.R
import com.mateuswilliam.financas.core.*
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun DashboardScreen(state: FinanceState, month: YearMonth, onMonth: (YearMonth) -> Unit, onHide: () -> Unit,
                    onPending: () -> Unit, onExpenseGroup: (ExpenseFilter) -> Unit,
                    notice: (@Composable () -> Unit)? = null, onPlanning: () -> Unit = {},
                    onNew: (Movement) -> Unit = {}, movements: List<MonthlyMovement>, actions: MovementActions, busy: Boolean) {
    var status by rememberSaveable { mutableStateOf("Todas") }
    val visible = movements.filter { status == "Todas" || if (status == "Realizadas") it.realized else it.status == "Prevista" }
    val zone = ZoneId.systemDefault()
    val summary = remember(state.records, month, zone) { Ledger.month(state.records, month, zone) }
    val hidden = state.settings.hideAmounts
    LazyColumn(Modifier.testTag("dashboard-list"), contentPadding = PaddingValues(FinanceSpacing.page, 16.dp, FinanceSpacing.page, FinanceSpacing.listBottom), verticalArrangement = Arrangement.spacedBy(FinanceSpacing.cardGap)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("CONTROLE FINANCEIRO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Visão geral", style = MaterialTheme.typography.headlineMedium)
                }
                TextButton(onClick = onHide, contentPadding = PaddingValues(8.dp)) {
                    AppIcon(selectedIcon(if (hidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility, hidden))
                    Spacer(Modifier.width(6.dp))
                    Text(if (hidden) "Mostrar" else "Ocultar")
                }
            }
        }
        item { MonthPicker(month, onMonth) }
        item {
            Surface(Modifier.testTag("period-summary"), shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                Column(Modifier.fillMaxWidth().padding(FinanceSpacing.card), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppIcon(R.drawable.ic_wallet)
                        Text("Resultado do período", style = MaterialTheme.typography.titleSmall)
                    }
                    Amount(summary.result, hidden, Modifier.fillMaxWidth(), size = 32)
                    val largeText = LocalDensity.current.fontScale > 1.3f
                    if (largeText) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SummaryValue("Receitas", summary.income, hidden, R.drawable.ic_income)
                        SummaryValue("Despesas líquidas", summary.expenses, hidden, R.drawable.ic_expense)
                    } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f)) { SummaryValue("Receitas", summary.income, hidden, R.drawable.ic_income) }
                        Box(Modifier.weight(1f)) { SummaryValue("Despesas líquidas", summary.expenses, hidden, R.drawable.ic_expense) }
                    }
                    FinanceDisclosure("Sobre o resultado", "period-summary-details") {
                        Text("Somente lançamentos confirmados. Não representa o saldo bancário.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { QuickActions(onNew) }
        if (notice != null) item { notice() }
        item {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Column {
                    if (state.pending.isNotEmpty()) ListItem(
                        modifier = Modifier.fillMaxWidth().clickableAction(onPending),
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                        leadingContent = { IconBadge(R.drawable.ic_pending) },
                        headlineContent = { Text("Revisões pendentes") },
                        supportingContent = { Text("${state.pending.size} sugestão(ões) para conferir") },
                        trailingContent = { AppIcon(R.drawable.ic_chevron_right) })
                    ListItem(modifier = Modifier.fillMaxWidth().testTag("open-planning").clickableAction(onPlanning),
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                        leadingContent = { IconBadge(R.drawable.ic_calendar) },
                        headlineContent = { Text("Planejamento e análises", style = MaterialTheme.typography.bodyMedium) },
                        supportingContent = { Text("Projeções, renda e orçamentos", style = MaterialTheme.typography.bodySmall) },
                        trailingContent = { AppIcon(R.drawable.ic_chevron_right) })
                }
            }
        }
        item {
            SectionTitle("Movimentações do mês", "Receitas, despesas, recorrências e parcelas")
            ChoiceField("Situação", status, listOf("Todas", "Previstas", "Realizadas"), { it }) { status = it }
        }
        monthlyMovementItems(visible, hidden, busy, actions)
        item {
            FinanceDisclosure("Detalhes do período", "period-breakdown") {
                val maximum = maxOf(summary.income, summary.expenses, 1)
                CategoryBar("Receitas", summary.income.coerceAtLeast(0), maximum, hidden)
                CategoryBar("Despesas líquidas", summary.expenses.coerceAtLeast(0), maximum, hidden)
            }
        }
        item { ExpenseChart(state.records, month, hidden, onExpenseGroup) }
    }
}

@Composable
private fun SummaryValue(label: String, cents: Long, hidden: Boolean, icon: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(icon, Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
        Amount(cents, hidden, size = 18)
    }
}

@Composable
private fun QuickActions(onNew: (Movement) -> Unit) {
    val movements = listOf(Movement.INCOME to "Receita", Movement.EXPENSE to "Despesa", Movement.TRANSFER to "Transferência")
    val largeText = LocalDensity.current.fontScale > 1.3f
    @Composable fun Action(movement: Movement, label: String, modifier: Modifier) {
        FilledTonalButton(onClick = { onNew(movement) }, modifier = modifier.heightIn(min = 56.dp)
            .testTag("quick-${movement.name.lowercase(Locale.ROOT)}"), shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                contentColor = MaterialTheme.colorScheme.onSurface), contentPadding = PaddingValues(8.dp)) {
            if (largeText) {
                AppIcon(movementIcon(movement)); Spacer(Modifier.width(12.dp)); Text(label)
            } else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AppIcon(movementIcon(movement)); Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    if (largeText) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        movements.forEach { (movement, label) -> Action(movement, label, Modifier.fillMaxWidth()) }
    } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        movements.forEach { (movement, label) -> Action(movement, label, Modifier.weight(1f)) }
    }
}

@Composable
fun MonthPicker(month: YearMonth, onMonth: (YearMonth) -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonth(month.minusMonths(1)) }, modifier = Modifier.size(48.dp)) { AppIcon(R.drawable.ic_chevron_left, description = "Mês anterior") }
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("pt-BR"))), Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(onClick = { onMonth(month.plusMonths(1)) }, modifier = Modifier.size(48.dp)) { AppIcon(R.drawable.ic_chevron_right, description = "Próximo mês") }
        }
    }
}

private fun Modifier.clickableAction(onClick: () -> Unit): Modifier = this.then(Modifier.clickable(onClick = onClick))
