package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.core.*
import com.mateuswilliam.financas.data.DraftEntity
import java.time.YearMonth
import java.time.ZoneId

@Composable
fun PendingScreen(pending: List<DraftEntity>, hidden: Boolean, onReview: (DraftEntity) -> Unit, onIgnore: (String) -> Unit, busy: Boolean) {
    LazyColumn(Modifier.testTag("pending-list"), contentPadding = PaddingValues(FinanceSpacing.page, 20.dp, FinanceSpacing.page, FinanceSpacing.listBottom)) {
        item { Box(Modifier.padding(bottom = 20.dp)) { SectionTitle("Caixa de revisão", "Sugestões locais. Nenhum lançamento é registrado sem confirmação.") } }
        if (pending.isEmpty()) item { EmptyCard("Tudo em dia", "As notificações dos aplicativos selecionados aparecem aqui. A captura opcional pode ser configurada em Ajustes.") }
        itemsIndexed(pending, key = { _, it -> it.id }) { index, draft ->
            Surface(shape = groupedShape(index, pending.size), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                        leadingContent = { IconBadge(categoryIcon(draft.category), movementColor(draft.movement)) },
                        overlineContent = { Text(privateDisplayText(draft.bank, hidden), style = MaterialTheme.typography.labelMedium) },
                        headlineContent = { Text(privateDisplayText(draft.sourceTitle.ifBlank { "Nova notificação" }, hidden)) },
                        supportingContent = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${draft.movement.label} · ${draft.method.label}", style = MaterialTheme.typography.bodySmall)
                            if (draft.amountCents != null) Amount(draft.amountCents, hidden, size = 22) else Text("Valor a preencher")
                            Text(privateDisplayText(draft.explanation, hidden), style = MaterialTheme.typography.bodySmall)
                        } })
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { onReview(draft) }, enabled = !busy, modifier = Modifier.testTag("review-${draft.id}")) { Text("Revisar") }
                        TextButton(onClick = { onIgnore(draft.id) }, enabled = !busy) { Text("Ignorar") }
                    }
                }
            }
        }
    }
}

@Composable
fun RecordsScreen(records: List<Record>, month: YearMonth, onMonth: (YearMonth) -> Unit, hidden: Boolean,
                  category: Category?, onCategory: (Category?) -> Unit, onEdit: (Record) -> Unit,
                  expenseFilter: ExpenseFilter? = null, onClearExpenseFilter: () -> Unit = {}) {
    var search by rememberSaveable { mutableStateOf("") }
    var movement by rememberSaveable { mutableStateOf<Movement?>(null) }
    LaunchedEffect(expenseFilter) { if (expenseFilter != null) movement = Movement.EXPENSE }
    val zone = ZoneId.systemDefault()
    val period = remember(records, month, zone) { Ledger.month(records, month, zone).records }
    val filtered = remember(period, category, movement, search, expenseFilter) { period.filter {
        (expenseFilter == null || expenseFilter.matches(it)) &&
            (category == null || it.category == category) && (movement == null || it.movement == movement) &&
            (search.isBlank() || "${it.description} ${it.bank} ${it.destination}".contains(search.trim(), ignoreCase = true))
    } }
    LazyColumn(Modifier.testTag("records-list"), contentPadding = PaddingValues(FinanceSpacing.page, 20.dp, FinanceSpacing.page, FinanceSpacing.listBottom)) {
        item { Box(Modifier.padding(bottom = 16.dp)) { SectionTitle("Extrato", "Lançamentos confirmados neste aparelho") } }
        item { Box(Modifier.padding(bottom = 16.dp)) { MonthPicker(month, onMonth) } }
        if (expenseFilter != null) item {
            InputChip(selected = true, onClick = onClearExpenseFilter, label = { Text("Despesas · ${expenseFilter.label} · Limpar filtro") })
        }
        item { OutlinedTextField(search, { search = it.take(100) }, label = { Text("Buscar descrição ou banco") }, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), leadingIcon = { AppIcon(com.mateuswilliam.financas.R.drawable.ic_search) }, singleLine = true) }
        item {
            Row(Modifier.padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { ChoiceField("Tipo", movement, listOf(null) + Movement.entries.filter { it != Movement.UNKNOWN }, { it?.label ?: "Todos" }) { onClearExpenseFilter(); movement = it } }
                Box(Modifier.weight(1f)) { ChoiceField("Categoria", category, listOf(null) + Category.entries, { it?.label ?: "Todas" }) { onClearExpenseFilter(); onCategory(it) } }
            }
        }
        item { Text("${filtered.size} lançamento(s)", Modifier.padding(bottom = 12.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (filtered.isEmpty()) item { EmptyCard("Nenhum lançamento neste filtro", "Mude o período ou os filtros. Os lançamentos só entram aqui após a confirmação.") }
        itemsIndexed(filtered, key = { _, it -> it.id }) { index, record ->
            Surface(shape = groupedShape(index, filtered.size), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                RecordRow(record, hidden) { onEdit(record) }
            }
        }
    }
}
