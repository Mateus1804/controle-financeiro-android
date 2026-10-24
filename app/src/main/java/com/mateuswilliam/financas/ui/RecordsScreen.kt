package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    LazyColumn(contentPadding = PaddingValues(20.dp, 20.dp, 20.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { SectionTitle("Caixa de revisão", "Confira, edite e decida o que entra no seu controle.") }
        if (pending.isEmpty()) item { EmptyCard("Tudo em dia", "Novas notificações dos aplicativos selecionados aparecerão aqui. Ative a captura em Ajustes ou registre um lançamento pelo botão +.") }
        items(pending, key = { it.id }) { draft ->
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(draft.bank, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    Text(draft.sourceTitle.ifBlank { "Nova notificação" }, style = MaterialTheme.typography.titleMedium)
                    if (draft.amountCents != null) Amount(draft.amountCents, hidden, size = 24)
                    else Text("Valor a preencher", style = MaterialTheme.typography.titleMedium)
                    Text("${draft.movement.label} · ${draft.method.label} · ${draft.category.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(draft.explanation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { onReview(draft) }, enabled = !busy, modifier = Modifier.testTag("review-${draft.id}")) { Text("Revisar") }
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
    LazyColumn(contentPadding = PaddingValues(20.dp, 20.dp, 20.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SectionTitle("Meu extrato", "Histórico dos lançamentos registrados neste aparelho") }
        item { MonthPicker(month, onMonth) }
        if (expenseFilter != null) item {
            InputChip(selected = true, onClick = onClearExpenseFilter, label = { Text("Despesas · ${expenseFilter.label} · Limpar filtro") })
        }
        item { OutlinedTextField(search, { search = it.take(100) }, label = { Text("Buscar descrição ou banco") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { ChoiceField("Tipo", movement, listOf(null) + Movement.entries.filter { it != Movement.UNKNOWN }, { it?.label ?: "Todos" }) { onClearExpenseFilter(); movement = it } }
                Box(Modifier.weight(1f)) { ChoiceField("Categoria", category, listOf(null) + Category.entries, { it?.label ?: "Todas" }) { onClearExpenseFilter(); onCategory(it) } }
            }
        }
        item { Text("${filtered.size} lançamento(s)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (filtered.isEmpty()) item { EmptyCard("Nenhum lançamento neste filtro", "Mude o período ou os filtros. Os lançamentos só entram aqui após a confirmação.") }
        items(filtered, key = { it.id }) { record ->
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Box(Modifier.padding(14.dp)) { RecordRow(record, hidden) { onEdit(record) } }
            }
        }
    }
}
