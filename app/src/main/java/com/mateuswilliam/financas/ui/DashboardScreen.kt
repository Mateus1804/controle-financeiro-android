package com.mateuswilliam.financas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mateuswilliam.financas.FinanceState
import com.mateuswilliam.financas.R
import com.mateuswilliam.financas.core.Category
import com.mateuswilliam.financas.core.Ledger
import com.mateuswilliam.financas.core.ExpenseFilter
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DashboardScreen(state: FinanceState, month: YearMonth, onMonth: (YearMonth) -> Unit, onHide: () -> Unit,
                    onPending: () -> Unit, onCategory: (Category) -> Unit, onExpenseGroup: (ExpenseFilter) -> Unit,
                    notice: (@Composable () -> Unit)? = null) {
    val zone = ZoneId.systemDefault()
    val summary = androidx.compose.runtime.remember(state.records, month, zone) { Ledger.month(state.records, month, zone) }
    val hidden = state.settings.hideAmounts
    LazyColumn(Modifier.testTag("dashboard-list"), contentPadding = PaddingValues(20.dp, 20.dp, 20.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("CONTROLE FINANCEIRO", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.primary)
                    Text("Minhas finanças", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = onHide) { Text(if (hidden) "Mostrar" else "Ocultar") }
            }
        }
        if (notice != null) item { notice() }
        item { MonthPicker(month, onMonth) }
        item {
            Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Ink, contentColor = androidx.compose.ui.graphics.Color.White)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Resultado registrado no mês", style = MaterialTheme.typography.bodyMedium, color = SoftBlue)
                    Amount(summary.result, hidden, size = 32)
                    HorizontalDivider(color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.16f))
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Receitas", style = MaterialTheme.typography.bodySmall, color = SoftBlue)
                            Amount(summary.income, hidden, size = 18)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Despesas líquidas", style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color(0xFFFFBDBD))
                            Amount(summary.expenses, hidden, size = 18)
                        }
                    }
                    Text("Calculado com os lançamentos confirmados. Transferências não alteram o resultado.",
                        style = MaterialTheme.typography.bodySmall, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.72f))
                }
            }
        }
        if (state.pending.isNotEmpty()) item {
            Card(onClick = onPending, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(state.pending.size.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Column(Modifier.weight(1f)) {
                        Text("Vamos conferir?", style = MaterialTheme.typography.titleMedium)
                        Text("Notificações aguardando sua revisão", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("→", fontSize = 24.sp)
                }
            }
        }
        item { SectionTitle("Visão do período", "Receitas e despesas líquidas confirmadas") }
        item {
            if (summary.records.isEmpty()) EmptyCard("Seu controle começa aqui", "Toque em + para registrar uma movimentação. A captura de notificações pode ser ativada em Ajustes.")
            else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val maximum = maxOf(summary.income, summary.expenses, 1)
                CategoryBar("Receitas", summary.income.coerceAtLeast(0), maximum, hidden)
                CategoryBar("Despesas líquidas", summary.expenses.coerceAtLeast(0), maximum, hidden)
            }
        }
        if (summary.categories.isNotEmpty()) {
            item { SectionTitle("Para onde foi", "Despesas por categoria · toque para explorar") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    summary.categories.forEach { (category, cents) ->
                        CategoryBar(category.label, cents, summary.categories.values.maxOrNull() ?: 1, hidden) { onCategory(category) }
                    }
                }
            }
        }
        item { ExpenseChart(state.records, month, hidden, onExpenseGroup) }
        if (summary.records.isNotEmpty()) {
            item { SectionTitle("Últimos lançamentos") }
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        summary.records.take(4).forEachIndexed { index, item ->
                            RecordRow(item, hidden)
                            if (index < minOf(summary.records.size, 4) - 1) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MonthPicker(month: YearMonth, onMonth: (YearMonth) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onMonth(month.minusMonths(1)) }, modifier = Modifier.size(48.dp)) { AppIcon(R.drawable.ic_chevron_left, description = "Mês anterior") }
        Text(month.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.forLanguageTag("pt-BR"))),
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = { onMonth(month.plusMonths(1)) }, modifier = Modifier.size(48.dp)) { AppIcon(R.drawable.ic_chevron_right, description = "Próximo mês") }
    }
}
