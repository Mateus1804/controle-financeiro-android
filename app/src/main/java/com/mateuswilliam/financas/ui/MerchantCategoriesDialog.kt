package com.mateuswilliam.financas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mateuswilliam.financas.core.*
import com.mateuswilliam.financas.data.MerchantCategoryStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MerchantCategoriesDialog(store: MerchantCategoryStore, hidden: Boolean, onDismiss: () -> Unit) {
    var entries by remember { mutableStateOf(store.entries()) }
    var editingKey by rememberSaveable { mutableStateOf<String?>(null) }
    var clearing by rememberSaveable { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    fun refresh(message: String) { entries = store.entries(); feedback = message }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        DialogSystemBars()
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 600.dp).navigationBarsPadding(),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Categorias lembradas", style = MaterialTheme.typography.headlineSmall)
                Text("Estas escolhas só sugerem a categoria de próximas compras. Corrigir ou esquecer uma lembrança não altera seu histórico.")
            }
            feedback?.let { message -> item { Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) } }
            if (entries.isEmpty()) item { Text("Nenhuma categoria lembrada.") }
            items(entries, key = { it.key }) { entry ->
                ListItem(headlineContent = { Text(privateDisplayText(entry.key, hidden)) },
                    supportingContent = { Text(entry.category.label) },
                    trailingContent = { ContextualActions(privateDisplayText(entry.key, hidden), "merchant-actions-${entry.key}", true,
                        listOf(FinanceAction("Corrigir categoria", { editingKey = entry.key }),
                            FinanceAction("Esquecer categoria", { store.forget(entry.key); refresh("Categoria esquecida.") }))) })
            }
            if (entries.isNotEmpty()) item { TextButton({ clearing = true }, Modifier.heightIn(min = 48.dp)) { Text("Apagar todas as lembranças") } }
            item { TextButton(onDismiss, Modifier.heightIn(min = 48.dp)) { Text("Fechar") } }
        }
    }
    entries.firstOrNull { it.key == editingKey }?.let { entry -> key(entry.key) {
        var category by rememberSaveable { mutableStateOf(entry.category) }
        AlertDialog(onDismissRequest = { editingKey = null }, title = { DialogSystemBars(); Text("Corrigir categoria lembrada") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(privateDisplayText(entry.key, hidden))
                ChoiceField("Categoria lembrada", category, categoriesFor(Movement.EXPENSE), { it.label }) { category = it }
                Text("Vale para próximas sugestões. Os lançamentos existentes serão preservados.")
            } },
            confirmButton = { TextButton({ store.remember(entry.key, category); editingKey = null; refresh("Categoria atualizada para próximas sugestões.") },
                Modifier.testTag("save-merchant-category")) { Text("Salvar") } },
            dismissButton = { TextButton({ editingKey = null }) { Text("Cancelar") } })
    } }
    if (clearing) AlertDialog(onDismissRequest = { clearing = false }, title = { DialogSystemBars(); Text("Apagar todas as lembranças?") },
        text = { Text("Somente as categorias lembradas serão apagadas. Seus lançamentos continuam iguais.") },
        confirmButton = { TextButton({ store.clear(); clearing = false; refresh("Lembranças apagadas.") }) { Text("Apagar lembranças") } },
        dismissButton = { TextButton({ clearing = false }) { Text("Cancelar") } })
}
